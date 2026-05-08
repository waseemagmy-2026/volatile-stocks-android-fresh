package com.example.volatilestocks

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.DecimalFormat
import kotlin.concurrent.thread

class StockDetailActivity : AppCompatActivity() {

    private lateinit var symbolTitle: TextView
    private lateinit var detailStatusText: TextView
    private lateinit var lineChartView: LineChartView
    private lateinit var rsiText: TextView
    private lateinit var highText: TextView
    private lateinit var lastPriceText: TextView
    private lateinit var dropAlertInput: EditText
    private lateinit var nearHighInput: EditText
    private lateinit var saveAlertButton: Button
    private lateinit var refreshNowButton: Button
    private lateinit var prefs: AppPrefs
    private lateinit var notifications: NotificationHelper

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var symbol: String
    private var referencePrice: Double? = null

    private val refreshRunnable = object : Runnable {
        override fun run() {
            loadDetail()
            handler.postDelayed(this, 15_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stock_detail)

        prefs = AppPrefs(this)
        notifications = NotificationHelper(this)

        symbol = intent.getStringExtra("symbol") ?: "N/A"
        symbolTitle = findViewById(R.id.symbolTitle)
        detailStatusText = findViewById(R.id.detailStatusText)
        lineChartView = findViewById(R.id.lineChartView)
        rsiText = findViewById(R.id.rsiText)
        highText = findViewById(R.id.highText)
        lastPriceText = findViewById(R.id.lastPriceText)
        dropAlertInput = findViewById(R.id.dropAlertInput)
        nearHighInput = findViewById(R.id.nearHighInput)
        saveAlertButton = findViewById(R.id.saveAlertButton)
        refreshNowButton = findViewById(R.id.refreshNowButton)

        symbolTitle.text = symbol
        dropAlertInput.setText(prefs.getString("drop_$symbol", "3"))
        nearHighInput.setText(prefs.getString("high_$symbol", "0.5"))

        saveAlertButton.setOnClickListener {
            prefs.saveString("drop_$symbol", dropAlertInput.text.toString())
            prefs.saveString("high_$symbol", nearHighInput.text.toString())
            detailStatusText.text = "ההתראות נשמרו"
        }

        refreshNowButton.setOnClickListener {
            loadDetail()
        }
    }

    override fun onResume() {
        super.onResume()
        loadDetail()
        handler.postDelayed(refreshRunnable, 15_000)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refreshRunnable)
    }

    private fun loadDetail() {
        val apiKey = prefs.getApiKey()
        if (apiKey.isBlank()) {
            detailStatusText.text = "חסר API key במסך הראשי"
            return
        }

        detailStatusText.text = "טוען גרף, RSI והתראות..."

        thread {
            try {
                val detail = AlphaVantageService(apiKey).fetchDetail(symbol)
                runOnUiThread {
                    bindDetail(detail)
                    detailStatusText.text = "עודכן ${detail.points.lastOrNull()?.timestampLabel ?: ""}"
                }
            } catch (e: Exception) {
                runOnUiThread {
                    detailStatusText.text = e.message ?: "שגיאה בטעינת נתוני מניה"
                    detailStatusText.setTextColor(Color.RED)
                }
            }
        }
    }

    private fun bindDetail(detail: StockDetailData) {
        val dropPercent = dropAlertInput.text.toString().toDoubleOrNull() ?: 3.0
        val nearHighPercent = nearHighInput.text.toString().toDoubleOrNull() ?: 0.5

        val closes = detail.points.map { it.close }
        lineChartView.setValues(closes)
        rsiText.text = "RSI: ${detail.rsi?.let { DecimalFormat("0.00").format(it) } ?: "--"}"
        highText.text = "שיא יומי: ${DecimalFormat("0.00").format(detail.dayHigh)}"
        lastPriceText.text = "מחיר אחרון: ${DecimalFormat("0.00").format(detail.lastPrice)}"

        val baseline = referencePrice ?: detail.lastPrice.also { referencePrice = it }
        val dropFromReference = if (baseline == 0.0) 0.0 else ((baseline - detail.lastPrice) / baseline) * 100.0
        val distanceToHigh = if (detail.dayHigh == 0.0) 100.0 else ((detail.dayHigh - detail.lastPrice) / detail.dayHigh) * 100.0

        if (dropFromReference >= dropPercent) {
            notifications.showNotification(
                "$symbol ירדה חזק",
                "$symbol ירדה ${DecimalFormat("0.00").format(dropFromReference)}% ממחיר הייחוס.",
                symbol.hashCode() + 10
            )
            referencePrice = detail.lastPrice
        }

        if (distanceToHigh <= nearHighPercent) {
            notifications.showNotification(
                "$symbol קרובה לשיא",
                "$symbol נמצאת במרחק ${DecimalFormat("0.00").format(distanceToHigh)}% מהשיא היומי.",
                symbol.hashCode() + 20
            )
        }
    }
}
