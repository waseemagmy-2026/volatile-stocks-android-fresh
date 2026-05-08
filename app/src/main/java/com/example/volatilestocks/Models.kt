package com.example.volatilestocks

data class StockSignal(
    val symbol: String,
    val price: Double,
    val changePercent: Double,
    val volume: Long,
    val score: Int,
    val summary: String
)

data class IntradayPoint(
    val timestampLabel: String,
    val close: Double,
    val high: Double,
    val low: Double,
    val volume: Long
)

data class StockDetailData(
    val symbol: String,
    val lastPrice: Double,
    val dayHigh: Double,
    val rsi: Double?,
    val points: List<IntradayPoint>
)
