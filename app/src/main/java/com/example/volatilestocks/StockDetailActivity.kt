package com.example.volatilestocks

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

class StockDetailActivity : AppCompatActivity() {

    private lateinit var symbolText: TextView
    private lateinit var chartStatusText: TextView
    private lateinit var rsiText: TextView
    private lateinit var highText: TextView
    private lateinit var lastPriceText: TextView
    private lateinit var dropThresholdInput: EditText
    private lateinit var highThresholdInput: EditText
    private lateinit var saveAlertsButton: Button
    private lateinit var refreshButton: Button
    private lateinit var chart: LineChart

    private var symbol: String = ""
    private var apiKey: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stock_detail)

        bindViews()

        symbol = normalizeSymbol(intent.getStringExtra("symbol") ?: "")
        apiKey = getSharedPreferences("scanner_prefs", Context.MODE_PRIVATE)
            .getString("api_key", "") ?: ""

        symbolText.text = symbol

        val prefs = getSharedPreferences("alerts_$symbol", Context.MODE_PRIVATE)
        dropThresholdInput.setText(prefs.getFloat("dropThreshold", 3f).toString())
        highThresholdInput.setText(prefs.getFloat("highThreshold", 0.5f).toString())

        saveAlertsButton.setOnClickListener {
            val drop = dropThresholdInput.text.toString().toFloatOrNull() ?: 3f
            val high = highThresholdInput.text.toString().toFloatOrNull() ?: 0.5f
            prefs.edit()
                .putFloat("dropThreshold", drop)
                .putFloat("highThreshold", high)
                .apply()
            chartStatusText.text = "ההתראות נשמרו"
        }

        refreshButton.setOnClickListener {
            loadAllData()
        }

        setupChart()
        loadAllData()
    }

    private fun bindViews() {
        symbolText = findViewById(R.id.symbolText)
        chartStatusText = findViewById(R.id.chartStatusText)
        rsiText = findViewById(R.id.rsiText)
        highText = findViewById(R.id.highText)
        lastPriceText = findViewById(R.id.lastPriceText)
        dropThresholdInput = findViewById(R.id.dropThresholdInput)
        highThresholdInput = findViewById(R.id.highThresholdInput)
        saveAlertsButton = findViewById(R.id.saveAlertsButton)
        refreshButton = findViewById(R.id.refreshButton)
        chart = findViewById(R.id.lineChart)
    }

    private fun setupChart() {
        chart.setNoDataText("No chart data")
        chart.setTouchEnabled(true)
        chart.setPinchZoom(true)
        chart.axisRight.isEnabled = false
        chart.legend.isEnabled = false
        chart.description = Description().apply { text = "" }
    }

    private fun loadAllData() {
        if (symbol.isBlank()) {
            chartStatusText.text = "סימבול מניה לא תקין"
            return
        }

        if (apiKey.isBlank()) {
            chartStatusText.text = "אין API KEY"
            return
        }

        chartStatusText.text = "טוען נתוני מניה..."
        rsiText.text = "RSI: --"

        loadQuote()

        // טעינה מדורגת כדי לא ליפול על מגבלת 1 בקשה לשנייה
        chart.postDelayed({
            loadChartWithFallback()
        }, 1300)

        chart.postDelayed({
            loadRsi()
        }, 2600)
    }

    private fun loadQuote() {
        thread {
            try {
                val url = buildUrl(
                    function = "GLOBAL_QUOTE",
                    extra = mapOf("symbol" to symbol)
                )

                val response = URL(url).readText()
                val json = JSONObject(response)

                val apiMessage = extractApiMessage(json)
                if (apiMessage != null) {
                    runOnUiThread {
                        chartStatusText.text = apiMessage
                        lastPriceText.text = "מחיר אחרון: --"
                        highText.text = "שיא יומי: --"
                    }
                    return@thread
                }

                val quote = json.optJSONObject("Global Quote")
                if (quote == null || quote.length() == 0) {
                    runOnUiThread {
                        lastPriceText.text = "מחיר אחרון: --"
                        highText.text = "שיא יומי: --"
                    }
                    return@thread
                }

                val price = quote.optString("05. price", "--")
                val high = quote.optString("03. high", "--")

                runOnUiThread {
                    lastPriceText.text = "מחיר אחרון: $price"
                    highText.text = "שיא יומי: $high"
                }
            } catch (_: Exception) {
                runOnUiThread {
                    lastPriceText.text = "מחיר אחרון: --"
                    highText.text = "שיא יומי: --"
                }
            }
        }
    }

    private fun loadChartWithFallback() {
        loadIntradayChart { result ->
            when (result) {
                is ChartLoadResult.Success -> {
                    runOnUiThread {
                        renderChart(result.entries, symbol)
                        chartStatusText.text = "גרף תוך-יומי נטען"
                    }
                }
                is ChartLoadResult.RateLimited -> {
                    runOnUiThread {
                        chart.clear()
                        chartStatusText.text = result.message
                    }
                }
                is ChartLoadResult.NoData -> {
                    chart.postDelayed({
                        loadDailyChart(result.message)
                    }, 1300)
                }
                is ChartLoadResult.Error -> {
                    chart.postDelayed({
                        loadDailyChart(result.message)
                    }, 1300)
                }
            }
        }
    }

    private fun loadIntradayChart(callback: (ChartLoadResult) -> Unit) {
        thread {
            try {
                val url = buildUrl(
                    function = "TIME_SERIES_INTRADAY",
                    extra = mapOf(
                        "symbol" to symbol,
                        "interval" to "5min",
                        "outputsize" to "compact",
                        "datatype" to "json"
                    )
                )

                val response = URL(url).readText()
                val json = JSONObject(response)

                val apiMessage = extractApiMessage(json)
                if (apiMessage != null) {
                    if (isRateLimitMessage(apiMessage)) {
                        callback(ChartLoadResult.RateLimited(apiMessage))
                    } else {
                        callback(ChartLoadResult.Error(apiMessage))
                    }
                    return@thread
                }

                val seriesKey = "Time Series (5min)"
                if (!json.has(seriesKey)) {
                    callback(ChartLoadResult.NoData("לא התקבלו נתוני גרף תוך-יומיים"))
                    return@thread
                }

                val series = json.getJSONObject(seriesKey)
                val keys = series.keys().asSequence().toList().sorted()

                if (keys.isEmpty()) {
                    callback(ChartLoadResult.NoData("גרף תוך-יומי ריק"))
                    return@thread
                }

                val entries = ArrayList<Entry>()
                var index = 0f

                for (time in keys) {
                    val item = series.getJSONObject(time)
                    val close = item.optString("4. close", "0").toFloatOrNull() ?: 0f
                    if (close > 0f) {
                        entries.add(Entry(index, close))
                        index += 1f
                    }
                }

                if (entries.isEmpty()) {
                    callback(ChartLoadResult.NoData("לא נמצאו נקודות לגרף תוך-יומי"))
                    return@thread
                }

                callback(ChartLoadResult.Success(entries))

            } catch (e: Exception) {
                callback(ChartLoadResult.Error(e.message ?: "שגיאה בטעינת גרף תוך-יומי"))
            }
        }
    }

    private fun loadDailyChart(previousMessage: String?) {
        thread {
            try {
                val url = buildUrl(
                    function = "TIME_SERIES_DAILY",
                    extra = mapOf(
                        "symbol" to symbol,
                        "outputsize" to "compact",
                        "datatype" to "json"
                    )
                )

                val response = URL(url).readText()
                val json = JSONObject(response)

                val apiMessage = extractApiMessage(json)
                if (apiMessage != null) {
                    runOnUiThread {
                        chart.clear()
                        chartStatusText.text = apiMessage
                    }
                    return@thread
                }

                val seriesKey = "Time Series (Daily)"
                if (!json.has(seriesKey)) {
                    runOnUiThread {
                        chart.clear()
                        chartStatusText.text = previousMessage ?: "לא התקבלו נתוני גרף"
                    }
                    return@thread
                }

                val series = json.getJSONObject(seriesKey)
                val keys = series.keys().asSequence().toList().sorted().takeLast(30)

                if (keys.isEmpty()) {
                    runOnUiThread {
                        chart.clear()
                        chartStatusText.text = previousMessage ?: "לא התקבלו נתוני גרף"
                    }
                    return@thread
                }

                val entries = ArrayList<Entry>()
                var index = 0f

                for (date in keys) {
                    val item = series.getJSONObject(date)
                    val close = item.optString("4. close", "0").toFloatOrNull() ?: 0f
                    if (close > 0f) {
                        entries.add(Entry(index, close))
                        index += 1f
                    }
                }

                if (entries.isEmpty()) {
                    runOnUiThread {
                        chart.clear()
                        chartStatusText.text = previousMessage ?: "לא התקבלו נתוני גרף"
                    }
                    return@thread
                }

                runOnUiThread {
                    renderChart(entries, "$symbol daily")
                    chartStatusText.text = "נטען גרף יומי חלופי"
                }

            } catch (_: Exception) {
                runOnUiThread {
                    chart.clear()
                    chartStatusText.text = previousMessage ?: "לא התקבלו נתוני גרף"
                }
            }
        }
    }

    private fun loadRsi() {
        thread {
            try {
                val url = buildUrl(
                    function = "RSI",
                    extra = mapOf(
                        "symbol" to symbol,
                        "interval" to "daily",
                        "time_period" to "14",
                        "series_type" to "close"
                    )
                )

                val response = URL(url).readText()
                val json = JSONObject(response)

                val apiMessage = extractApiMessage(json)
                if (apiMessage != null) {
                    runOnUiThread {
                        rsiText.text = "RSI: --"
                    }
                    return@thread
                }

                if (!json.has("Technical Analysis: RSI")) {
                    runOnUiThread {
                        rsiText.text = "RSI: --"
                    }
                    return@thread
                }

                val rsiObject = json.getJSONObject("Technical Analysis: RSI")
                val latestKey = rsiObject.keys().asSequence().toList().sorted().lastOrNull()

                if (latestKey == null) {
                    runOnUiThread {
                        rsiText.text = "RSI: --"
                    }
                    return@thread
                }

                val rsiValue = rsiObject.getJSONObject(latestKey).optString("RSI", "--")

                runOnUiThread {
                    rsiText.text = "RSI: $rsiValue"
                }

            } catch (_: Exception) {
                runOnUiThread {
                    rsiText.text = "RSI: --"
                }
            }
        }
    }

    private fun renderChart(entries: List<Entry>, label: String) {
        val dataSet = LineDataSet(entries, label).apply {
            setDrawCircles(false)
            lineWidth = 2f
            setDrawValues(false)
        }
        chart.data = LineData(dataSet)
        chart.invalidate()
    }

    private fun extractApiMessage(json: JSONObject): String? {
        if (json.has("Note")) {
            return "הגעת למגבלת הבקשות של Alpha Vantage. המתן מעט ונסה שוב."
        }
        if (json.has("Information")) {
            return json.optString("Information", "לא התקבלו נתונים")
        }
        if (json.has("Error Message")) {
            return json.optString("Error Message", "שגיאה בנתוני המניה")
        }
        return null
    }

    private fun isRateLimitMessage(message: String): Boolean {
        val lower = message.lowercase()
        return lower.contains("rate limit") ||
            lower.contains("requests") ||
            lower.contains("please consider spreading out") ||
            lower.contains("25 requests per day") ||
            lower.contains("1 request per second")
    }

    private fun normalizeSymbol(raw: String): String {
        return raw.trim()
            .replace("+", "")
            .replace(Regex("[^A-Za-z0-9.\\-]"), "")
            .uppercase()
    }

    private fun buildUrl(function: String, extra: Map<String, String>): String {
        val base = StringBuilder("https://www.alphavantage.co/query?function=$function")
        for ((k, v) in extra) {
            base.append("&")
            base.append(k)
            base.append("=")
            base.append(URLEncoder.encode(v, "UTF-8"))
        }
        base.append("&apikey=")
        base.append(URLEncoder.encode(apiKey, "UTF-8"))
        return base.toString()
    }

    private sealed class ChartLoadResult {
        data class Success(val entries: List<Entry>) : ChartLoadResult()
        data class NoData(val message: String) : ChartLoadResult()
        data class RateLimited(val message: String) : ChartLoadResult()
        data class Error(val message: String) : ChartLoadResult()
    }
}
