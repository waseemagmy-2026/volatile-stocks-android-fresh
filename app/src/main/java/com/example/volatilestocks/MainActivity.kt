package com.example.volatilestocks

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.DecimalFormat
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var scanButton: Button
    private lateinit var statusText: TextView
    private lateinit var resultsContainer: LinearLayout
    private lateinit var apiKeyInput: EditText
    private lateinit var minPriceInput: EditText
    private lateinit var maxPriceInput: EditText
    private lateinit var minVolumeInput: EditText
    private lateinit var minChangeInput: EditText
    private lateinit var maxChangeInput: EditText
    private lateinit var sortSpinner: Spinner
    private lateinit var prefs: AppPrefs

    private val sortOptions = listOf(
        "ציון איכות",
        "אחוז עלייה",
        "מחזור",
        "מחיר נמוך לגבוה",
        "מחיר גבוה לנמוך"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = AppPrefs(this)
        NotificationHelper(this).ensureChannel()
        requestNotificationPermissionIfNeeded()

        scanButton = findViewById(R.id.scanButton)
        statusText = findViewById(R.id.statusText)
        resultsContainer = findViewById(R.id.resultsContainer)
        apiKeyInput = findViewById(R.id.apiKeyInput)
        minPriceInput = findViewById(R.id.minPriceInput)
        maxPriceInput = findViewById(R.id.maxPriceInput)
        minVolumeInput = findViewById(R.id.minVolumeInput)
        minChangeInput = findViewById(R.id.minChangeInput)
        maxChangeInput = findViewById(R.id.maxChangeInput)
        sortSpinner = findViewById(R.id.sortSpinner)

        apiKeyInput.setText(prefs.getApiKey())
        minPriceInput.setText(prefs.getString("min_price", "1"))
        maxPriceInput.setText(prefs.getString("max_price", "50"))
        minVolumeInput.setText(prefs.getString("min_volume", "100000"))
        minChangeInput.setText(prefs.getString("min_change", "3"))
        maxChangeInput.setText(prefs.getString("max_change", "80"))

        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, sortOptions)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        sortSpinner.adapter = spinnerAdapter

        scanButton.setOnClickListener { runScan() }
    }

    private fun runScan() {
        val apiKey = apiKeyInput.text.toString().trim()
        val minPrice = minPriceInput.text.toString().toDoubleOrNull() ?: 1.0
        val maxPrice = maxPriceInput.text.toString().toDoubleOrNull() ?: 50.0
        val minVolume = minVolumeInput.text.toString().toLongOrNull() ?: 100000L
        val minChange = minChangeInput.text.toString().toDoubleOrNull() ?: 3.0
        val maxChange = maxChangeInput.text.toString().toDoubleOrNull() ?: 80.0
        val selectedSort = sortSpinner.selectedItem?.toString() ?: "ציון איכות"

        if (apiKey.isBlank()) {
            statusText.text = "חסר API KEY"
            resultsContainer.removeAllViews()
            resultsContainer.addView(buildErrorText("יש להכניס API key של Alpha Vantage."))
            return
        }

        prefs.saveApiKey(apiKey)
        prefs.saveString("min_price", minPrice.toString())
        prefs.saveString("max_price", maxPrice.toString())
        prefs.saveString("min_volume", minVolume.toString())
        prefs.saveString("min_change", minChange.toString())
        prefs.saveString("max_change", maxChange.toString())

        statusText.text = "טוען נתוני שוק..."
        resultsContainer.removeAllViews()
        scanButton.isEnabled = false

        thread {
            try {
                val service = AlphaVantageService(apiKey)
                val results = service.fetchScannerResults(minPrice, maxPrice, minVolume, minChange, maxChange, selectedSort)

                runOnUiThread {
                    if (results.isEmpty()) {
                        statusText.text = "לא נמצאו מניות מתאימות לפי הסינון"
                    } else {
                        statusText.text = "נמצאו ${results.size} מניות מתאימות"
                        results.forEach { stock ->
                            resultsContainer.addView(createStockCard(stock))
                        }
                    }
                    scanButton.isEnabled = true
                }
            } catch (e: Exception) {
                runOnUiThread {
                    statusText.text = "שגיאה בטעינת נתונים"
                    resultsContainer.addView(buildErrorText(e.message ?: "Unknown error"))
                    scanButton.isEnabled = true
                }
            }
        }
    }

    private fun createStockCard(stock: StockSignal): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(Color.parseColor("#111111"))
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = 24
            layoutParams = params
            setOnClickListener {
                openDetail(stock.symbol)
            }
        }

        val title = TextView(this).apply {
            text = "${stock.symbol}  |  $${formatPrice(stock.price)}"
            textSize = 22f
            setTextColor(Color.WHITE)
        }
        val change = TextView(this).apply {
            text = "שינוי יומי: ${DecimalFormat("0.00").format(stock.changePercent)}%"
            textSize = 18f
            setTextColor(if (stock.changePercent >= 0) Color.parseColor("#00C853") else Color.RED)
        }
        val volume = TextView(this).apply {
            text = "מחזור: ${formatVolume(stock.volume)}"
            textSize = 16f
            setTextColor(Color.LTGRAY)
        }
        val scoreText = TextView(this).apply {
            text = "ציון איכות: ${stock.score}/100"
            textSize = 16f
            setTextColor(Color.parseColor("#4FC3F7"))
        }
        val summary = TextView(this).apply {
            text = "סיכום: ${stock.summary}"
            textSize = 16f
            setTextColor(Color.WHITE)
        }
        val hint = TextView(this).apply {
            text = "הקש לפתיחת גרף, RSI והתראות"
            textSize = 14f
            setTextColor(Color.parseColor("#AAAAAA"))
        }

        card.addView(title)
        card.addView(change)
        card.addView(volume)
        card.addView(scoreText)
        card.addView(summary)
        card.addView(hint)
        return card
    }

    private fun buildErrorText(message: String): TextView {
        return TextView(this).apply {
            text = message
            textSize = 16f
            setTextColor(Color.RED)
            gravity = Gravity.CENTER
        }
    }

    private fun openDetail(symbol: String) {
        startActivity(Intent(this, StockDetailActivity::class.java).putExtra("symbol", symbol))
    }

    private fun formatPrice(price: Double): String {
        return if (price >= 1) DecimalFormat("0.00").format(price) else DecimalFormat("0.####").format(price)
    }

    private fun formatVolume(volume: Long): String {
        return when {
            volume >= 1_000_000_000 -> "${DecimalFormat("0.0").format(volume / 1_000_000_000.0)}B"
            volume >= 1_000_000 -> "${DecimalFormat("0.0").format(volume / 1_000_000.0)}M"
            volume >= 1_000 -> "${DecimalFormat("0.0").format(volume / 1_000.0)}K"
            else -> volume.toString()
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2001)
            }
        }
    }
}
