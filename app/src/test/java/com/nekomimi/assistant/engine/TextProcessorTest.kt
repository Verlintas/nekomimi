package com.nekomimi.assistant.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextProcessorTest {

    private fun cfg(
        rules: List<Rule> = emptyList(),
        enableAppend: Boolean = false,
        appendText: String = "喵",
        enableEmoticon: Boolean = false,
        customEmoticons: List<String> = emptyList(),
    ) = Config(
        rules = rules,
        enableAppend = enableAppend,
        appendText = appendText,
        enableRandomEmoticon = enableEmoticon,
        customEmoticons = customEmoticons,
    )

    @Test
    fun `blank input returns unchanged`() {
        assertEquals("   ", TextProcessor.process("   ", cfg()))
        assertEquals("", TextProcessor.process("", cfg()))
    }

    @Test
    fun `literal rule replaces all occurrences`() {
        val c = cfg(rules = listOf(Rule(from = "我", candidates = listOf("本喵"))))
        assertEquals("本喵是本喵", TextProcessor.process("我是我", c))
    }

    @Test
    fun `rules applied in order`() {
        val c = cfg(
            rules = listOf(
                Rule(from = "a", candidates = listOf("b")),
                Rule(from = "b", candidates = listOf("c")),
            ),
        )
        assertEquals("c", TextProcessor.process("a", c))
    }

    @Test
    fun `invalid regex rule is skipped not crash`() {
        val c = cfg(rules = listOf(Rule(mode = RuleMode.REGEX, from = "([", candidates = listOf("x"))))
        assertEquals("hello", TextProcessor.process("hello", c))
    }

    @Test
    fun `regex rule with capture group backreference`() {
        val c = cfg(rules = listOf(Rule(mode = RuleMode.REGEX, from = "(\\d{4})-(\\d{4})", candidates = listOf("$2-$1"))))
        assertEquals("5678-1234", TextProcessor.process("1234-5678", c))
    }

    @Test
    fun `regex rule with dollar literal escaping`() {
        val c = cfg(rules = listOf(Rule(mode = RuleMode.REGEX, from = "价格:(\\d+)", candidates = listOf("$$$$$1"))))
        assertEquals("$$100 元-> $$100", TextProcessor.process("价格:100 元-> $$100", c))
    }

    @Test
    fun `random rule picks one of candidates each time`() {
        val c = cfg(rules = listOf(Rule(from = "你", candidates = listOf("主人", "殿下", "亲亲"))))
        val pool = setOf("主人", "殿下", "亲亲")
        repeat(300) {
            val out = TextProcessor.process("你", c)!!
            assertTrue("unexpected output: $out", out in pool)
        }
    }

    @Test
    fun `random rule never leaves original token`() {
        val c = cfg(rules = listOf(Rule(from = "你", candidates = listOf("主人", "殿下"))))
        repeat(100) {
            val out = TextProcessor.process("你好吗你", c)!!
            assertTrue(!out.contains("你"))
        }
    }

    @Test
    fun `literal multi-candidate picks per occurrence`() {
        val c = cfg(rules = listOf(Rule(from = "你", candidates = listOf("主人", "殿下"))))
        val out = TextProcessor.process("你你你", c)!!
        assertEquals(6, out.length)
        assertTrue(out.chunked(2).all { it == "主人" || it == "殿下" })
    }

    @Test
    fun `regex multi-candidate picks per match`() {
        val c = cfg(rules = listOf(Rule(mode = RuleMode.REGEX, from = "\\d", candidates = listOf("A", "B"))))
        val out = TextProcessor.process("123", c)!!
        assertEquals("AAA".length, out.length)
        assertTrue(out.all { it == 'A' || it == 'B' })
    }

    @Test
    fun `append per sentence before separators`() {
        val c = cfg(enableAppend = true, appendText = "喵")
        assertEquals(
            "今天很好喵，你准备好了吗喵？我们去公园吧喵",
            TextProcessor.process("今天很好，你准备好了吗？我们去公园吧", c),
        )
    }

    @Test
    fun `append respects spaces in separators`() {
        val c = cfg(enableAppend = true, appendText = "喵")
        assertEquals(
            "好喵， 好喵！",
            TextProcessor.process("好， 好！", c),
        )
    }

    @Test
    fun `append disabled leaves text alone`() {
        val c = cfg(enableAppend = false, appendText = "喵")
        assertEquals("你好吗？", TextProcessor.process("你好吗？", c))
    }

    @Test
    fun `custom append text`() {
        val c = cfg(enableAppend = true, appendText = "喵呜")
        assertEquals("好喵呜，好喵呜。", TextProcessor.process("好，好。", c))
    }

    @Test
    fun `emoticon appended with leading space`() {
        val c = cfg(enableAppend = false, enableEmoticon = true, customEmoticons = listOf("(=^w^=)"))
        assertEquals("你好 (=^w^=)", TextProcessor.process("你好", c)!!)
    }

    @Test
    fun `builtin emoticons used when custom empty`() {
        val c = cfg(enableEmoticon = true)
        val out = TextProcessor.process("你好", c)!!
        assertTrue(out.startsWith("你好 "))
        assertTrue(Config.BUILTIN_EMOTICONS.any { out.endsWith(it) })
    }

    @Test
    fun `full pipeline rules then append then emoticon`() {
        val c = Config(
            rules = listOf(Rule(from = "我", candidates = listOf("本喵"))),
            enableAppend = true,
            appendText = "喵",
            enableRandomEmoticon = true,
            customEmoticons = listOf("(=^w^=)"),
        )
        val out = TextProcessor.process("我好。", c)
        assertEquals("本喵好喵。 (=^w^=)", out)
    }

    @Test
    fun `validate rule reports regex errors`() {
        assertTrue(TextProcessor.validateRule(Rule(mode = RuleMode.REGEX, from = "([", candidates = listOf("x"))) != null)
        assertEquals(null, TextProcessor.validateRule(Rule(from = "我", candidates = listOf("本喵"))))
        assertTrue(TextProcessor.validateRule(Rule(from = "", candidates = listOf("x"))) != null)
        assertTrue(TextProcessor.validateRule(Rule(from = "我", candidates = emptyList())) != null)
    }

    @Test
    fun `random processing is not always same candidate`() {
        val c = cfg(rules = listOf(Rule(from = "x", candidates = listOf("1", "2"))))
        val outputs = (1..100).map { TextProcessor.process("x", c) }.toSet()
        assertTrue(outputs.size > 1)
    }

    @Test
    fun `null input returns null`() {
        assertEquals(null, TextProcessor.process(null, cfg()))
    }
}
