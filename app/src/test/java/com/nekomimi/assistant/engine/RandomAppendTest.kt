/*
 * 猫猫助手 (Nekomimi) — Android accessibility text-rewriting assistant
 * Copyright (C) 2026 Verlintas
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.nekomimi.assistant.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RandomAppendTest {

    @Test
    fun `single candidate behaves as before`() {
        val c = Config(enableAppend = true, appendText = "喵", enableRandomEmoticon = false)
        assertEquals("好喵，好喵。", TextProcessor.process("好，好。", c))
    }

    @Test
    fun `multiple candidates pick one per sentence`() {
        val c = Config(enableAppend = true, appendText = "喵|喵呜|喵喵", enableRandomEmoticon = false)
        val pool = setOf("喵", "喵呜", "喵喵")
        repeat(200) {
            val out = TextProcessor.process("好，好。", c)!!
            val parts = out.split("，", "。").filter { it.isNotEmpty() }
            assertEquals(2, parts.size)
            assertTrue(pool.any { parts[0] == "好$it" } && pool.any { parts[1] == "好$it" })
        }
    }

    @Test
    fun `random append is not constant`() {
        val c = Config(enableAppend = true, appendText = "喵|喵呜|喵喵", enableRandomEmoticon = false)
        val outputs = (1..100).map { TextProcessor.process("好。", c)!! }.toSet()
        assertTrue(outputs.size > 1)
    }

    @Test
    fun `empty candidates filtered`() {
        val c = Config(enableAppend = true, appendText = "喵||喵呜|", enableRandomEmoticon = false)
        val out = TextProcessor.process("好。", c)!!
        assertTrue(out == "好喵。" || out == "好喵呜。")
    }

    @Test
    fun `append disabled leaves random syntax alone`() {
        val c = Config(enableAppend = false, appendText = "喵|喵呜", enableRandomEmoticon = false)
        assertEquals("好。", TextProcessor.process("好。", c))
    }

    @Test
    fun `strip removes any candidate`() {
        val c = Config(enableAppend = true, appendText = "喵|喵呜|喵喵", enableRandomEmoticon = false)
        assertEquals("好，好。", TextProcessor.stripDecorations("好喵呜，好喵。", c))
        assertEquals("好，好。", TextProcessor.stripDecorations("好喵，好喵喵。", c))
    }

    @Test
    fun `strip does not remove candidate inside word`() {
        val c = Config(enableAppend = true, appendText = "喵|喵呜")
        assertEquals("喵星人", TextProcessor.stripDecorations("喵星人", c))
    }

    @Test
    fun `process then strip roundtrip with random append`() {
        val c = Config(
            enableAppend = true,
            appendText = "喵|喵呜",
            enableRandomEmoticon = true,
            customEmoticons = listOf("(=^w^=)"),
        )
        val original = "我好，你准备好了吗？"
        val transformed = TextProcessor.process(original, c)!!
        assertEquals("我好，你准备好了吗？", TextProcessor.stripDecorations(transformed, c))
    }
}
