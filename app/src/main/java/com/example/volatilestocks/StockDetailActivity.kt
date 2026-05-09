package com.example.volatilestocks

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
    private lateinit var lineChart: LineChart

    private val handler = Handler(Looper.getMainLooper())
    private var refreshRunnable: Runnable? = null

    private var symbol: String = ""
    private var apiKey: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stock_detail)

        symbolText = findViewById(R.id.symbolText)
        chartStatusText = findViewById(R.id.chartStatusText)
        rsiText = findViewById(R.id.rsiText)
        highText = findViewById(R.id.highText)
        lastPriceText = findViewById(R.id.lastPriceText)
        dropThresholdInput = findViewById(R.id.dropThresholdInput)
        highThresholdInput = findViewById(R.id.highThresholdInput)
        saveAlertsButton = findViewById(R.id.saveAlertsButton)
        refreshButton = findViewById(R.id.refreshButton)
        lineChart = findViewById(R.id.lineChart)

        symbol = intent.getStringExtra("symbol") ?: ""
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
        startAutoRefresh()
    }

    override fun onDestroy() {
        super.onDestroy()
        refreshRunnable?.let { handler.removeCallbacks(it) }
    }

    private fun startAutoRefresh() {
        refreshRunnable = object : Runnable {
            override fun run() {
                loadAllData()
                handler.postDelayed(this, 15000)
            }
        }
        handler.postDelayed(refreshRunnable!!, 15000)
    }

    private fun setupChart() {
        lineChart.setNoDataText("No chart data")
        lineChart.setTouchEnabled(true)
        lineChart.setPinchZoom(true)
        lineChart.axisRight.isEnabled = false
        lineChart.legend.isEnabled = false

        val description = Description()
        description.text = ""
        lineChart.description = description
    }

    private fun loadAllData() {
        if (apiKey.isBlank()) {
            chartStatusText.text = "אין API KEY"
            return
        }

        chartStatusText.text = "טוען נתוני גרף ו-RSI..."
        loadIntradayChart()
        loadRsi()
        loadQuoteFallback()
    }

    private fun loadIntradayChart() {
        thread {
            try {
                val url = buildUrl(
                    "TIME_SERIES_INTRADAY",
                    mapOf(
                        "symbol" to symbol,
                        "interval" to "5min",
                        "outputsize" to "compact",
                        "datatype" to "json"
                    )
                )

                val response = URL(url).readText()
                val json = JSONObject(response)

                var seriesKey: String? = null
                if (json.has("Time Series (5min)")) {
                    seriesKey = "Time Series (5min)"
                } else if (json.has("Time Series (15min)")) {
                    seriesKey = "Time Series (15min)"
                }

                if (seriesKey == null) {
                    runOnUiThread {
                        lineChart.clear()
                        chartStatusText.text = "לא התקבלו נתוני גרף למניה $symbol"
                    }
                    return@thread
                }

                val series = json.getJSONObject(seriesKey)
                val times = ArrayList<String>()
                val iterator = series.keys()
                while (iterator.hasNext()) {
                    times.add(iterator.next())
                }
                times.sort()

                if (times.isEmpty()) {
                    runOnUiThread {
                        lineChart.clear()
                        chartStatusText.text = "לא התקבלו נתוני גרף למניה $symbol"
                    }
                    return@thread
                }

                val entries = ArrayList<Entry>()
                var index = 0f
                var dailyHigh = 0.0
                var lastClose = 0.0

                for (time in times) {
                    val item = series.getJSONObject(time)
                    val close = item.optString("4. close", "0").toFloatOrNull() ?: 0f
                    val high = item.optString("2. high", "0").toDoubleOrNull() ?: 0.0

                    if (high > dailyHigh) {
                        dailyHigh = high
                    }

                    lastClose = close.toDouble()
                    entries.add(Entry(index, close))
                    index += 1f
                }

                runOnUiThread {
                    val dataSet = LineDataSet(entries, symbol).apply {
                        setDrawCircles(false)
                        lineWidth = 2f
                        setDrawValues(false)
                    }

                    lineChart.data = LineData(dataSet)
                    lineChart.invalidate()

                    if (dailyHigh > 0) {
                        highText.text = "שיא יומי: %.2f".format(dailyHigh)
                    }
                    if (lastClose > 0) {
                        lastPriceText.text = "מחיר אחרון: %.2f".format(lastClose)
                    }

                    chartStatusText.text = "נתוני גרף נטענו"
                }

            } catch (e: Exception) {
                runOnUiThread {
                    lineChart.clear()
                    chartStatusText.text = "שגיאה בטעינת גרף"
                }
            }
        }
    }

    private fun loadRsi() {
        thread {
            try {
                val url = buildUrl(
                    "RSI",
                    mapOf(
                        "symbol" to symbol,
                        "interval" to "5min",
                        "time_period" to "14",
                        "series_type" to "close"
                    )
                )

                val response = URL(url).readText()
                val json = JSONObject(response)

                if (!json.has("Technical Analysis: RSI")) {
                    runOnUiThread {
                        rsiText.text = "RSI: --"
                    }
                    return@thread
                }

                val rsiObject = json.getJSONObject("Technical Analysis: RSI")
                val rsiTimes = ArrayList<String>()
                val iterator = rsiObject.keys()
                while (iterator.hasNext()) {
                    rsiTimes.add(iterator.next())
                }
                rsiTimes.sort()

                if (rsiTimes.isEmpty()) {
                    runOnUiThread {
                        rsiText.text = "RSI: --"
                    }
                    return@thread
                }

                val latestKey = rsiTimes[rsiTimes.size - 1]
                val rsiValue = rsiObject.getJSONObject(latestKey).optString("RSI", "--")

                runOnUiThread {
                    rsiText.text = "RSI: $rsiValue"
                }

            } catch (e: Exception) {
                runOnUiThread {
                    rsiText.text = "RSI: --"
                }
            }
        }
    }

    private fun loadQuoteFallback() {
        thread {
            try {
                val url = buildUrl(
                    "GLOBAL_QUOTE",
                    mapOf("symbol" to symbol)
                )

                val response = URL(url).readText()
                val json = JSONObject(response)

                if (!json.has("Global Quote")) return@thread

                val quote = json.getJSONObject("Global Quote")
                val price = quote.optString("05. price", "--")
                val high = quote.optString("03. high", "--")

                runOnUiThread {
                    if (lastPriceText.text.toString().contains("--")) {
                        lastPriceText.text = "מחיר אחרון: $price"
                    }
                    if (highText.text.toString().contains("--")) {
                        highText.text = "שיא יומי: $high"
                    }
                }
            } catch (_: Exception) {
            }
        }
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
}
