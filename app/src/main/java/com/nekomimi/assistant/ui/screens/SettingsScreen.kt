package com.nekomimi.assistant.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nekomimi.assistant.engine.Config
import com.nekomimi.assistant.ui.AppState

/** 常用聊天软件预置包名 */
private val CHAT_PRESETS = listOf(
    "QQ" to "com.tencent.mobileqq",
    "微信" to "com.tencent.mm",
    "Telegram" to "org.telegram.messenger",
    "WhatsApp" to "com.whatsapp",
    "钉钉" to "com.alibaba.android.rimet",
    "Discord" to "com.discord",
    "飞书" to "com.ss.android.lark",
    "Slack" to "com.slack",
    "LINE" to "jp.naver.line.android",
    "Signal" to "org.thoughtcrime.securesms",
)

@Composable
fun SettingsScreen(appState: AppState, modifier: Modifier = Modifier) {
    val cfg = appState.config
    var modeRealtime by remember { mutableStateOf(cfg.processingMode == Config.MODE_REALTIME) }
    var enableAppend by remember { mutableStateOf(cfg.enableAppend) }
    var appendText by remember { mutableStateOf(cfg.appendText) }
    var enableEmoticon by remember { mutableStateOf(cfg.enableRandomEmoticon) }
    var customEmoticons by remember { mutableStateOf(cfg.customEmoticons.joinToString("\n")) }
    var enableSendFallback by remember { mutableStateOf(cfg.enableSendFallback) }
    var onlyFocused by remember { mutableStateOf(cfg.onlyProcessFocused) }
    var stableDelay by remember { mutableStateOf(cfg.stableDelayMs.toString()) }
    var targets by remember { mutableStateOf(cfg.targetPackages.joinToString("\n")) }
    var excludes by remember { mutableStateOf(cfg.excludePackages.joinToString("\n")) }
    var saved by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("设置", style = MaterialTheme.typography.headlineMedium)

        // ===== 处理模式 =====
        SectionCard("处理模式") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !modeRealtime,
                    onClick = { modeRealtime = false },
                    label = { Text("标点触发（推荐）") },
                )
                FilterChip(
                    selected = modeRealtime,
                    onClick = { modeRealtime = true },
                    label = { Text("实时处理") },
                )
            }
            Text(
                "标点触发：打字时在句末标点处处理；实时处理：输入停止后立即处理（可配合下方防抖）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ===== 断句追加 =====
        SectionCard("断句追加") {
            SwitchRow(
                title = "启用断句追加",
                desc = "在每个分句的句末追加自定义文本",
                checked = enableAppend,
                onChecked = { enableAppend = it },
            )
            OutlinedTextField(
                value = appendText,
                onValueChange = { appendText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("追加内容（默认：喵）") },
                enabled = enableAppend,
            )
        }

        // ===== 句末颜文字 =====
        SectionCard("句末颜文字") {
            SwitchRow(
                title = "启用随机颜文字",
                desc = "在消息末尾附加随机颜文字；实时模式下点击发送时补上",
                checked = enableEmoticon,
                onChecked = { enableEmoticon = it },
            )
            OutlinedTextField(
                value = customEmoticons,
                onValueChange = { customEmoticons = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                label = { Text("自定义颜文字（每行一个，留空 = 内置 50+ 个）") },
                enabled = enableEmoticon,
            )
        }

        // ===== 高级 =====
        SectionCard("高级") {
            SwitchRow(
                title = "仅处理聚焦的输入框",
                desc = "防止误识别提示词/后台输入框（推荐开启）",
                checked = onlyFocused,
                onChecked = { onlyFocused = it },
            )
            SwitchRow(
                title = "发送按钮兜底",
                desc = "点击发送按钮时做最后一次处理",
                checked = enableSendFallback,
                onChecked = { enableSendFallback = it },
            )
            OutlinedTextField(
                value = stableDelay,
                onValueChange = { stableDelay = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("流式输入防抖（毫秒）") },
                supportingText = {
                    Text("语音输入等流式识别时等待输入停止 N 毫秒再改写。默认 800，填 0 关闭。")
                },
            )
        }

        // ===== 作用范围 =====
        SectionCard("作用范围") {
            Text(
                "目标应用（每行一个包名；留空 = 所有应用）",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            OutlinedTextField(
                value = targets,
                onValueChange = { targets = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                placeholder = { Text("例如：com.tencent.mobileqq") },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 预置包名 chips（横向滚动由父级 ScrollView 承载，仅展示前几个）
                for ((label, pkg) in CHAT_PRESETS.take(5)) {
                    FilterChip(
                        selected = targets.split("\n").any { it.trim() == pkg },
                        onClick = {
                            val list = targets.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
                            if (pkg in list) list.remove(pkg) else list.add(pkg)
                            targets = list.joinToString("\n")
                        },
                        label = { Text(label) },
                    )
                }
            }
            Text(
                "排除应用（每行一个包名；输入法/桌面/系统设置已默认排除）",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            OutlinedTextField(
                value = excludes,
                onValueChange = { excludes = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { excludes = "" }) {
                    Text("清空排除")
                }
                Button(onClick = { excludes = Config.DEFAULT_EXCLUDE_PACKAGES.joinToString("\n") }) {
                    Text("恢复默认")
                }
            }
        }

        Button(
            onClick = {
                val delay = stableDelay.toIntOrNull()?.coerceAtLeast(0) ?: 800
                appState.save(
                    appState.config.copy(
                        processingMode = if (modeRealtime) Config.MODE_REALTIME else Config.MODE_PUNCTUATION,
                        enableAppend = enableAppend,
                        appendText = appendText.ifBlank { "喵" },
                        enableRandomEmoticon = enableEmoticon,
                        customEmoticons = customEmoticons.lines().map { it.trim() }.filter { it.isNotEmpty() },
                        enableSendFallback = enableSendFallback,
                        onlyProcessFocused = onlyFocused,
                        stableDelayMs = delay,
                        targetPackages = splitLines(targets),
                        excludePackages = splitLines(excludes),
                    ),
                )
                saved = true
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("保存设置")
        }
        if (saved) {
            Text("已保存", color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun SwitchRow(title: String, desc: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

private fun splitLines(s: String): List<String> =
    s.lines().map { it.trim() }.filter { it.isNotEmpty() }
