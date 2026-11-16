package com.zeroxion.cardscanner.data

import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.Reader
import java.net.HttpURLConnection
import java.net.URL
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Der öffentliche Preisführer von Cardmarket: eine Datei mit den Tagespreisen aller Pokémon-Produkte,
 * einmal täglich erneuert. TCGdex bezieht seine Preise aus derselben Datei; direkt gelesen sind sie
 * aktueller, und eine ganze Sammlung braucht einen Download statt einer Abfrage pro Karte.
 *
 * Die Datei kennt nur Cardmarket-Produktnummern, keine Kartennamen oder -nummern. Sie taugt deshalb
 * nur für Karten, deren Produktnummer schon bekannt ist (sie kommt beim Scannen aus TCGdex).
 *
 * Alle Funktionen blockieren und gehören auf Dispatchers.IO.
 */
object CardmarketPriceGuide {
    private const val SOURCE = "https://downloads.s3.cardmarket.com/productCatalog/priceGuide/price_guide_6.json"

    /** So lange gilt eine geladene Datei als aktuell; Cardmarket erneuert sie einmal am Tag. */
    private const val FRESH_MILLIS = 6 * 60 * 60 * 1000L

    /** Preise eines Produkts, getrennt nach Normal und Holo/Reverse. */
    data class Entry(val normal: Prices?, val holo: Prices?) {
        val hasAny: Boolean get() = normal != null || holo != null
    }

    /**
     * @param updated Stand der Preise als ISO-Zeitpunkt ("2026-11-16T01:48:57Z"), wie bei TCGdex
     * @param entries Preise je Cardmarket-Produktnummer – nur für die angefragten Produkte
     */
    data class Guide(val updated: String?, val entries: Map<Long, Entry>)

    private fun file(dir: File) = File(dir, "price_guide_6.json")
    private fun etagFile(dir: File) = File(dir, "price_guide_6.etag")

    /** Ob eine heute schon geladene Datei bereitliegt (dann kostet das Lesen kein Datenvolumen). */
    fun isFresh(dir: File, now: Long = System.currentTimeMillis()): Boolean {
        val file = file(dir)
        return file.length() > 0 && now - file.lastModified() in 0 until FRESH_MILLIS
    }

    /**
     * Preise der Produkte [wanted]. Lädt die Datei (rund 16 MB) nach [dir], wenn dort keine aktuelle
     * liegt; hat sich bei Cardmarket nichts geändert, bleibt es bei einer kurzen Nachfrage.
     */
    fun load(dir: File, wanted: Set<Long>, now: Long = System.currentTimeMillis()): Guide {
        if (wanted.isEmpty()) return Guide(null, emptyMap())
        val file = file(dir)
        if (!isFresh(dir, now)) {
            try {
                download(dir)
                file.setLastModified(now)
            } catch (e: Exception) {
                // Ohne Verbindung lieber die Preise von gestern als gar keine.
                if (file.length() <= 0) throw IOException("Preisliste von Cardmarket nicht erreichbar", e)
            }
        }
        return try {
            file.bufferedReader().use { parse(it, wanted) }
        } catch (e: Exception) {
            // Abgebrochener Download oder unerwartetes Format: beim nächsten Mal frisch laden.
            file.delete()
            etagFile(dir).delete()
            throw IOException("Preisliste von Cardmarket nicht lesbar", e)
        }
    }

    private fun download(dir: File) {
        if (!dir.exists() && !dir.mkdirs()) throw IOException("Speicherordner fehlt")
        val file = file(dir)
        val etagFile = etagFile(dir)
        val conn = URL(SOURCE).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", "Cardscanner-Android")
            val known = if (file.length() > 0 && etagFile.exists()) etagFile.readText().trim() else ""
            if (known.isNotEmpty()) conn.setRequestProperty("If-None-Match", known)
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_NOT_MODIFIED) return
            if (code !in 200..299) throw IOException("Cardmarket antwortet mit $code")

            // Erst vollständig in eine Nebendatei, damit ein Abbruch die alte Datei nicht zerstört.
            val partial = File(dir, "price_guide_6.part")
            conn.inputStream.use { input -> partial.outputStream().use { input.copyTo(it) } }
            val expected = conn.contentLengthLong
            if (expected > 0 && partial.length() != expected) {
                partial.delete()
                throw IOException("Download unvollständig")
            }
            if (!partial.renameTo(file)) {
                partial.copyTo(file, overwrite = true)
                partial.delete()
            }
            val etag = conn.getHeaderField("ETag")
            if (etag.isNullOrBlank()) etagFile.delete() else etagFile.writeText(etag)
        } finally {
            conn.disconnect()
        }
    }

    private val createdAt = Regex(""""createdAt"\s*:\s*"([^"]+)"""")
    private val cardmarketTime = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ")

    /**
     * Liest die Preisliste Eintrag für Eintrag, ohne die ganze Datei im Speicher zu halten,
     * und behält nur die Produkte aus [wanted]. Die Einträge sind flache Objekte ohne Verschachtelung.
     */
    fun parse(reader: Reader, wanted: Set<Long>): Guide {
        val entries = HashMap<Long, Entry>()
        val head = StringBuilder()
        val item = StringBuilder()
        var inList = false
        var inItem = false
        var sawList = false
        val buffer = CharArray(64 * 1024)
        while (true) {
            val read = reader.read(buffer)
            if (read < 0) break
            for (i in 0 until read) {
                val c = buffer[i]
                when {
                    !inList -> if (c == '[') {
                        inList = true
                        sawList = true
                    } else if (head.length < 4096) head.append(c)
                    inItem -> {
                        item.append(c)
                        if (c == '}') {
                            inItem = false
                            val id = productId(item)
                            if (id != null && id in wanted) entry(item.toString())?.let { entries[id] = it }
                        }
                    }
                    c == '{' -> {
                        inItem = true
                        item.setLength(0)
                        item.append(c)
                    }
                    c == ']' -> inList = false
                }
            }
        }
        if (!sawList) throw IOException("Preisliste ohne Einträge")
        val updated = createdAt.find(head)?.groupValues?.get(1)?.let { stamp ->
            runCatching { OffsetDateTime.parse(stamp, cardmarketTime).toInstant().toString() }.getOrNull()
        }
        return Guide(updated, entries)
    }

    /** Produktnummer eines Eintrags, ohne ihn ganz zu zerlegen – die meisten werden nicht gebraucht. */
    private fun productId(item: CharSequence): Long? {
        val key = "\"idProduct\""
        var i = item.indexOf(key)
        if (i < 0) return null
        i += key.length
        while (i < item.length && (item[i] == ':' || item[i].isWhitespace())) i++
        var value = 0L
        var digits = 0
        while (i < item.length && item[i].isDigit()) {
            value = value * 10 + (item[i] - '0')
            digits++
            i++
        }
        return if (digits in 1..15) value else null
    }

    private fun entry(json: String): Entry? {
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return null
        return Entry(TcgDexApi.parsePrices(o, ""), TcgDexApi.parsePrices(o, "-holo")).takeIf { it.hasAny }
    }
}
