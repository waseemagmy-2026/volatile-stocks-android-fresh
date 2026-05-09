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

        apiKeyInput.setText(prefs.getApiKey())
        manualSymbolInput.setText(prefs.getString("manual_symbol", ""))
        backendUrlInput.setText(prefs.getString("backend_url", "http://127.0.0.1:3000"))

        minPriceInput.setText(prefs.getString("min_price", "1"))
        maxPriceInput.setText(prefs.getString("max_price", "50"))
        minVolumeInput.setText(prefs.getString("min_volume", "100000"))
        minChangeInput.setText(prefs.getString("min_change", "3"))
        maxChangeInput.setText(prefs.getString("max_change", "80"))

        val spinnerAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            sortOptions
        )
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        sortSpinner.adapter = spinnerAdapter

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
        aiAnalyzeButton.isEnabled = false
        lastAiAnalyses = emptyMap()

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

        val topStocks = lastScanResults.take(5)

        thread {
            try {
                val reqJson = JSONObject()
                reqJson.put("mode", mode)

                val stocksArray = JSONArray()
                topStocks.forEach { s ->
                    val obj = JSONObject()
                    obj.put("symbol", s.symbol)
                    obj.put("price", s.price)
                    obj.put("changePercent", s.changePercent)
                    obj.put("volume", s.volume)
                    obj.put("score", s.score)
                    obj.put("notes", buildNotes(s))
                    stocksArray.put(obj)
                }
                reqJson.put("stocks", stocksArray)

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
                buildInfoText("נסה להרחיב טווח מחיר, להוריד מחזור מינימלי, או לפתוח מניה ידנית.")
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
            val params = Linear
