package com.indexdca.app

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.selection.SelectionContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val AI_SYSTEM_PROMPT =
    "你是一名专业、稳健的基金投资顾问，擅长指数基金定投与止盈策略（估值定投法）。" +
    "请基于用户提供的基金估值百分位、历史走势、持仓成本数据，给出具体、可执行、有理有据的建议。" +
    "结构化输出：一、定投建议（继续/放缓/暂停/加倍，说明理由）；二、卖出建议（继续持有/部分止盈/清仓，说明理由）；" +
    "三、主要风险提示；四、一句话结论。语言简洁，直接给结论。最后必须注明：以上分析仅供参考，不构成投资建议。"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiScreen(holdingId: String?, onBack: () -> Unit, onSettings: () -> Unit) {
    val settings by AppStore.settings.collectAsState()
    val holdings by AppStore.holdings.collectAsState()
    val indexes by AppStore.indexCache.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var contextText by remember { mutableStateOf("") }
    var building by remember { mutableStateOf(true) }
    var result by remember { mutableStateOf("") }
    var calling by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showResultDialog by remember { mutableStateOf(false) }

    LaunchedEffect(holdingId) {
        if (AppStore.indexCache.value.isEmpty()) {
            AppStore.setIndexes(FundApi.fetchIndexValuations())
        }
        building = true
        contextText = AiContext.build(
            holdingId, AppStore.holdings.value, AppStore.indexCache.value, settings, context
        )
        building = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 投资助手") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = onSettings) { Text("API 设置") }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (settings.aiApiKey.isBlank()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("尚未配置 AI 接口", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "本应用兼容 OpenAI 格式接口，支持 DeepSeek / Kimi / 智谱 / 通义千问 / OpenAI 等。请先在 API 设置中填入地址与密钥。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(onClick = onSettings) { Text("去设置") }
                    }
                }
            }

            Text(
                "基金现状与持仓上下文（发送前可编辑）",
                style = MaterialTheme.typography.labelLarge
            )
            OutlinedTextField(
                value = contextText,
                onValueChange = { contextText = it },
                modifier = Modifier.fillMaxWidth().weight(1f),
                textStyle = MaterialTheme.typography.bodySmall,
                placeholder = { Text(if (building) "正在生成上下文…" else "") }
            )
            if (calling) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            Button(
                onClick = {
                    calling = true
                    error = null
                    scope.launch {
                        FundApi.aiChat(
                            settings.aiBaseUrl, settings.aiApiKey, settings.aiModel,
                            AI_SYSTEM_PROMPT, contextText
                        ).onSuccess {
                            result = it
                        }.onFailure {
                            error = it.message ?: "调用失败"
                        }
                        calling = false
                    }
                },
                enabled = !calling && settings.aiApiKey.isNotBlank() && contextText.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (calling) "AI 思考中…" else "发送给 AI 分析")
            }
            error?.let {
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        "调用失败：$it",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
            if (result.isNotBlank()) {
                Button(
                    onClick = { showResultDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("查看 AI 建议") }
            }
        }
    }

    if (showResultDialog) {
        AlertDialog(
            onDismissRequest = { showResultDialog = false },
            title = { Text("AI 建议", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                SelectionContainer {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text(result, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showResultDialog = false }) { Text("关闭") }
            }
        )
    }
}

/** 组装发给 AI 的上下文：基金现状 + 全部持仓概览 */
object AiContext {

    suspend fun build(
        holdingId: String?,
        holdings: List<Holding>,
        indexes: List<IndexValuation>,
        settings: Settings,
        context: Context
    ): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        val focus = holdingId?.let { id -> holdings.firstOrNull { it.id == id } }

        if (focus != null) {
            sb.appendLine("【关注基金】${focus.name}（${focus.code}）")
            val a = FundApi.fetchAnalysis(focus.code, context)
            val idx = indexes.firstOrNull { it.indexCode == focus.indexCode }
            if (a != null) {
                sb.appendLine("最新净值：${Fmt.p4(a.latestNav)}（${a.latestDate}）")
                sb.appendLine("近60日涨跌：${Fmt.signed1(a.chg60d)}%；近180日：${Fmt.signed1(a.chg180d)}%；近365日：${Fmt.signed1(a.chg365d)}%")
                a.drawdownFromPeak?.let { sb.appendLine("距近1年高点回撤：${Fmt.p1(it)}%") }
            }
            if (idx != null) {
                sb.appendLine("跟踪指数：${idx.indexName}")
                val parts = mutableListOf<String>()
                idx.pe?.let { parts.add("PE=${Fmt.p2(it)}（历史百分位 ${Fmt.p1(idx.pePercentile)}%）") }
                idx.pb?.let { parts.add("PB=${Fmt.p2(it)}（历史百分位 ${Fmt.p1(idx.pbPercentile)}%）") }
                if (parts.isNotEmpty()) sb.appendLine(parts.joinToString("  "))
                idx.date?.let { sb.appendLine("估值数据日期：${idx.date}") }
            }
            if (focus.shares > 0 && focus.costPrice > 0) {
                a?.latestNav?.let {
                    sb.appendLine(
                        "我的持仓：份额 ${Fmt.p2(focus.shares)} 份，平均成本 ${Fmt.p4(focus.costPrice)} 元，" +
                        "市值 ${Fmt.p2(focus.shares * it)} 元，浮动收益率 ${Fmt.p1((it / focus.costPrice - 1) * 100)}%"
                    )
                }
            } else {
                sb.appendLine("我的持仓：未填写份额与成本")
            }
            sb.appendLine("我的止盈目标线：${Fmt.p1(settings.targetProfit)}%")
            sb.appendLine()
        }

        if (holdings.isNotEmpty()) {
            sb.appendLine("【当前全部持仓概览】")
            for (h in holdings) {
                val idx = indexes.firstOrNull { it.indexCode == h.indexCode }
                val pct = idx?.mainPercentile?.let { "${Fmt.p1(it)}%" } ?: "无估值数据"
                sb.appendLine("- ${h.name}（${h.code}），关联指数：${h.indexName ?: "无"}，指数估值百分位：$pct")
            }
            sb.appendLine()
        }

        sb.appendLine("【我的定投策略偏好】低估值定投、高估值止盈；低估线 ${Fmt.p1(settings.lowThreshold)}%，高估线 ${Fmt.p1(settings.highThreshold)}%，极端线 ${Fmt.p1(settings.extremeThreshold)}%，止盈线 ${Fmt.p1(settings.targetProfit)}%")
        sb.appendLine()
        if (focus != null) {
            sb.appendLine("请结合以上数据，给出针对「${focus.name}」的定投与卖出建议。")
        } else {
            sb.appendLine("请结合以上持仓概览，给出整体定投与止盈建议。")
        }
        sb.toString()
    }
}
