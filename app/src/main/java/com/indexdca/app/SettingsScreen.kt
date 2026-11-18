package com.indexdca.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val s by AppStore.settings.collectAsState()

    var baseUrl by remember { mutableStateOf(s.aiBaseUrl) }
    var apiKey by remember { mutableStateOf(s.aiApiKey) }
    var model by remember { mutableStateOf(s.aiModel) }
    var low by remember { mutableStateOf(Fmt.p1(s.lowThreshold)) }
    var high by remember { mutableStateOf(Fmt.p1(s.highThreshold)) }
    var extreme by remember { mutableStateOf(Fmt.p1(s.extremeThreshold)) }
    var target by remember { mutableStateOf(Fmt.p1(s.targetProfit)) }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("AI 接口（OpenAI 兼容格式）", style = MaterialTheme.typography.labelLarge)
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("API 地址") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key") },
                singleLine = true,
                visualTransformation = if (apiKey.isBlank()) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("模型名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "常用配置示例：\n" +
                "· DeepSeek：https://api.deepseek.com  模型 deepseek-chat\n" +
                "· 智谱：https://open.bigmodel.cn/api/paas/v4  模型 glm-4-flash\n" +
                "· Kimi：https://api.moonshot.cn/v1  模型 moonshot-v1-8k\n" +
                "· 通义千问：https://dashscope.aliyuncs.com/compatible-mode/v1  模型 qwen-plus\n" +
                "· OpenAI：https://api.openai.com/v1  模型 gpt-4o-mini",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.padding(top = 4.dp))
            Text("定投规则阈值（%）", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = low,
                    onValueChange = { low = it },
                    label = { Text("低估线") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = high,
                    onValueChange = { high = it },
                    label = { Text("高估线") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = extreme,
                    onValueChange = { extreme = it },
                    label = { Text("极端线") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it },
                    label = { Text("止盈线") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                "低估：百分位低于此值→积极定投；高估：超过此值→放缓/暂停；极端：超过此值→高估警示；收益达到止盈线→提示止盈。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = {
                    val lowV = low.toDoubleOrNull()
                    val highV = high.toDoubleOrNull()
                    val extV = extreme.toDoubleOrNull()
                    val tgtV = target.toDoubleOrNull()
                    if (baseUrl.isBlank() || model.isBlank()) {
                        error = "API 地址与模型名称不能为空"
                        return@Button
                    }
                    if (lowV == null || highV == null || extV == null || tgtV == null ||
                        lowV <= 0 || highV <= lowV || extV < highV || tgtV <= 0
                    ) {
                        error = "阈值不合法：需满足 0 < 低估线 < 高估线 ≤ 极端线，止盈线 > 0"
                        return@Button
                    }
                    AppStore.saveSettings(
                        Settings(baseUrl.trim(), apiKey.trim(), model.trim(), lowV, highV, extV, tgtV)
                    )
                    onBack()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("保存设置") }
        }
    }
}
