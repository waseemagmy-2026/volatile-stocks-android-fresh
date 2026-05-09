package com.example.volatilestocks

data class AiAnalyzeRequest(
    val mode: String,
    val stocks: List<AiStockInput>
)

data class AiStockInput(
    val symbol: String,
    val price: Double,
    val changePercent: Double,
    val volume: Long,
    val score: Int,
    val rsi: Double? = null,
    val distanceFromHighPct: Double? = null,
    val notes: String = ""
)

data class AiAnalyzeResponse(
    val analyses: List<AiAnalysis>
)

data class AiAnalysis(
    val symbol: String,
    val aiScore: Int,
    val verdict: String,
    val riskLevel: String,
    val reasons: List<String>,
    val warning: String
)
