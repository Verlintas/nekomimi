/*
 * 猫猫助手 (Nekomimi) — Android accessibility text-rewriting assistant
 * Copyright (C) 2026 Verlintas
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.nekomimi.assistant.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleParsingTest {

    @Test
    fun `literal rule with halfwidth separator`() {
        val r = Rule.parse("我=本喵")
        assertEquals(RuleMode.LITERAL, r?.mode)
        assertEquals("我", r?.from)
        assertEquals(listOf("本喵"), r?.candidates)
    }

    @Test
    fun `literal rule with fullwidth separator`() {
        val r = Rule.parse("我＝本喵")
        assertEquals("我", r?.from)
        assertEquals(listOf("本喵"), r?.candidates)
    }

    @Test
    fun `literal rule with arrow separator`() {
        val r = Rule.parse("我→本喵")
        assertEquals("我", r?.from)
        assertEquals(listOf("本喵"), r?.candidates)
    }

    @Test
    fun `random rule with multiple candidates`() {
        val r = Rule.parse("你=主人|殿下|亲亲")
        assertEquals("你", r?.from)
        assertEquals(listOf("主人", "殿下", "亲亲"), r?.candidates)
    }

    @Test
    fun `regex rule with re prefix`() {
        val r = Rule.parse("re:(\\d{11})=已打码$1")
        assertEquals(RuleMode.REGEX, r?.mode)
        assertEquals("(\\d{11})", r?.from)
        assertEquals(listOf("已打码$1"), r?.candidates)
    }

    @Test
    fun `regex rule with random candidates`() {
        val r = Rule.parse("re:狗=汪|汪汪")
        assertEquals(RuleMode.REGEX, r?.mode)
        assertEquals(listOf("汪", "汪汪"), r?.candidates)
    }

    @Test
    fun `comment and blank lines are ignored`() {
        assertNull(Rule.parse("# 注释"))
        assertNull(Rule.parse("// 注释"))
        assertNull(Rule.parse("   "))
        assertNull(Rule.parse(""))
    }

    @Test
    fun `line without separator is invalid`() {
        assertNull(Rule.parse("hello"))
    }

    @Test
    fun `line with empty from or to is invalid`() {
        assertNull(Rule.parse("=喵"))
        assertNull(Rule.parse("我="))
        assertNull(Rule.parse("re:=x"))
    }

    @Test
    fun `line with empty candidates only pipes is invalid`() {
        assertNull(Rule.parse("你=  |  "))
    }

    @Test
    fun `display roundtrip preserves syntax`() {
        val text = listOf(
            "我=本喵",
            "re:(\\d{11})=已打码$1",
            "你=主人|殿下|亲亲",
            "# 注释",
            "",
            "坏行没有分隔符",
        ).joinToString("\n")
        val rules = Config.parseRulesText(text)
        assertEquals(3, rules.size)
        assertEquals(text.split("\n").take(3), Config.rulesToText(rules).split("\n"))
        assertTrue(Config.rulesToText(rules).contains("re:(\\d{11})=已打码$1"))
    }

    @Test
    fun `regex with spaces around prefix`() {
        val r = Rule.parse("  re:\\d+  =  <数字>  ")
        assertEquals(RuleMode.REGEX, r?.mode)
        assertEquals("\\d+", r?.from)
        assertEquals(listOf("<数字>"), r?.candidates)
    }
}
