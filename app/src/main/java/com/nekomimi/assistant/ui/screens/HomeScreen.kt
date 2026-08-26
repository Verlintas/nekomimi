package com.nekomimi.assistant.ui.screens

import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nekomimi.assistant.service.AccessibilityUtil
import com.nekomimi.assistant.service.BatteryGuard
import com.nekomimi.assistant.ui.AppState
import com.nekomimi.assistant.ui.theme.NekoCardColors

@Composable
fun HomeScreen(appState: AppState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var refreshTick by remember { mutableStateOf(0) }
    // 依赖 version：从系统设置开启/关闭无障碍后回到应用，状态立即刷新
    val enabled = remember(refreshTick, appState.version) { AccessibilityUtil.isEnabled(context) }
    val batteryOk = remember(refreshTick, appState.version) { BatteryGuard.isIgnoringBatteryOptimizations(context) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("猫猫助手", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "聊天输入框自动改写 · 任意聊天软件通用",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // ===== 服务状态 =====
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (enabled) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.errorContainer,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.PowerSettingsNew,
                        contentDescription = null,
                        tint = if (enabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (enabled) "无障碍服务已开启" else "无障碍服务未开启",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (enabled) "文本改写正在运行，掉线时会收到通知提醒。"
                    else "开启后即可在聊天软件输入框内自动改写文本。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Row {
                    Button(onClick = { AccessibilityUtil.openSettings(context) }) {
                        Text(if (enabled) "打开无障碍设置" else "前往开启无障碍服务")
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { refreshTick++ }) {
                        Text("刷新")
                    }
                }
            }
        }

        // ===== 配置方案快速切换（蓝色卡片背景） =====
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isSystemInDarkTheme()) NekoCardColors.BlueCardDark else NekoCardColors.BlueCardLight,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "配置方案：${appState.activeProfile}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (name in appState.profiles) {
                        FilterChip(
                            selected = name == appState.activeProfile,
                            onClick = {
                                if (name != appState.activeProfile) {
                                    appState.switchProfile(name)
                                    Toast.makeText(context, "已切换到配置「$name」", Toast.LENGTH_SHORT).show()
                                }
                            },
                            label = { Text(name) },
                        )
                    }
                }
                Text(
                    "切换立即生效；通知栏常驻通知也可一键轮换。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ===== 暂停开关 =====
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("暂停改写", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "暂停期间不处理任何输入（通知栏也可一键切换）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = !appState.paused,
                    onCheckedChange = {
                        appState.updatePaused(!it)
                        Toast.makeText(context, if (it) "已暂停改写" else "已恢复改写", Toast.LENGTH_SHORT).show()
                    },
                )
            }
        }

        // ===== 电池白名单 =====
        if (!batteryOk) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Filled.BatteryAlert, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "建议加入电池优化白名单",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "当前系统可能杀死后台服务导致掉线。加入白名单可大幅提升稳定性。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = {
                        AccessibilityUtil.openBatteryRequest(context)
                        refreshTick++
                    }) {
                        Text("加入电池白名单")
                    }
                }
            }
        }

        Text(
            "提示：设置改动后点击各页的「保存」生效；服务每 5 秒自动重载配置。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
