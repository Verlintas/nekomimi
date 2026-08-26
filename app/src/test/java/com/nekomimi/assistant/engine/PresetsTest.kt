/*
 * 猫猫助手 (Nekomimi) — Android accessibility text-rewriting assistant
 * Copyright (C) 2026 Verlintas
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.nekomimi.assistant.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetsTest {

    @Test
    fun `all preset packs parse to valid rules`() {
        for (pack in Presets.PACKS) {
            val rules = Config.parseRulesText(pack.rulesText)
            assertTrue("${pack.name} 应至少有 5 条规则", rules.size >= 5)
            for (rule in rules) {
                assertEquals(null, TextProcessor.validateRule(rule))
            }
        }
    }

    @Test
    fun `meng pack replaces pronouns`() {
        val cfg = Config(rules = Presets.asReplaceRules(Presets.MENG_RULES), enableRandomEmoticon = false, enableAppend = false)
        assertEquals("本喵想找主人", TextProcessor.process("我想找你", cfg))
    }

    @Test
    fun `clean pack neutralizes profanity`() {
        val cfg = Config(rules = Presets.asReplaceRules(Presets.CLEAN_RULES), enableRandomEmoticon = false, enableAppend = false)
        val out = TextProcessor.process("卧槽你妈的", cfg)!!
        assertTrue(!out.contains("槽") && !out.contains("妈") && out.contains("喵"))
    }

    @Test
    fun `tsundere pack works end to end`() {
        val cfg = Config(rules = Presets.asReplaceRules(Presets.TSUNDERE_RULES), enableRandomEmoticon = false, enableAppend = false)
        assertEquals("才、才不是想要", TextProcessor.process("才不要", cfg))
    }

    @Test
    fun `append mode keeps existing rules`() {
        val existing = listOf(Rule(from = "我", candidates = listOf("本喵")))
        val merged = Presets.asAppendRules(existing, Presets.ROAST_RULES)
        assertEquals("本喵", merged.first().candidates.first())
        assertTrue(merged.size > existing.size)
    }

    @Test
    fun `replace mode drops existing rules`() {
        val replaced = Presets.asReplaceRules(Presets.ROAST_RULES)
        assertTrue(replaced.none { it.from == "我" })
        assertTrue(replaced.any { it.from == "好的" })
    }
}
