package com.indexdca.app

/** 通过基金名称与指数名称的 2-gram 相似度，自动猜测基金跟踪的指数 */
object IndexMatcher {

    fun bestMatch(fundName: String, indexes: List<IndexValuation>): IndexValuation? {
        if (indexes.isEmpty()) return null
        val a = bigrams(clean(fundName))
        if (a.isEmpty()) return null
        var best: IndexValuation? = null
        var bestScore = 0.0
        for (iv in indexes) {
            val b = bigrams(clean(iv.indexName))
            if (b.isEmpty()) continue
            val score = a.intersect(b).size.toDouble() / minOf(a.size, b.size)
            if (score > bestScore) {
                bestScore = score
                best = iv
            }
        }
        return best?.takeIf { bestScore >= 0.3 }
    }

    private fun clean(s: String): String =
        s.replace(Regex("[（(].*?[）)]"), "")
            .replace(Regex("指数|基金|LOF|ETF|增强|联接|量化|价格|人民币|QDII"), "")
            .trim()

    private fun bigrams(s: String): Set<String> =
        if (s.length >= 2) s.windowed(2).toSet() else emptySet()
}
