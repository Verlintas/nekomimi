/*
 * 猫猫助手 (Nekomimi) — Android accessibility text-rewriting assistant
 * Copyright (C) 2026 Verlintas
 * SPDX-License-Identifier: AGPL-3.0-only
 */

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.nekomimi.assistant.engine.Config
import com.nekomimi.assistant.engine.TextProcessor
import com.nekomimi.assistant.ui.AppState
import com.nekomimi.assistant.ui.theme.NekoCardColors

@Composable
fun RulesScreen(appState: AppState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // keyed by config：切换 Profile 后规则编辑框立即跟随新配置
    var rulesText by remember(appState.config) { mutableStateOf(Config.rulesToText(appState.config.rules)) }
    var sample by remember { mutableStateOf("今天我很好，你准备好了吗？") }
    var saved by remember { mutableStateOf(false) }

    val parsedRules = remember(rulesText) { Config.parseRulesText(rulesText) }
    val invalidCount = remember(parsedRules) {
        parsedRules.count { TextProcessor.validateRule(it) != null }
    }
    val previewCfg = remember(parsedRules) {
        appState.config.copy(rules = parsedRules)
    }
    val preview = remember(sample, previewCfg) {
        TextProcessor.process(sample, previewCfg) ?: sample
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("替换规则", style = MaterialTheme.typography.headlineMedium)

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = NekoCardColors.BlueCardLight)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("规则语法", style = MaterialTheme.typography.titleSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Text(
                    "· 字面替换：原词=替换词（也支持全角 ＝ / →）\n" +
                        "· 正则替换：re:正则=替换词（支持 \$1 捕获组）\n" +
                        "· 随机替换：原词=选项1|选项2|选项3\n" +
                        "· 动态占位符：{time} {date} {week} {random} {random:1-6}\n" +
                        "· 注释：# 开头或空行\n" +
                        "· 按顺序应用，每行一条",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        OutlinedTextField(
            value = rulesText,
            onValueChange = { rulesText = it; saved = false },
            modifier = Modifier.fillMaxWidth(),
            minLines = 8,
            placeholder = { Text("例如：\n我=本喵\n你=主人|殿下\nre:(\\d{11})=已打码\$1") },
            supportingText = {
                Text(
                    if (invalidCount > 0) "$invalidCount 条规则存在问题（无效正则/空词）"
                    else "已解析 ${parsedRules.size} 条规则",
                    color = if (invalidCount > 0) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )

        // ===== 实时预览 =====
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = NekoCardColors.BlueCardLight)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("实时预览", style = MaterialTheme.typography.titleSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                OutlinedTextField(
                    value = sample,
                    onValueChange = { sample = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("输入原文") },
                )
                Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
                    Text(
                        preview,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    appState.save(appState.config.copy(rules = parsedRules))
                    saved = true
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("保存规则")
            }
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = {
                    rulesText = ""
                    saved = false
                    Toast.makeText(context, "已清空（保存后生效）", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaultsOutlined(),
            ) {
                Text("清空")
            }
        }
        if (saved) {
            Text("已保存", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
        }
        HorizontalDivider()
        Text(
            "断句追加与颜文字在「设置」页配置",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ButtonDefaultsOutlined() = androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
