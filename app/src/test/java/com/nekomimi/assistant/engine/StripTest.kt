/*
 * 猫猫助手 (Nekomimi) — Android accessibility text-rewriting assistant
 * Copyright (C) 2026 Verlintas
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.nekomimi.assistant.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class StripTest {

    private fun cfg(
        enableAppend: Boolean = true,
        appendText: String = "喵",
        customEmoticons: List<String> = emptyList(),
    ) = Config(
        enableAppend = enableAppend,
        appendText = appendText,
        customEmoticons = customEmoticons,
    )

    @Test
    fun `strip removes appended text after sentence parts`() {
        val c = cfg(appendText = "喵")
        assertEquals(
            "今天很好，你准备好了吗？我们去公园吧",
            TextProcessor.stripDecorations("今天很好喵，你准备好了吗喵？我们去公园吧喵", c),
        )
    }

    @Test
    fun `strip removes emoticon with leading space`() {
        val c = cfg(customEmoticons = listOf("(=^w^=)"))
        assertEquals("你好", TextProcessor.stripDecorations("你好 (=^w^=)", c))
    }

    @Test
    fun `strip removes custom emoticons sorted by length`() {
        val c = cfg(customEmoticons = listOf("喵呜(=^w^=)", "(=^w^=)"))
        assertEquals("你好", TextProcessor.stripDecorations("你好 喵呜(=^w^=)", c))
    }

    @Test
    fun `strip removes builtin emoticon`() {
        val c = cfg()
        val em = Config.BUILTIN_EMOTICONS.first()
        assertEquals("你好", TextProcessor.stripDecorations("你好 $em", c))
    }

    @Test
    fun `strip does not remove append inside a word`() {
        val c = cfg(appendText = "喵")
        assertEquals("喵星人", TextProcessor.stripDecorations("喵星人", c))
    }

    @Test
    fun `strip leaves real user punctuation alone`() {
        val c = cfg(appendText = "喵")
        assertEquals("你好！！！", TextProcessor.stripDecorations("你好！！！", c))
    }

    @Test
    fun `strip with append disabled leaves appends`() {
        val c = cfg(enableAppend = false, appendText = "喵")
        assertEquals("好喵，好喵。", TextProcessor.stripDecorations("好喵，好喵。", c))
    }

    @Test
    fun `full roundtrip process then strip removes only decorations`() {
        val c = Config(
            rules = listOf(Rule(from = "我", candidates = listOf("本喵"))),
            enableAppend = true,
            appendText = "喵",
            enableRandomEmoticon = true,
            customEmoticons = listOf("(=^w^=)"),
        )
        val original = "我好，你准备好了吗？"
        val transformed = TextProcessor.process(original, c)!!
        // 剥离后：规则替换保留，追加与颜文字还原
        assertEquals("本喵好，你准备好了吗？", TextProcessor.stripDecorations(transformed, c))
    }

    @Test
    fun `strip empty text returns empty`() {
        assertEquals("", TextProcessor.stripDecorations("", cfg()))
    }
}
