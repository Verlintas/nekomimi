package com.nekomimi.assistant.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ConfigJsonTest {

    @Test
    fun `roundtrip preserves all fields`() {
        val cfg = Config(
            rules = listOf(
                Rule(from = "我", candidates = listOf("本喵")),
                Rule(mode = RuleMode.REGEX, from = "(\\d{11})", candidates = listOf("已打码$1")),
                Rule(from = "你", candidates = listOf("主人", "殿下")),
            ),
            enableAppend = true,
            appendText = "喵~",
            enableRandomEmoticon = false,
            customEmoticons = listOf("(=^w^=)", "ฅ^•ﻌ•^ฅ"),
            processingMode = Config.MODE_REALTIME,
            targetPackages = listOf("com.tencent.mm"),
            excludePackages = listOf("com.android.settings"),
            enableSendFallback = false,
            onlyProcessFocused = false,
            stableDelayMs = 500,
        )
        val decoded = ConfigJson.decode(ConfigJson.encode(cfg))!!
        assertEquals(cfg.rules, decoded.rules)
        assertEquals(cfg.enableAppend, decoded.enableAppend)
        assertEquals(cfg.appendText, decoded.appendText)
        assertEquals(cfg.enableRandomEmoticon, decoded.enableRandomEmoticon)
        assertEquals(cfg.customEmoticons, decoded.customEmoticons)
        assertEquals(cfg.processingMode, decoded.processingMode)
        assertEquals(cfg.targetPackages, decoded.targetPackages)
        assertEquals(cfg.excludePackages, decoded.excludePackages)
        assertEquals(cfg.enableSendFallback, decoded.enableSendFallback)
        assertEquals(cfg.onlyProcessFocused, decoded.onlyProcessFocused)
        assertEquals(cfg.stableDelayMs, decoded.stableDelayMs)
    }

    @Test
    fun `empty config roundtrip`() {
        val cfg = Config()
        assertEquals(cfg.rules, ConfigJson.decode(ConfigJson.encode(cfg))!!.rules)
    }

    @Test
    fun `decode garbage returns null`() {
        assertEquals(null, ConfigJson.decode("not json{{{"))
    }

    @Test
    fun `decode unknown keys is tolerant`() {
        val cfg = ConfigJson.decode("""{"rules":[],"unknown_future_field":123}""")
        assertEquals(emptyList<Rule>(), cfg?.rules)
    }

    @Test
    fun `encode of different configs differ`() {
        val a = ConfigJson.encode(Config(rules = listOf(Rule(from = "a", candidates = listOf("b")))))
        val b = ConfigJson.encode(Config())
        assertNotEquals(a, b)
    }
}
