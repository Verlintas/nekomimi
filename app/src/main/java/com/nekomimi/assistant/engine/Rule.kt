/*
 * 猫猫助手 (Nekomimi) — Android accessibility text-rewriting assistant
 * Copyright (C) 2026 Verlintas
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.nekomimi.assistant.engine

import kotlinx.serialization.Serializable

/** 规则匹配模式：LITERAL 字面替换 / REGEX 正则替换 */
@Serializable
enum class RuleMode {
    LITERAL,
    REGEX,
}

/**
 * 一条替换规则。
 * - mode LITERAL：from 按字面匹配
 * - mode REGEX：from 为正则表达式
 * - candidates 有多个时每次匹配随机选一个（随机替换）
 */
@Serializable
data class Rule(
    val mode: RuleMode = RuleMode.LITERAL,
    val from: String = "",
    val candidates: List<String> = emptyList(),
) {
    val display: String
        get() = (if (mode == RuleMode.REGEX) REGEX_PREFIX else "") + from + "=" + candidates.joinToString("|")

    companion object {
        const val REGEX_PREFIX = "re:"
        const val SEPARATORS = "=＝→"

        /**
         * 解析一行规则文本，非法/注释行返回 null。
         * 支持：原词=替换词、全角 ＝、→、随机候选 | 分隔、re: 正则前缀、# 注释。
         */
        fun parse(line: String): Rule? {
            val s = line.trim()
            if (s.isEmpty() || s.startsWith("#") || s.startsWith("//")) {
                return null
            }
            val idx = indexOfSeparator(s) ?: return null
            var from = s.substring(0, idx).trim()
            val to = s.substring(idx + 1).trim()
            if (from.isEmpty() || to.isEmpty()) {
                return null
            }
            var mode = RuleMode.LITERAL
            if (from.startsWith(REGEX_PREFIX)) {
                mode = RuleMode.REGEX
                from = from.removePrefix(REGEX_PREFIX).trim()
                if (from.isEmpty()) {
                    return null
                }
            }
            val candidates = to.split("|").map { it.trim() }.filter { it.isNotEmpty() }
            if (candidates.isEmpty()) {
                return null
            }
            return Rule(mode, from, candidates)
        }

        private fun indexOfSeparator(s: String): Int? {
            var idx = -1
            for (c in SEPARATORS) {
                val p = s.indexOf(c)
                if (p >= 0 && (idx < 0 || p < idx)) {
                    idx = p
                }
            }
            return if (idx <= 0) null else idx
        }
    }
}
