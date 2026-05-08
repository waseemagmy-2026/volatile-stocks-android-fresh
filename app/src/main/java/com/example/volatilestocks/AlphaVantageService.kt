package com.example.volatilestocks

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.DecimalFormat
import java.util.Locale

class AlphaVantageService(private val apiKey: String) {

    fun fetchScannerResults(
        minPrice: Double,
        maxPrice: Double,
        minVolume: Long,
        minChange: Double,
        maxChange: Double,
        sortBy: String
    ): List<StockSignal> {
        val json = getJson("https://www.alphavantage.co/query?function=TOP_GAINERS_LOSERS&apikey=$apiKey")

        if (json.has("Note")) throw Exception("מגבלת בקשות ל-API. נסה שוב בעוד דקה.")
        if (json.has("Information")) throw Exception(json.optString("Information", "אין מידע זמין כרגע"))
        if (json.has("Error Message")) throw Exception(json.optString("Error Message", "שגיאת API"))
        if (!json.has("top_gainers")) throw Exception("לא התקבלו נתוני top gainers.")

        val results = mutableListOf<StockSignal>()
        val gainers = json.getJSONArray("top_gainers")
        val blockedSuffixes = listOf("W", "WS", "WT", "R", "U")

        for (i in 0 until gainers.length()) {
            val item = gainers.getJSONObject(i)
            val symbol = item.optString("ticker", "N/A")
            if (blockedSuffixes.any { symbol.endsWith(it) }) continue

            val price = parseDoubleSafe(item.optString("price", "0"))
            val volume = parseLongSafe(item.optString("volume", "0"))
            val changePercent = parseDoubleSafe(item.optString("change_percentage", "0"))

            if (price in minPrice..maxPrice && volume >= minVolume && changePercent in minChange..maxChange) {
                val score = calculateScore(price, volume, changePercent)
                results.add(
                    StockSignal(
                        symbol = symbol,
                        price = price,
                        changePercent = changePercent,
                        volume = volume,
                        score = score,
                        summary = buildSummary(price, volume, changePercent, score)
                    )
                )
            }
        }

        return sortResults(results, sortBy).take(15)
    }

    fun fetchDetail(symbol: String): StockDetailData {
        val intradayJson = getJson(
            "https://www.alphavantage.co/query?function=TIME_SERIES_INTRADAY&symbol=$symbol&interval=5min&outputsize=compact&apikey=$apiKey"
        )
        val metaKey = "Time Series (5min)"
        if (!intradayJson.has(metaKey)) {
            if (intradayJson.has("Note")) throw Exception("מגבלת בקשות ל-API בפירוט מניה.")
            throw Exception("לא התקבלו נתוני גרף למניה $symbol")
        }

        val series = intradayJson.getJSONObject(metaKey)
        val keys = series.keys().asSequence().toList().sorted()
        val recentKeys = keys.takeLast(20)
        val points = recentKeys.map { key ->
            val item = series.getJSONObject(key)
            IntradayPoint(
                timestampLabel = key.takeLast(5),
                close = parseDoubleSafe(item.optString("4. close", "0")),
                high = parseDoubleSafe(item.optString("2. high", "0")),
                low = parseDoubleSafe(item.optString("3. low", "0")),
                volume = parseLongSafe(item.optString("5. volume", "0"))
            )
        }
        val lastPrice = points.lastOrNull()?.close ?: 0.0
        val dayHigh = points.maxOfOrNull { it.high } ?: 0.0
        val rsi = fetchRsi(symbol)

        return StockDetailData(
            symbol = symbol,
            lastPrice = lastPrice,
            dayHigh = dayHigh,
            rsi = rsi,
            points = points
        )
    }

    private fun fetchRsi(symbol: String): Double? {
        val json = getJson(
            "https://www.alphavantage.co/query?function=RSI&symbol=$symbol&interval=5min&time_period=14&series_type=close&apikey=$apiKey"
        )
        val key = "Technical Analysis: RSI"
        if (!json.has(key)) return null
        val rsiSeries = json.getJSONObject(key)
        val latestKey = rsiSeries.keys().asSequence().toList().sorted().lastOrNull() ?: return null
        return parseDoubleSafe(rsiSeries.getJSONObject(latestKey).optString("RSI", "0"))
    }

    private fun getJson(urlString: String): JSONObject {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        val response = connection.inputStream.bufferedReader().use { it.readText() }
        return JSONObject(response)
    }

    private fun parseDoubleSafe(value: String): Double {
        val cleaned = value.replace("%", "").replace(",", "").trim()
        return cleaned.toDoubleOrNull() ?: 0.0
    }

    private fun parseLongSafe(value: String): Long {
        val cleaned = value.replace(",", "").trim()
        return cleaned.toLongOrNull() ?: 0L
    }

    private fun sortResults(results: List<StockSignal>, selectedSort: String): List<StockSignal> {
        return when (selectedSort) {
            "אחוז עלייה" -> results.sortedByDescending { it.changePercent }
            "מחזור" -> results.sortedByDescending { it.volume }
            "מחיר נמוך לגבוה" -> results.sortedBy { it.price }
            "מחיר גבוה לנמוך" -> results.sortedByDescending { it.price }
            else -> results.sortedByDescending { it.score }
        }
    }

    private fun calculateScore(price: Double, volume: Long, changePercent: Double): Int {
        var score = 0
        score += when {
            price in 1.0..10.0 -> 30
            price in 10.0..25.0 -> 22
            price in 25.0..50.0 -> 12
            else -> 4
        }
        score += when {
            volume >= 10_000_000 -> 30
            volume >= 3_000_000 -> 22
            volume >= 1_000_000 -> 14
            volume >= 100_000 -> 8
            else -> 0
        }
        score += when {
            changePercent >= 20 -> 25
            changePercent >= 10 -> 18
            changePercent >= 5 -> 12
            changePercent >= 3 -> 8
            else -> 0
        }
        if (price < 1) score -= 8
        if (changePercent > 60) score -= 6
        return score.coerceIn(0, 100)
    }

    private fun buildSummary(price: Double, volume: Long, changePercent: Double, score: Int): String {
        val priceComment = when {
            price < 5 -> "מחיר נמוך עם פוטנציאל תנודתי גבוה"
            price < 20 -> "טווח מחיר נוח למסחר תנודתי"
            else -> "מחיר גבוה יחסית אך עדיין בתחום"
        }
        val volumeComment = when {
            volume > 10_000_000 -> "מחזור חזק מאוד"
            volume > 3_000_000 -> "מחזור טוב"
            volume > 1_000_000 -> "מחזור בינוני"
            else -> "מחזור סביר"
        }
        val momentumComment = when {
            changePercent > 20 -> "מומנטום חזק מאוד"
            changePercent > 10 -> "מומנטום חיובי ברור"
            else -> "עלייה מתונה יחסית"
        }
        val qualityComment = when {
            score >= 85 -> "איכות סריקה גבוהה"
            score >= 70 -> "מניה מעניינת לבדיקה"
            else -> "מניה גבולית יחסית"
        }
        return "$priceComment, $volumeComment, $momentumComment, $qualityComment."
    }
}
