package com.indexdca.app

/** 一条定投记录 */
data class Holding(
    val id: String,
    val code: String,
    val name: String,
    var indexCode: String? = null,
    var indexName: String? = null,
    var shares: Double = 0.0,
    var costPrice: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis()
)

/** 全局设置（AI 接口 + 规则阈值） */
data class Settings(
    var aiBaseUrl: String = "https://api.deepseek.com",
    var aiApiKey: String = "",
    var aiModel: String = "deepseek-chat",
    var lowThreshold: Double = 30.0,
    var highThreshold: Double = 70.0,
    var extremeThreshold: Double = 80.0,
    var targetProfit: Double = 30.0
)

data class FundSearchItem(val code: String, val name: String)

data class NavPoint(val x: Long, val y: Double)

/** 从 pingzhongdata 解析出的基金走势分析 */
data class FundAnalysis(
    val code: String,
    val name: String,
    val points: List<NavPoint>,
    val latestNav: Double,
    val latestDate: String,
    val chg60d: Double?,
    val chg180d: Double?,
    val chg365d: Double?,
    val drawdownFromPeak: Double?,
    val peak1y: Double?
)

/** 指数估值（蛋卷数据，百分位已换算为 0~100） */
data class IndexValuation(
    val indexCode: String,
    val indexName: String,
    val pe: Double?,
    val pePercentile: Double?,
    val pb: Double?,
    val pbPercentile: Double?,
    val evaType: String?,
    val date: String?
) {
    val mainPercentile: Double? get() = pePercentile ?: pbPercentile
    val mainMetricLabel: String get() = if (pePercentile != null) "PE" else "PB"
    val mainMetricValue: Double? get() = if (pePercentile != null) pe else pb
}
