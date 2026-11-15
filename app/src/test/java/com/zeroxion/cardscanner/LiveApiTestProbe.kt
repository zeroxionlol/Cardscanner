package com.zeroxion.cardscanner

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

class LiveApiTestProbe {
    private fun get(url: String): String? {
        val c = URL(url).openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", "probe")
        return try {
            val code = c.responseCode
            if (code != 200) "HTTP $code" else c.inputStream.bufferedReader().readText()
        } catch (e: Exception) { "ERR $e" } finally { c.disconnect() }
    }

    @Test
    fun probe() {
        assumeTrue(System.getenv("LIVE_API") == "1")
        val b = "https://api.tcgdex.net/v2"
        println("PROBE setdetail " + get("$b/en/sets/sv08.5")?.take(900)); Thread.sleep(1500)
        for (q in listOf("set.id=eq:sv08.5&rarity=eq:Special%20illustration%20rare", "set.id=eq:sv08.5&rarity=Special%20illustration%20rare", "set.id=eq:sv08.5&rarity=Hyper%20rare", "set.id=eq:sv07&rarity=eq:Illustration%20rare", "set.id=eq:me01&rarity=eq:Special%20illustration%20rare")) {
            val body = get("$b/en/cards?$q")
            val n = runCatching { JSONArray(body).length() }.getOrNull()
            println("PROBE filter $q -> $n ${body?.take(150)}"); Thread.sleep(1500)
        }
        println("PROBE rarities " + get("$b/en/rarities")); Thread.sleep(1500)
        println("PROBE de rarities " + get("$b/de/rarities")); Thread.sleep(1500)
        println("PROBE gql " + run {
            val c = URL("$b/graphql").openConnection() as HttpURLConnection
            c.requestMethod = "POST"; c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.write("""{"query":"{ cards(filters: {set: \"sv08.5\"}, pagination: {count: 5}) { id rarity } }"}""".toByteArray())
            runCatching { c.inputStream.bufferedReader().readText() }.getOrElse { "ERR ${c.responseCode} " + c.errorStream?.bufferedReader()?.readText() }
        })
        for (id in listOf("sv08.5-161", "sv01-245", "swsh7-215", "me01-180", "sv03.5-199", "sv04.5-232", "swsh12.5-GG70", "sv05-190")) {
            val en = get("$b/en/cards/$id")?.let { runCatching { JSONObject(it).optString("rarity") }.getOrNull() }
            val de = get("$b/de/cards/$id")?.let { runCatching { JSONObject(it).optString("rarity") }.getOrNull() }
            println("PROBE card $id en=$en de=$de"); Thread.sleep(1500)
        }
    }
}
