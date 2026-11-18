package com.indexdca.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(holdingId: String, onBack: () -> Unit, onAi: () -> Unit) {
    val holdings by AppStore.holdings.collectAsState()
    val settings by AppStore.settings.collectAsState()
    val indexes by AppStore.indexCache.collectAsState()
    val context = LocalContext.current
    val holding = holdings.firstOrNull { it.id == holdingId }

    var analysis by remember { mutableStateOf<FundAnalysis?>(null) }
    var loading by remember { mutableStateOf(true) }
    var editDialog by remember { mutableStateOf(false) }
    var deleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(holding?.code) {
        if (AppStore.indexCache.value.isEmpty()) {
            AppStore.setIndexes(FundApi.fetchIndexValuations())
        }
        if (holding != null) {
            loading = true
            val a = FundApi.fetchAnalysis(holding.code, context)
            analysis = a
            loading = a == null
        }
    }

    if (holding == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("详情") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                )
            }
        ) { pad ->
            Text("该定投记录已被删除", Modifier.padding(pad).padding(24.dp))
        }
        return
    }

    val idx = indexes.firstOrNull { it.indexCode == holding.indexCode }
    val profitRate = analysis?.latestNav
        ?.takeIf { holding.costPrice > 0 && holding.shares > 0 }
        ?.let { (it / holding.costPrice - 1) * 100 }
    val verdict = RuleEngine.evaluate(
        pePercentile = idx?.pePercentile,
        pbPercentile = idx?.pbPercentile,
        chg60d = analysis?.chg60d,
        drawdownFromPeak = analysis?.drawdownFromPeak,
        profitRate = profitRate,
        s = settings
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(holding.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { editDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "编辑")
                    }
                    IconButton(onClick = { deleteConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "删除")
                    }
                    TextButton(onClick = onAi) { Text("AI") }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---------------- 定投判断 ----------------
            val vColor = signalColor(verdict.signal)
            Card(Modifier.fillMaxWidth()) {
                Surface(
                    color = vColor.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("定投 / 止盈判断", style = MaterialTheme.typography.labelLarge)
                        SignalBadge(verdict.signal)
                        if (verdict.reasons.isEmpty() && loading) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.height(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.height(8.dp))
                                Text("  正在加载基金数据…", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        verdict.reasons.forEach { r ->
                            Row {
                                Text("· ", color = vColor, style = MaterialTheme.typography.bodySmall)
                                Text(r, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Text(
                            "规则依据：估值百分位 + 走势 + 你的止盈线（可在设置中调整阈值）",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ---------------- 指数估值 ----------------
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("跟踪指数估值", style = MaterialTheme.typography.labelLarge)
                    if (idx == null) {
                        Text(
                            "未匹配到指数估值（可点击右上角编辑更换关联指数）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            idx.indexName + (idx.date?.let { "（${it}）" } ?: ""),
                            style = MaterialTheme.typography.titleSmall
                        )
                        PercentileBar("PE", idx.pe, idx.pePercentile)
                        PercentileBar("PB", idx.pb, idx.pbPercentile)
                        idx.evaType?.let {
                            Text(
                                "综合评估：${when (it) {
                                    "low" -> "低估"
                                    "high" -> "高估"
                                    else -> "适中"
                                }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // ---------------- 走势 ----------------
            val a = analysis
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("净值走势（近1年）", style = MaterialTheme.typography.labelLarge)
                    if (a != null) {
                        LineChart(
                            a.points,
                            Modifier.fillMaxWidth().height(160.dp),
                            color = ColorBlue
                        )
                        StatRow("最新净值", "${Fmt.p4(a.latestNav)}（${a.latestDate}）")
                        StatRow("近60日", Fmt.signed1(a.chg60d) + "%", profitColor(a.chg60d))
                        StatRow("近180日", Fmt.signed1(a.chg180d) + "%", profitColor(a.chg180d))
                        StatRow("近365日", Fmt.signed1(a.chg365d) + "%", profitColor(a.chg365d))
                        a.drawdownFromPeak?.let {
                            StatRow("距1年高点回撤", Fmt.p1(it) + "%", ColorOrange)
                        }
                    } else {
                        Text(
                            if (loading) "正在加载…" else "加载失败，请下拉重试",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ---------------- 持仓 ----------------
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("我的持仓", style = MaterialTheme.typography.labelLarge)
                    if (holding.shares > 0 && holding.costPrice > 0 && a != null) {
                        StatRow("持有份额", Fmt.p2(holding.shares) + " 份")
                        StatRow("平均成本", Fmt.p4(holding.costPrice) + " 元")
                        StatRow("当前市值", Fmt.p2(holding.shares * a.latestNav) + " 元")
                        StatRow("浮动收益", Fmt.signed1(profitRate) + "%", profitColor(profitRate))
                        StatRow("止盈目标线", Fmt.p1(settings.targetProfit) + "%")
                    } else {
                        Text(
                            "未填写份额与成本，点击右上角编辑完善后可计算收益",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Button(onClick = onAi, modifier = Modifier.fillMaxWidth()) {
                Text("让 AI 结合以上数据分析")
            }
            Text(
                "免责声明：本应用所有信号与 AI 建议均基于公开数据的规则计算，仅供参考，不构成任何投资建议。市场有风险，投资需谨慎。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (editDialog) {
        AddFundDialog(
            fundCode = holding.code,
            fundName = holding.name,
            existing = holding,
            indexes = indexes,
            onDismiss = { editDialog = false },
            onSaved = { editDialog = false }
        )
    }
    if (deleteConfirm) {
        AlertDialog(
            onDismissRequest = { deleteConfirm = false },
            title = { Text("删除定投记录") },
            text = { Text("确定删除「${holding.name}」吗？") },
            confirmButton = {
                TextButton(onClick = {
                    AppStore.removeHolding(holding.id)
                    deleteConfirm = false
                    onBack()
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirm = false }) { Text("取消") }
            }
        )
    }
}

@Composable
fun PercentileBar(label: String, value: Double?, pct: Double?) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$label ${Fmt.p2(value)}",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (pct != null) "历史百分位 ${Fmt.p1(pct)}%" else "百分位暂无",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (pct != null) {
            LinearProgressIndicator(
                progress = { (pct / 100).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp)
            )
        }
    }
}

@Composable
fun StatRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface
        )
    }
}
