package com.indexdca.app

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** 数据源：天天基金（搜索/历史净值）+ 蛋卷基金（指数估值）+ OpenAI 兼容 AI 接口 */
object FundApi {

    private const val UA =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
    private const val SEARCH_TOKEN = "D43BF722C8E33BDC906FB84D85E326E8"
    private val gson = Gson()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private fun get(url: String, referer: String? = null): Response {
        val b = Request.Builder().url(url).header("User-Agent", UA)
        referer?.let { b.header("Referer", it) }
        return client.newCall(b.build()).execute()
    }

    private fun JsonObject.str(k: String): String? =
        get(k)?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asString

    private fun JsonObject.dbl(k: String): Double? =
        get(k)?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asString?.toDoubleOrNull()

    // ---------------------------------------------------------------- 搜索

    suspend fun searchFunds(key: String): List<FundSearchItem> = withContext(Dispatchers.IO) {
        val url = "https://searchapi.eastmoney.com/api/suggest/get?input=" +
                URLEncoder.encode(key, "UTF-8") +
                "&type=14&count=20&token=$SEARCH_TOKEN"
        runCatching {
            get(url).use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val root = JsonParser.parseString(resp.body?.string() ?: "").asJsonObject
                val table = root.getAsJsonObject("QuotationCodeTable") ?: return@use emptyList()
                val arr = table.getAsJsonArray("Data") ?: return@use emptyList()
                arr.mapNotNull { el ->
                    val o = el.asJsonObject
                    val type = o.str("SecurityTypeName") ?: ""
                    val code = o.str("Code") ?: ""
                    if (type == "基金" && Regex("^\\d{6}$").matches(code)) {
                        FundSearchItem(code, o.str("Name") ?: code)
                    } else null
                }
            }
        }.getOrDefault(emptyList())
    }

    // ------------------------------------------------------ 历史净值与走势

    suspend fun fetchAnalysis(code: String, context: Context, force: Boolean = false): FundAnalysis? =
        withContext(Dispatchers.IO) {
            runCatching {
                val cache = File(context.cacheDir, "pz_$code.js")
                val body: String = if (!force && cache.exists() &&
                    System.currentTimeMillis() - cache.lastModified() < 3600_000L
                ) {
                    cache.readText()
                } else {
                    val b = get(
                        "https://fund.eastmoney.com/pingzhongdata/$code.js",
                        referer = "https://fund.eastmoney.com/"
                    ).use { resp ->
                        if (resp.isSuccessful) resp.body?.string().orEmpty() else ""
                    }
                    if (b.isNotBlank()) runCatching { cache.writeText(b) }
                    b
                }
                if (body.isBlank()) null else parsePingzhong(code, body)
            }.getOrNull()
        }

    fun parsePingzhong(code: String, body: String): FundAnalysis? {
        val name = Regex("fS_name\\s*=\\s*\"([^\"]*)\"").find(body)?.groupValues?.get(1) ?: code
        val trendJson = Regex("Data_netWorthTrend\\s*=\\s*(\\[.*?\\])\\s*;", RegexOption.DOT_MATCHES_ALL)
            .find(body)?.groupValues?.get(1) ?: return null
        val points: List<NavPoint> = runCatching {
            gson.fromJson<List<NavPoint>>(trendJson, object : TypeToken<List<NavPoint>>() {}.type)
        }.getOrDefault(emptyList())
        if (points.size < 10) return null

        val last = points.last()
        fun chg(days: Long): Double? {
            val cut = last.x - days * 86_400_000L
            val base = points.lastOrNull { it.x <= cut } ?: points.firstOrNull() ?: return null
            if (base.y <= 0.0) return null
            return (last.y / base.y - 1) * 100
        }

        val yearAgo = last.x - 365L * 86_400_000L
        val peak = points.filter { it.x >= yearAgo }.maxOfOrNull { it.y }
        val drawdown = peak?.takeIf { it > 0 }?.let { (last.y / it - 1) * 100 }

        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return FundAnalysis(
            code = code,
            name = name.ifBlank { code },
            points = points.takeLast(365),
            latestNav = last.y,
            latestDate = fmt.format(Date(last.x)),
            chg60d = chg(60),
            chg180d = chg(180),
            chg365d = chg(365),
            drawdownFromPeak = drawdown,
            peak1y = peak
        )
    }

    // ------------------------------------------------------------ 指数估值

    suspend fun fetchIndexValuations(): List<IndexValuation> = withContext(Dispatchers.IO) {
        runCatching {
            get("https://danjuanfunds.com/djapi/index_eva/dj", referer = "https://danjuanfunds.com/").use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val root = JsonParser.parseString(resp.body?.string() ?: "").asJsonObject
                val items = root.getAsJsonObject("data")?.getAsJsonArray("items")
                    ?: return@use emptyList()
                items.mapNotNull { el ->
                    val o = el.asJsonObject
                    val code = o.str("index_code") ?: return@mapNotNull null
                    val name = o.str("name") ?: return@mapNotNull null
                    val pe = o.dbl("pe")
                    val pb = o.dbl("pb")
                    fun pct(v: Double?): Double? = v?.let { (it * 100).coerceIn(0.0, 100.0) }
                    val pePct = if (pe != null && pe > 0) pct(o.dbl("pe_percentile")) else null
                    val pbPct = if (pb != null && pb > 0) pct(o.dbl("pb_percentile")) else null
                    IndexValuation(
                        indexCode = code,
                        indexName = name,
                        pe = pe?.takeIf { it > 0 },
                        pb = pb?.takeIf { it > 0 },
                        pePercentile = pePct,
                        pbPercentile = pbPct,
                        evaType = o.str("eva_type"),
                        date = o.str("date")
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    // ---------------------------------------------------------------- AI

    /**
     * 调用 OpenAI 兼容的 chat/completions 接口。
     * baseUrl 支持写法：https://api.deepseek.com 、 https://api.openai.com/v1 、
     * https://open.bigmodel.cn/api/paas/v4 等，自动补全路径。
     */
    suspend fun aiChat(baseUrl: String, apiKey: String, model: String, system: String, user: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val base = baseUrl.trim().trimEnd('/')
                val url = when {
                    base.endsWith("/chat/completions") -> base
                    base.matches(Regex(".*?/v\\d+$")) -> "$base/chat/completions"
                    else -> "$base/v1/chat/completions"
                }
                val payload = JsonObject().apply {
                    addProperty("model", model)
                    addProperty("temperature", 0.4)
                    add("messages", JsonArray().apply {
                        add(JsonObject().apply {
                            addProperty("role", "system")
                            addProperty("content", system)
                        })
                        add(JsonObject().apply {
                            addProperty("role", "user")
                            addProperty("content", user)
                        })
                    })
                }
                val req = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $apiKey")
                    .header("User-Agent", UA)
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(req).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        throw IllegalStateException("HTTP ${resp.code}: ${text.take(300)}")
                    }
                    val root = JsonParser.parseString(text).asJsonObject
                    val content = root.getAsJsonArray("choices")
                        ?.firstOrNull()?.asJsonObject
                        ?.getAsJsonObject("message")?.str("content")
                    content ?: throw IllegalStateException("AI 返回格式异常: ${text.take(300)}")
                }
            }
        }
}
