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
        for (series in listOf("sv", "me", "swsh")) {
            val s = get("$b/en/series/$series") ?: continue
            val sets = JSONObject(s).optJSONArray("sets") ?: JSONArray()
            for (i in 0 until sets.length()) {
                val id = sets.getJSONObject(i).getString("id")
                val name = sets.getJSONObject(i).optString("name")
                val body = get("$b/en/cards?set.id=eq:$id") ?: "null"
                val arr = runCatching { JSONArray(body) }.getOrNull()
                val keys = arr?.optJSONObject(0)?.keys()?.asSequence()?.toList()
                println("PROBE set $id | $name | cards=${arr?.length()} keys=$keys")
            }
        }
        println("PROBE setdetail " + get("$b/en/sets/sv08.5")?.take(600))
        println("PROBE filter rarity " + get("$b/en/cards?set.id=eq:sv08.5&rarity=eq:Special%20illustration%20rare")?.take(400))
        println("PROBE rarities " + get("$b/en/rarities"))
        println("PROBE de rarities " + get("$b/de/rarities"))
        val r = get("$b/en/rarities/Special%20illustration%20rare")
        println("PROBE rarity list len=${r?.length} " + r?.take(300))
        println("PROBE gql " + run {
            val c = URL("$b/graphql").openConnection() as HttpURLConnection
            c.requestMethod = "POST"; c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.write("""{"query":"{ cards(filters: {set: \"sv08.5\"}, pagination: {count: 5}) { id rarity } }"}""".toByteArray())
            runCatching { c.inputStream.bufferedReader().readText() }.getOrElse { "ERR ${c.responseCode} " + c.errorStream?.bufferedReader()?.readText() }
        })
        for (id in listOf("sv08.5-161", "sv01-245", "swsh7-215", "me01-180")) {
            val en = get("$b/en/cards/$id")?.let { runCatching { JSONObject(it).optString("rarity") }.getOrNull() }
            val de = get("$b/de/cards/$id")?.let { runCatching { JSONObject(it).optString("rarity") }.getOrNull() }
            println("PROBE card $id en=$en de=$de")
        }
    }
}
