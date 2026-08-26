/*
 * 猫猫助手 (Nekomimi) — Android accessibility text-rewriting assistant
 * Copyright (C) 2026 Verlintas
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.nekomimi.assistant.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltinEmoticonsTest {

    @Test
    fun `library has at least 90 emoticons`() {
        assertTrue("内置库应 ≥90 个，实际 ${Config.BUILTIN_EMOTICONS.size}", Config.BUILTIN_EMOTICONS.size >= 90)
    }

    @Test
    fun `no duplicates in builtin library`() {
        val unique = Config.BUILTIN_EMOTICONS.toSet()
        assertEquals("内置库不应有重复", Config.BUILTIN_EMOTICONS.size, unique.size)
    }

    @Test
    fun `all emoticons non-empty and non-trivial`() {
        for (em in Config.BUILTIN_EMOTICONS) {
            assertTrue("空颜文字", em.isNotBlank())
            assertTrue("过短颜文字: [$em]", em.length >= 2)
        }
    }

    @Test
    fun `builtin emoticons are distinct from append text`() {
        // 内置颜文字不应包含追加文本"喵"，否则剥离器会误伤
        for (em in Config.BUILTIN_EMOTICONS) {
            assertTrue("颜文字不应包含[喵]: [$em]", !em.contains("喵"))
        }
    }

    @Test
    fun `first emoticon is stripped correctly`() {
        val c = Config()
        val em = Config.BUILTIN_EMOTICONS.first()
        assertEquals("你好", TextProcessor.stripDecorations("你好 $em", c))
    }

    @Test
    fun `every builtin emoticon strips cleanly`() {
        val c = Config()
        for (em in Config.BUILTIN_EMOTICONS) {
            val stripped = TextProcessor.stripDecorations("你好 $em", c)
            assertEquals("剥离失败: [$em] -> [$stripped]", "你好", stripped)
        }
    }
}
