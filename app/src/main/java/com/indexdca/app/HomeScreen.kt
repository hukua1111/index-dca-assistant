package com.indexdca.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    onAi: () -> Unit,
    onSettings: () -> Unit
) {
    val holdings by AppStore.holdings.collectAsState()
    val indexes by AppStore.indexCache.collectAsState()
    val settings by AppStore.settings.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var analysisMap by remember { mutableStateOf<Map<String, FundAnalysis>>(emptyMap()) }
    var loading by remember { mutableStateOf(false) }

    fun refresh(force: Boolean) {
        if (loading || holdings.isEmpty()) return
        loading = true
        scope.launch {
            val map = mutableMapOf<String, FundAnalysis>()
            for (h in holdings) {
                FundApi.fetchAnalysis(h.code, context, force)?.let { map[h.code] = it }
            }
            analysisMap = map
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        if (AppStore.indexCache.value.isEmpty()) {
            AppStore.setIndexes(FundApi.fetchIndexValuations())
        }
    }
    LaunchedEffect(holdings.size) {
        if (holdings.isNotEmpty() && analysisMap.isEmpty()) refresh(false)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("指数定投助手") },
                actions = {
                    TextButton(onClick = onAi) { Text("AI 分析") }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                    IconButton(onClick = { refresh(true) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = "添加基金")
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Text(
                "数据仅供参考，不构成投资建议",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            if (holdings.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("还没有定投记录", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "点击右下角 + 搜索并添加指数基金\n可自动关联跟踪指数做估值判断",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(holdings, key = { it.id }) { h ->
                        val a = analysisMap[h.code]
                        val idx = indexes.firstOrNull { it.indexCode == h.indexCode }
                        HoldingCard(h, a, idx, settings) { onOpen(h.id) }
                    }
                }
            }
        }
    }
}

@Composable
fun HoldingCard(
    h: Holding,
    a: FundAnalysis?,
    idx: IndexValuation?,
    s: Settings,
    onClick: () -> Unit
) {
    val profitRate = a?.latestNav
        ?.takeIf { h.costPrice > 0 && h.shares > 0 }
        ?.let { (it / h.costPrice - 1) * 100 }
    val verdict = RuleEngine.evaluate(
        pePercentile = idx?.pePercentile,
        pbPercentile = idx?.pbPercentile,
        chg60d = a?.chg60d,
        drawdownFromPeak = a?.drawdownFromPeak,
        profitRate = profitRate,
        s = s
    )
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    h.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                SignalBadge(verdict.signal)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    h.code,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    h.indexName ?: "未关联指数",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (a == null) "加载中…" else "净值 ${Fmt.p4(a.latestNav)}（${a.latestDate}）",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val pct = idx?.mainPercentile
            Text(
                if (pct != null) "${idx?.mainMetricLabel}百分位 ${Fmt.p1(pct)}%（${idx?.date}）"
                else "暂无指数估值数据",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (h.shares > 0 && profitRate != null) "收益 ${Fmt.signed1(profitRate)}%"
                    else "未填写持仓",
                    style = MaterialTheme.typography.titleSmall,
                    color = profitColor(profitRate)
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (h.shares > 0 && a != null) "市值 ${Fmt.p2(h.shares * a.latestNav)} 元" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
