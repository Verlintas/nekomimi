/*
 * 猫猫助手 (Nekomimi) — Android accessibility text-rewriting assistant
 * Copyright (C) 2026 Verlintas
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.nekomimi.assistant.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class PlaceholderExpanderTest {

    @Test
    fun `time expands to HH mm format`() {
        val out = PlaceholderExpander.expand("{time}")
        assertTrue("unexpected: $out", Regex("\\d{2}:\\d{2}").matches(out))
    }

    @Test
    fun `time with custom format`() {
        val out = PlaceholderExpander.expand("{time:HH时mm分}")
        assertTrue("unexpected: $out", Regex("\\d{2}时\\d{2}分").matches(out))
    }

    @Test
    fun `date expands to M月d日`() {
        val out = PlaceholderExpander.expand("{date}")
        assertTrue("unexpected: $out", Regex("\\d{1,2}月\\d{1,2}日").matches(out))
    }

    @Test
    fun `week expands to 星期X`() {
        val out = PlaceholderExpander.expand("{week}")
        assertTrue("unexpected: $out", Regex("星期[日一二三四五六]").matches(out))
    }

    @Test
    fun `week respects fixed timestamp`() {
        // 2026-08-25 是周二
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.AUGUST, 25, 12, 0, 0)
        }
        assertEquals("星期二", PlaceholderExpander.expand("{week}", cal.timeInMillis))
    }

    @Test
    fun `random expands to number in range`() {
        repeat(200) {
            val out = PlaceholderExpander.expand("{random}").toInt()
            assertTrue(out in 0..99)
        }
    }

    @Test
    fun `random with range stays within bounds`() {
        repeat(200) {
            val out = PlaceholderExpander.expand("{random:1-100}").toInt()
            assertTrue("out of range: $out", out in 1..100)
        }
    }

    @Test
    fun `random with reversed bounds`() {
        repeat(100) {
            val out = PlaceholderExpander.expand("{random:5-1}").toInt()
            assertTrue(out in 1..5)
        }
    }

    @Test
    fun `random is not constant`() {
        val values = (1..50).map { PlaceholderExpander.expand("{random}") }.toSet()
        assertTrue(values.size > 1)
    }

    @Test
    fun `unknown placeholder kept as is`() {
        assertEquals("你好{nothing}", PlaceholderExpander.expand("你好{nothing}"))
    }

    @Test
    fun `text without placeholders unchanged`() {
        assertEquals("你好喵", PlaceholderExpander.expand("你好喵"))
        assertEquals("", PlaceholderExpander.expand(""))
    }

    @Test
    fun `placeholder inside rule result expands`() {
        val cfg = Config(
            rules = listOf(Rule(from = "早安", candidates = listOf("{week}好，{time}了"))),
        )
        val out = TextProcessor.process("早安", cfg)!!
        assertTrue("unexpected: $out", out.startsWith("星期") || out.startsWith("星"))
        assertTrue(out.contains(":"))
    }

    @Test
    fun `random differs across process calls`() {
        val cfg = Config(rules = listOf(Rule(from = "抽签", candidates = listOf("{random:1-6}"))))
        val values = (1..20).map { TextProcessor.process("抽签", cfg)!! }.toSet()
        assertNotEquals(1, values.size)
    }
}
