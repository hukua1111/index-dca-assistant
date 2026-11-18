package com.indexdca.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<FundSearchItem>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val indexes by AppStore.indexCache.collectAsState()
    var addDialogFor by remember { mutableStateOf<FundSearchItem?>(null) }

    LaunchedEffect(Unit) {
        if (AppStore.indexCache.value.isEmpty()) {
            AppStore.setIndexes(FundApi.fetchIndexValuations())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("搜索基金") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("基金代码或名称") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        searching = true
                        error = null
                        scope.launch {
                            results = FundApi.searchFunds(query.trim())
                            searching = false
                            if (results.isEmpty()) error = "没有找到相关基金，请换个关键词"
                        }
                    },
                    enabled = query.isNotBlank() && !searching
                ) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(if (searching) "搜索中" else "搜索")
                }
            }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(results, key = { it.code }) { f ->
                    ListItem(
                        headlineContent = { Text(f.name) },
                        supportingContent = { Text(f.code) },
                        modifier = Modifier.clickable { addDialogFor = f }
                    )
                }
            }
        }
    }

    addDialogFor?.let { f ->
        AddFundDialog(
            fundCode = f.code,
            fundName = f.name,
            existing = null,
            indexes = indexes,
            onDismiss = { addDialogFor = null },
            onSaved = { addDialogFor = null }
        )
    }
}

@Composable
fun AddFundDialog(
    fundCode: String,
    fundName: String,
    existing: Holding?,
    indexes: List<IndexValuation>,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val suggested = remember(fundCode) { IndexMatcher.bestMatch(fundName, indexes) }
    var selectedCode by remember(fundCode) {
        mutableStateOf(existing?.indexCode ?: suggested?.indexCode)
    }
    var sharesText by remember(fundCode) {
        mutableStateOf(existing?.shares?.takeIf { it > 0 }?.let { Fmt.p2(it) } ?: "")
    }
    var costText by remember(fundCode) {
        mutableStateOf(existing?.costPrice?.takeIf { it > 0 }?.let { Fmt.p4(it) } ?: "")
    }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "添加定投基金" else "编辑持仓") },
        text = {
            Column {
                Text("$fundName（$fundCode）", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "关联跟踪指数（用于估值判断）",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                suggested?.let {
                    Text(
                        "系统推荐：${it.indexName}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                LazyColumn(Modifier.height(200.dp).padding(vertical = 4.dp)) {
                    items(indexes, key = { it.indexCode }) { iv ->
                        Row(
                            Modifier.fillMaxWidth().clickable { selectedCode = iv.indexCode },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedCode == iv.indexCode,
                                onClick = { selectedCode = iv.indexCode }
                            )
                            Column {
                                Text(iv.indexName, style = MaterialTheme.typography.bodySmall)
                                val pct = iv.mainPercentile
                                val metric = iv.mainMetricValue
                                Text(
                                    if (pct != null) "${iv.mainMetricLabel} ${Fmt.p2(metric)} · 百分位 ${Fmt.p1(pct)}%"
                                    else "暂无估值",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = sharesText,
                        onValueChange = { sharesText = it },
                        label = { Text("持有份额(份)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = costText,
                        onValueChange = { costText = it },
                        label = { Text("平均成本(元)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val shares = sharesText.trim().toDoubleOrNull() ?: 0.0
                val cost = costText.trim().toDoubleOrNull() ?: 0.0
                val idx = indexes.firstOrNull { it.indexCode == selectedCode }
                if (idx == null) {
                    error = "请选择关联指数"
                } else {
                    val h = existing?.copy()
                        ?: Holding(id = UUID.randomUUID().toString(), code = fundCode, name = fundName)
                    h.indexCode = idx.indexCode
                    h.indexName = idx.indexName
                    h.shares = shares
                    h.costPrice = cost
                    AppStore.upsertHolding(h)
                    onSaved()
                }
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
