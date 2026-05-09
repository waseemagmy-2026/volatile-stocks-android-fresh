package com.example.volatilestocks

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread
import kotlin.math.ln

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: AppPrefs

    private lateinit var apiKeyInput: EditText
    private lateinit var manualSymbolInput: EditText
    private lateinit var openSymbolButton: Button

    private lateinit var minPriceInput: EditText
    private lateinit var maxPriceInput: EditText
    private lateinit var minVolumeInput: EditText
    private lateinit var minChangeInput: EditText
    private lateinit var maxChangeInput: EditText
    private lateinit var sortSpinner: Spinner
    private lateinit var scanButton: Button
    private lateinit var statusText: TextView
    private lateinit var resultsContainer: LinearLayout

    private val sortOptions = listOf(
        "ציון איכות",
        "שינוי יומי",
        "מחזור",
        "מחיר"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = AppPrefs(this)

        apiKeyInput = findViewById(R.id.apiKeyInput)
        manualSymbolInput = findViewById(R.id.manualSymbolInput)
        openSymbolButton = findViewById(R.id.openSymbolButton)

        minPriceInput = findViewById(R.id.minPriceInput)
        maxPriceInput = findViewById(R.id.maxPriceInput)
        minVolumeInput = findViewById(R.id.minVolumeInput)
        minChangeInput = findViewById(R.id.minChangeInput)
        maxChangeInput = findViewById(R.id.maxChangeInput)
        sortSpinner = findViewById(R.id.sortSpinner)
        scanButton = findViewById(R.id.scanButton)
        statusText = findViewById(R.id.statusText)
        resultsContainer = findViewById(R.id.resultsContainer)

        apiKeyInput.setText(prefs.getApiKey())
        minPriceInput.setText(prefs.getString("min_price", "1"))
        maxPriceInput.setText(prefs.getString("max_price", "50"))
        minVolumeInput.setText(prefs.getString("min_volume", "100000"))
        minChangeInput.setText(prefs.getString("min_change", "3"))
        maxChangeInput.setText(prefs.getString("max_change", "80"))
        manualSymbolInput.setText(prefs.getString("manual_symbol", ""))

        val spinnerAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            sortOptions
        )
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        sortSpinner.adapter = spinnerAdapter

        val savedSort = prefs.getString("sort_option", "ציון איכות")
        val savedIndex = sortOptions.indexOf(savedSort).coerceAtLeast(0)
        sortSpinner.setSelection(savedIndex)

        openSymbolButton.setOnClickListener {
            val raw = manualSymbolInput.text.toString().trim()
            val clean = normalizeSymbol(raw)

            if (clean.isBlank()) {
                statusText.text = "יש להזין סימבול מניה, למשל AAPL"
                return@setOnClickListener
            }

            prefs.saveString("manual_symbol", clean)
            openStockDetail(clean)
        }

        scanButton.setOnClickListener {
            runScan()
        }
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
            resultsContainer.addView(buildErrorText("יש להזין מפתח API של Alpha Vantage."))
            return
        }

        prefs.saveApiKey(apiKey)
        prefs.saveString("min_price", minPrice.toString())
        prefs.saveString("max_price", maxPrice.toString())
        prefs.saveString("min_volume", minVolume.toString())
        prefs.saveString("min_change", minChange.toString())
        prefs.saveString("max_change", maxChange.toString())
        prefs.saveString("sort_option", selectedSort)

        statusText.text = "טוען נתוני שוק..."
        resultsContainer.removeAllViews()
        scanButton.isEnabled = false

        thread {
            try {
                val url = "https://www.alphavantage.co/query?function=TOP_GAINERS_LOSERS&apikey=" +
                    URLEncoder.encode(apiKey, "UTF-8")

                val response = URL(url).readText()
                val json = JSONObject(response)

                if (json.has("Note")) {
                    throw Exception("הגעת למגבלת הבקשות של Alpha Vantage. נסה שוב בעוד דקה.")
                }
                if (json.has("Information")) {
                    throw Exception(json.optString("Information", "לא התקבלה תשובה תקינה מהשרת."))
                }

                val topGainers = json.optJSONArray("top_gainers") ?: JSONArray()
                val filtered = ArrayList<ScanStock>()

                for (i in 0 until topGainers.length()) {
                    val item = topGainers.optJSONObject(i) ?: continue

                    val rawSymbol = item.optString("ticker", "")
                    val symbol = normalizeSymbol(rawSymbol)
                    if (symbol.isBlank()) continue

                    val price = parseDouble(item.optString("price", "0"))
                    val changePercent = parsePercent(item.optString("change_percentage", "0"))
                    val volume = parseLong(item.optString("volume", "0"))

                    if (price < minPrice || price > maxPrice) continue
                    if (volume < minVolume) continue
                    if (changePercent < minChange || changePercent > maxChange) continue

                    val score = computeQualityScore(price, changePercent, volume)

                    filtered.add(
                        ScanStock(
                            symbol = symbol,
                            rawSymbol = rawSymbol,
                            price = price,
                            changePercent = changePercent,
                            volume = volume,
                            score = score
                        )
                    )
                }

                val sorted = when (selectedSort) {
                    "שינוי יומי" -> filtered.sortedByDescending { it.changePercent }
                    "מחזור" -> filtered.sortedByDescending { it.volume }
                    "מחיר" -> filtered.sortedByDescending { it.price }
                    else -> filtered.sortedByDescending { it.score }
                }

                runOnUiThread {
                    scanButton.isEnabled = true
                    renderResults(sorted)
                }

            } catch (e: Exception) {
                runOnUiThread {
                    scanButton.isEnabled = true
                    statusText.text = "שגיאה בטעינת הסורק"
                    resultsContainer.removeAllViews()
                    resultsContainer.addView(buildErrorText(e.message ?: "שגיאה לא ידועה"))
                }
            }
        }
    }

    private fun renderResults(results: List<ScanStock>) {
        resultsContainer.removeAllViews()

        if (results.isEmpty()) {
            statusText.text = "לא נמצאו מניות תואמות"
            resultsContainer.addView(
                buildInfoText("נסה להרחיב טווח מחיר, להוריד מחזור מינימלי, או לפתוח מניה ידנית.")
            )
            return
        }

        statusText.text = "נמצאו ${results.size} מניות תואמות"

        results.forEachIndexed { index, stock ->
            resultsContainer.addView(buildStockCard(stock, index))
        }
    }

    private fun buildStockCard(stock: ScanStock, index: Int): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 28)
            setBackgroundColor(Color.parseColor("#101010"))
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = 24
            layoutParams = params
            setOnClickListener {
                openStockDetail(stock.symbol)
            }
        }

        val title = TextView(this).apply {
            text = "${stock.symbol} | $${"%.2f".format(stock.price)}"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.START
        }

        val info = TextView(this).apply {
            text = buildString {
                append("שינוי יומי: ${"%.2f".format(stock.changePercent)}%\n")
                append("מחזור: ${formatVolume(stock.volume)}\n")
                append("ציון איכות: ${stock.score}/100\n")
                append(
                    when {
                        stock.rawSymbol != stock.symbol ->
                            "סיכום: זוהה סימבול עם תווים מיוחדים, נוקה ל-${stock.symbol} לפתיחה טובה יותר."
                        stock.score >= 85 ->
                            "סיכום: תנודתיות חזקה, מחזור טוב וסיכוי מעניין לבדיקה."
                        stock.score >= 70 ->
                            "סיכום: מניה פעילה עם נתונים טובים יחסית."
                        else ->
                            "סיכום: מניה סבירה, מומלץ לבדוק גרף ו-RSI לפני החלטה."
                    }
                )
            }
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.END
            setPadding(0, 18, 0, 0)
        }

        val openButton = Button(this).apply {
            text = "פתח פירוט"
            setOnClickListener {
                openStockDetail(stock.symbol)
            }
        }

        card.addView(title)
        card.addView(info)
        card.addView(openButton)

        return card
    }

    private fun buildErrorText(message: String): TextView {
        return TextView(this).apply {
            text = message
            textSize = 16f
            setTextColor(Color.parseColor("#FF6B6B"))
            gravity = Gravity.CENTER
            setPadding(16, 32, 16, 32)
        }
    }

    private fun buildInfoText(message: String): TextView {
        return TextView(this).apply {
            text = message
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(16, 32, 16, 32)
        }
    }

    private fun openStockDetail(symbol: String) {
        val intent = Intent(this, StockDetailActivity::class.java)
        intent.putExtra("symbol", normalizeSymbol(symbol))
        startActivity(intent)
    }

    private fun normalizeSymbol(raw: String): String {
        return raw
            .trim()
            .replace("+", "")
            .replace(Regex("[^A-Za-z0-9.\\-]"), "")
            .uppercase()
    }

    private fun parseDouble(value: String): Double {
        return value
            .replace(",", "")
            .trim()
            .toDoubleOrNull() ?: 0.0
    }

    private fun parsePercent(value: String): Double {
        return value
            .replace("%", "")
            .replace(",", "")
            .trim()
            .toDoubleOrNull() ?: 0.0
    }

    private fun parseLong(value: String): Long {
        return value
            .replace(",", "")
            .trim()
            .toLongOrNull() ?: 0L
    }

    private fun computeQualityScore(price: Double, changePercent: Double, volume: Long): Int {
        val priceScore = when {
            price in 1.0..30.0 -> 30.0
            price in 30.0..80.0 -> 22.0
            else -> 15.0
        }

        val changeScore = changePercent.coerceIn(0.0, 60.0) * 0.8
        val volumeScore = (ln(volume.coerceAtLeast(1).toDouble()) * 4.5).coerceAtMost(30.0)

        return (priceScore + changeScore + volumeScore)
            .toInt()
            .coerceIn(1, 100)
    }

    private fun formatVolume(volume: Long): String {
        return when {
            volume >= 1_000_000_000 -> "%.2fB".format(volume / 1_000_000_000.0)
            volume >= 1_000_000 -> "%.1fM".format(volume / 1_000_000.0)
            volume >= 1_000 -> "%.1fK".format(volume / 1_000.0)
            else -> volume.toString()
        }
    }
}

data class ScanStock(
    val symbol: String,
    val rawSymbol: String,
    val price: Double,
    val changePercent: Double,
    val volume: Long,
    val score: Int
)
