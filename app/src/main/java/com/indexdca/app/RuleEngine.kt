package com.indexdca.app

/** 规则引擎给出的信号 */
enum class Signal(val label: String) {
    ACCUMULATE("低估·积极定投"),
    NORMAL("正常定投"),
    CAUTION("放缓/暂停定投"),
    EXIT("考虑止盈/卖出"),
    UNKNOWN("数据不足")
}

data class Verdict(
    val signal: Signal,
    val score: Int,
    val reasons: List<String>
)

/**
 * 以「估值百分位」为核心的定投/止盈规则引擎：
 * - 百分位越低越适合定投，越高越要放缓甚至止盈
 * - 达到止盈线直接触发卖出信号
 * - 结合近 60 日涨跌与距 1 年高点回撤做辅助加减分
 */
object RuleEngine {

    fun evaluate(
        pePercentile: Double?,
        pbPercentile: Double?,
        chg60d: Double?,
        drawdownFromPeak: Double?,
        profitRate: Double?,
        s: Settings
    ): Verdict {
        val reasons = mutableListOf<String>()
        var score = 0

        val p = pePercentile ?: pbPercentile
        if (p != null) {
            when {
                p <= s.lowThreshold -> {
                    score += 3
                    reasons.add("估值百分位 ${Fmt.p1(p)}%，低于低估线 ${Fmt.p1(s.lowThreshold)}%，处于低估区，适合定投摊低成本")
                }
                p <= s.highThreshold -> {
                    score += 1
                    reasons.add("估值百分位 ${Fmt.p1(p)}%，处于合理区间，可正常定投")
                }
                p <= s.extremeThreshold -> {
                    score -= 2
                    reasons.add("估值百分位 ${Fmt.p1(p)}%，偏高，建议放缓或暂停定投")
                }
                else -> {
                    score -= 4
                    reasons.add("估值百分位 ${Fmt.p1(p)}%，处于高估区，注意止盈风险")
                }
            }
        } else {
            reasons.add("暂无对应指数估值数据，仅按走势与收益判断")
        }

        if (profitRate != null && profitRate >= s.targetProfit) {
            score -= 3
            reasons.add("当前收益 ${Fmt.p1(profitRate)}% 已达到止盈线 ${Fmt.p1(s.targetProfit)}%，建议分批止盈")
        } else if (profitRate != null && profitRate >= s.targetProfit * 0.75) {
            score -= 1
            reasons.add("当前收益 ${Fmt.p1(profitRate)}%，接近止盈线，可提前规划止盈方案")
        }

        if (chg60d != null && chg60d > 25) {
            score -= 1
            reasons.add("近60日已上涨 ${Fmt.p1(chg60d)}%，短期涨幅较大，追高风险上升")
        }
        if (chg60d != null && chg60d < -15) {
            score += 1
            reasons.add("近60日下跌 ${Fmt.p1(-chg60d)}%，处于回调区间（需区分低估回调与基本面恶化）")
        }
        if (drawdownFromPeak != null && drawdownFromPeak <= -25) {
            score += 1
            reasons.add("距近1年高点回撤 ${Fmt.p1(-drawdownFromPeak)}%，回撤中坚持定投可摊低成本")
        }

        val signal = when {
            profitRate != null && profitRate >= s.targetProfit -> Signal.EXIT
            score >= 3 -> Signal.ACCUMULATE
            score >= 1 -> Signal.NORMAL
            score >= -1 -> Signal.CAUTION
            else -> Signal.EXIT
        }
        return Verdict(signal, score, reasons)
    }
}
