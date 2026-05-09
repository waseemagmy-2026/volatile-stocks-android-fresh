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
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread
import kotlin.math.ln

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: AppPrefs

    private lateinit var apiKeyInput: EditText
    private lateinit var manualSymbolInput: EditText
    private lateinit var openSymbolButton: Button

    private lateinit var backendUrlInput: EditText
    private lateinit var aiModeSpinner: Spinner
    private lateinit var aiAnalyzeButton: Button

    private lateinit var minPriceInput: EditText
    private lateinit var maxPriceInput: EditText
    private lateinit var minVolumeInput: EditText
    private lateinit var minChangeInput: EditText
    private lateinit var maxChangeInput: EditText
    private lateinit var sortSpinner: Spinner
    private lateinit var scanButton: Button
    private lateinit var statusText: TextView
    private lateinit var resultsContainer: LinearLayout

    private val sortOptions = listOf("ציון איכות", "שינוי יומי", "מחזור", "מחיר")
    private val aiModes = listOf("balanced", "aggressive", "conservative")

    private var lastScanResults: List<ScanStock> = emptyList()
    private var lastAiAnalyses: Map<String, AiAnalysis> = emptyMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = AppPrefs(this)

        bindViews()
        setupSavedValues()
        setupSpinners()
        setupActions()
    }

    private fun bindViews() {
        apiKeyInput = findViewById(R.id.apiKeyInput)
        manualSymbolInput = findViewById(R.id.manualSymbolInput)
        openSymbolButton = findViewById(R.id.openSymbolButton)

        backendUrlInput = findViewById(R.id.backendUrlInput)
        aiModeSpinner = findViewById(R.id.aiModeSpinner)
        aiAnalyzeButton = findViewById(R.id.aiAnalyzeButton)

        minPriceInput = findViewById(R.id.minPriceInput)
        maxPriceInput = findViewById(R.id.maxPriceInput)
        minVolumeInput = findViewById(R.id.minVolumeInput)
        minChangeInput = findViewById(R.id.minChangeInput)
        maxChangeInput = findViewById(R.id.maxChangeInput)
        sortSpinner = findViewById(R.id.sortSpinner)
        scanButton = findViewById(R.id.scanButton)
        statusText = findViewById(R.id.statusText)
        resultsContainer = findViewById(R.id.resultsContainer)
    }

    private fun setupSavedValues() {
        apiKeyInput.setText(prefs.getApiKey())
        manualSymbolInput.setText(prefs.getString("manual_symbol", ""))
        backendUrlInput.setText(prefs.getString("backend_url", "http://127.0.0.1:3000"))

        minPriceInput.setText(prefs.getString("min_price", "1"))
        maxPriceInput.setText(prefs.getString("max_price", "50"))
        minVolumeInput.setText(prefs.getString("min_volume", "100000"))
        minChangeInput.setText(prefs.getString("min_change", "3"))
        maxChangeInput.setText(prefs.getString("max_change", "80"))

        statusText.text = "מוכן לסריקה"
        aiAnalyzeButton.isEnabled = false
    }

    private fun setupSpinners() {
        val sortAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            sortOptions
        )
        sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        sortSpinner.adapter = sortAdapter

        val aiModeAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            aiModes
        )
        aiModeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        aiModeSpinner.adapter = aiModeAdapter

        val savedSort = prefs.getString("sort_option", "ציון איכות")
        sortSpinner.setSelection(sortOptions.indexOf(savedSort).coerceAtLeast(0))

        val savedAiMode = prefs.getString("ai_mode", "balanced")
        aiModeSpinner.setSelection(aiModes.indexOf(savedAiMode).coerceAtLeast(0))
    }

    private fun setupActions() {
        openSymbolButton.setOnClickListener {
            val clean = normalizeSymbol(manualSymbolInput.text.toString())
            if (clean.isBlank()) {
                statusText.text = "יש להזין סימבול מניה, למשל AAPL"
                return@setOnClickListener
            }

            prefs.saveString("manual_symbol", clean)
            openStockDetail(clean)
        }

        scanButton.setOnClickListener { runScan() }
        aiAnalyzeButton.setOnClickListener { runAiAnalysis() }
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
            resultsContainer.addView(buildErrorText("יש להזין מפתח API של Alpha Vantage"))
            return
        }

        if (minPrice > maxPrice) {
            statusText.text = "טווח מחיר לא תקין"
            resultsContainer.removeAllViews()
            resultsContainer.addView(buildErrorText("מחיר מינימום חייב להיות קטן או שווה למחיר מקסימום"))
            return
        }

        if (minChange > maxChange) {
            statusText.text = "טווח אחוזים לא תקין"
            resultsContainer.removeAllViews()
            resultsContainer.addView(buildErrorText("אחוז עלייה מינימלי חייב להיות קטן או שווה לאחוז עלייה מקסימלי"))
            return
        }

        saveScanPreferences(
            apiKey = apiKey,
            minPrice = minPrice,
            maxPrice = maxPrice,
            minVolume = minVolume,
            minChange = minChange,
            maxChange = maxChange,
            selectedSort = selectedSort
        )

        statusText.text = "טוען נתוני שוק..."
        resultsContainer.removeAllViews()
        scanButton.isEnabled = false
        aiAnalyzeButton.isEnabled = false
        lastScanResults = emptyList()
        lastAiAnalyses = emptyMap()

        thread {
            try {
                val url = "https://www.alphavantage.co/query?function=TOP_GAINERS_LOSERS&apikey=" +
                    URLEncoder.encode(apiKey, "UTF-8")

                val response = URL(url).readText()
                val json = JSONObject(response)

                val apiError = extractApiError(json)
                if (apiError != null) {
                    throw Exception(apiError)
                }

                val topGainers = json.optJSONArray("top_gainers") ?: JSONArray()
                val uniqueStocks = linkedMapOf<String, ScanStock>()

                for (i in 0 until topGainers.length()) {
                    val item = topGainers.optJSONObject(i) ?: continue

                    val rawSymbol = item.optString("ticker", "")
                    val symbol = normalizeSymbol(rawSymbol)
                    if (symbol.isBlank()) continue

                    val price = parseDouble(item.optString("price", "0"))
                    val changePercent = parsePercent(item.optString("change_percentage", "0"))
                    val volume = parseLong(item.optString("volume", "0"))

                    if (price <= 0.0) continue
                    if (price < minPrice || price > maxPrice) continue
                    if (volume < minVolume) continue
                    if (changePercent < minChange || changePercent > maxChange) continue

                    val score = computeQualityScore(price, changePercent, volume)

                    uniqueStocks[symbol] = ScanStock(
                        symbol = symbol,
                        rawSymbol = rawSymbol,
                        price = price,
                        changePercent = changePercent,
                        volume = volume,
                        score = score
                    )
                }

                val filtered = uniqueStocks.values.toList()

                val sorted = when (selectedSort) {
                    "שינוי יומי" -> filtered.sortedByDescending { it.changePercent }
                    "מחזור" -> filtered.sortedByDescending { it.volume }
                    "מחיר" -> filtered.sortedByDescending { it.price }
                    else -> filtered.sortedByDescending { it.score }
                }

                runOnUiThread {
                    scanButton.isEnabled = true
                    aiAnalyzeButton.isEnabled = sorted.isNotEmpty()
                    lastScanResults = sorted
                    renderResults(sorted)
                }

            } catch (e: Exception) {
                runOnUiThread {
                    scanButton.isEnabled = true
                    aiAnalyzeButton.isEnabled = false
                    statusText.text = "שגיאה בטעינת הסורק"
                    resultsContainer.removeAllViews()
                    resultsContainer.addView(buildErrorText(e.message ?: "שגיאה לא ידועה"))
                }
            }
        }
    }

    private fun runAiAnalysis() {
        if (lastScanResults.isEmpty()) {
            statusText.text = "אין תוצאות לניתוח AI"
            return
        }

        val backendUrl = backendUrlInput.text.toString().trim().removeSuffix("/")
        if (backendUrl.isBlank()) {
            statusText.text = "יש להזין כתובת שרת AI"
            return
        }

        val mode = aiModeSpinner.selectedItem?.toString() ?: "balanced"
        prefs.saveString("backend_url", backendUrl)
        prefs.saveString("ai_mode", mode)

        statusText.text = "שולח לניתוח AI..."
        aiAnalyzeButton.isEnabled = false

        val topStocks = lastScanResults.take(3)

        thread {
            try {
                val reqJson = JSONObject().apply {
                    put("mode", mode)
                    put("stocks", JSONArray().apply {
                        topStocks.forEach { s ->
                            put(JSONObject().apply {
                                put("symbol", s.symbol)
                                put("price", s.price)
                                put("changePercent", s.changePercent)
                                put("volume", s.volume)
                                put("score", s.score)
                                put("notes", buildNotes(s))
                            })
                        }
                    })
                }

                val conn = URL("$backendUrl/analyze-stocks").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 15000
                conn.readTimeout = 25000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")

                BufferedWriter(OutputStreamWriter(conn.outputStream, Charsets.UTF_8)).use {
                    it.write(reqJson.toString())
                    it.flush()
                }

                val code = conn.responseCode
                val body = if (code in 200..299) {
                    BufferedReader(conn.inputStream.reader()).readText()
                } else {
                    BufferedReader(conn.errorStream.reader()).readText()
                }

                if (code !in 200..299) {
                    throw Exception("AI server error: $body")
                }

                val resp = JSONObject(body)
                val analysesArr = resp.optJSONArray("analyses") ?: JSONArray()
                val map = LinkedHashMap<String, AiAnalysis>()

                for (i in 0 until analysesArr.length()) {
                    val a = analysesArr.getJSONObject(i)
                    val reasonsJson = a.optJSONArray("reasons") ?: JSONArray()
                    val reasons = mutableListOf<String>()
                    for (j in 0 until reasonsJson.length()) {
                        reasons.add(reasonsJson.optString(j))
                    }

                    val analysis = AiAnalysis(
                        symbol = a.optString("symbol"),
                        aiScore = a.optInt("aiScore"),
                        verdict = a.optString("verdict"),
                        riskLevel = a.optString("riskLevel"),
                        reasons = reasons,
                        warning = a.optString("warning")
                    )
                    map[analysis.symbol.uppercase()] = analysis
                }

                runOnUiThread {
                    lastAiAnalyses = map
                    aiAnalyzeButton.isEnabled = true
                    statusText.text = "ניתוח AI הושלם"
                    renderResults(lastScanResults)
                }

            } catch (e: Exception) {
                runOnUiThread {
                    aiAnalyzeButton.isEnabled = true
                    statusText.text = "שגיאה בניתוח AI"
                    renderResults(lastScanResults)
                    resultsContainer.addView(buildErrorText(e.message ?: "AI failed"))
                }
            }
        }
    }

    private fun renderResults(results: List<ScanStock>) {
        resultsContainer.removeAllViews()

        if (results.isEmpty()) {
            statusText.text = "לא נמצאו מניות תואמות"
            resultsContainer.addView(
                buildInfoText("נסה להרחיב טווח מחיר, להוריד מחזור מינימלי, או לפתוח מניה ידנית")
            )
            return
        }

        statusText.text = "נמצאו ${results.size} מניות תואמות"
        results.forEach { stock ->
            resultsContainer.addView(buildStockCard(stock, lastAiAnalyses[stock.symbol.uppercase()]))
        }
    }

    private fun buildStockCard(stock: ScanStock, ai: AiAnalysis?): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 28)
            setBackgroundColor(Color.parseColor("#101010"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 24
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
            setPadding(0, 18, 0, 18)
        }

        val openButton = Button(this).apply {
            text = "פתח פירוט"
            setOnClickListener { openStockDetail(stock.symbol) }
        }

        card.addView(title)
        card.addView(info)

        if (ai != null) {
            val aiBox = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(20, 20, 20, 20)
                setBackgroundColor(Color.parseColor("#18202A"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 12
                    bottomMargin = 16
                }
            }

            aiBox.addView(TextView(this).apply {
                text = "ניתוח AI: ${ai.aiScore}/100 | ${ai.verdict}"
                textSize = 17f
                setTextColor(Color.WHITE)
                gravity = Gravity.END
            })

            aiBox.addView(TextView(this).apply {
                text = "רמת סיכון: ${ai.riskLevel}"
                textSize = 15f
                setTextColor(Color.WHITE)
                gravity = Gravity.END
                setPadding(0, 10, 0, 0)
            })

            aiBox.addView(TextView(this).apply {
                text = "סיבות:\n" + ai.reasons.joinToString("\n") { "• $it" }
                textSize = 15f
                setTextColor(Color.WHITE)
                gravity = Gravity.END
                setPadding(0, 10, 0, 0)
            })

            aiBox.addView(TextView(this).apply {
                text = "אזהרה: ${ai.warning}"
                textSize = 15f
                setTextColor(Color.parseColor("#FFD166"))
                gravity = Gravity.END
                setPadding(0, 10, 0, 0)
            })

            card.addView(aiBox)
        }

        card.addView(openButton)
        return card
    }

    private fun saveScanPreferences(
        apiKey: String,
        minPrice: Double,
        maxPrice: Double,
        minVolume: Long,
        minChange: Double,
        maxChange: Double,
        selectedSort: String
    ) {
        prefs.saveApiKey(apiKey)
        prefs.saveString("min_price", minPrice.toString())
        prefs.saveString("max_price", maxPrice.toString())
        prefs.saveString("min_volume", minVolume.toString())
        prefs.saveString("min_change", minChange.toString())
        prefs.saveString("max_change", maxChange.toString())
        prefs.saveString("sort_option", selectedSort)
    }

    private fun buildNotes(s: ScanStock): String {
        return when {
            s.score >= 85 -> "Strong momentum and strong volume"
            s.score >= 70 -> "Good setup with decent participation"
            else -> "Watch carefully, setup is not top tier"
        }
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
        return raw.trim()
            .replace("+", "")
            .replace(Regex("[^A-Za-z0-9.\\-]"), "")
            .uppercase()
    }

    private fun parseDouble(value: String): Double {
        return value.replace(",", "").trim().toDoubleOrNull() ?: 0.0
    }

    private fun parsePercent(value: String): Double {
        return value.replace("%", "").replace(",", "").trim().toDoubleOrNull() ?: 0.0
    }

    private fun parseLong(value: String): Long {
        return value.replace(",", "").trim().toLongOrNull() ?: 0L
    }

    private fun extractApiError(json: JSONObject): String? {
        if (json.has("Note")) {
            return json.optString("Note", "הגעת למגבלת הבקשות של Alpha Vantage")
        }
        if (json.has("Information")) {
            return json.optString("Information", "לא התקבלה תשובה תקינה מהשרת")
        }
        if (json.has("Error Message")) {
            return json.optString("Error Message", "שגיאה מהשרת")
        }
        return null
    }

    private fun computeQualityScore(price: Double, changePercent: Double, volume: Long): Int {
        val priceScore = when {
            price in 1.0..30.0 -> 30.0
            price in 30.0..80.0 -> 22.0
            else -> 15.0
        }

        val changeScore = changePercent.coerceIn(0.0, 60.0) * 0.8
        val volumeScore = (ln(volume.coerceAtLeast(1).toDouble()) * 4.5).coerceAtMost(30.0)

        return (priceScore + changeScore + volumeScore).toInt().coerceIn(1, 100)
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
