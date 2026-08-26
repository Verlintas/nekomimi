/*
 * 猫猫助手 (Nekomimi) — Android accessibility text-rewriting assistant
 * Copyright (C) 2026 Verlintas
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.nekomimi.assistant.engine

import kotlinx.serialization.Serializable

/** 完整配置模型（纯 Kotlin，可在 JVM 单测中使用；持久化由 ConfigStore 负责） */
@Serializable
data class Config(
    val rules: List<Rule> = emptyList(),
    val enableAppend: Boolean = true,
    val appendText: String = "喵",
    val enableRandomEmoticon: Boolean = true,
    val customEmoticons: List<String> = emptyList(),
    val processingMode: String = MODE_PUNCTUATION,
    val targetPackages: List<String> = emptyList(),
    val excludePackages: List<String> = emptyList(),
    val enableSendFallback: Boolean = true,
    val onlyProcessFocused: Boolean = true,
    val stableDelayMs: Int = 800,
) {
    /** 生效的颜文字库：自定义优先，否则内置 */
    val activeEmoticons: List<String>
        get() = if (customEmoticons.isNotEmpty()) customEmoticons else BUILTIN_EMOTICONS

    /** 是否允许处理指定包名：白名单非空则必须命中；否则不在黑名单且不在默认排除列表 */
    fun shouldHandlePackage(pkg: String): Boolean {
        if (pkg.isEmpty()) {
            return false
        }
        if (targetPackages.isNotEmpty()) {
            return pkg in targetPackages
        }
        if (pkg in DEFAULT_EXCLUDE_PACKAGES) {
            return false
        }
        return pkg !in excludePackages
    }

    companion object {
        const val MODE_PUNCTUATION = "punctuation"
        const val MODE_REALTIME = "realtime"

        /** 内置猫咪颜文字库（沿用上游数据 + 扩展：开心/害羞/睡觉/傲娇/惊讶/委屈/日常） */
        val BUILTIN_EMOTICONS = listOf(
            // —— 基础（上游） ——
            "^⌯𖥦⌯^ ੭ ^", "⌯'ㅅ'⌯", "=^𖥦^=", "⌯•ㅅ•⌯", "ฅ•̀∀•́ฅ", "ฅ ̳͒•ˑ̫• ̳͒ฅ♡",
            "ฅ(̳•·̫•̳ฅ)♡", "ฅ^••^ฅ", "=^•ω•^=", "₍^ >ヮ<^₎", "/ᐠ - ˕ -マ Ⳋ", "ฅ^•ﻌ•^ฅ",
            "ฅ՞•ﻌ•՞ฅ", "(ฅ´ω`ฅ)", "ฅ(*`ω´*)ฅ", "ฅ꒰ ⸝˶• •˶⸝꒱ฅ", "₍˄·͈༝·͈˄*₎◞ ̑̑",
            "!!^⌯𖥦⌯^ ੭!!", "₍^⸝⸝> ·̫ <⸝⸝ ^₎", "ฅ^._.^ฅ", "₍🎀˄•͈༝•͈˄₎ฅ˒˒", "^•͈༝•^ฅ",
            "꒰ఎ(^ . ֑ .^)໒꒱", "ฅ●ω●ฅ", "₍⸍⸌·͈༝·͈⸍⸌₎◞", "(>^ω^<)", "ฅ^-﹃-^ฅ", "^ ̳ට ̫ ට ̳^",
            "୧₍˄·͈༝·͈˄₎୨", "^ ̳ᴗ  ̫ ᴗ ̳^", "˓˓ก(⸍⸌̣ʷ̣̫⸍̣⸌₎ค˒˒", "ヽ(ฅ≧へ≦)ฅ", "(`･ω･´)ฅ",
            "(=^･ᴥ･^=)", "(^ω^ฅ)", "ฅ(≧▽≦)ฅ", "ฅ(=´▽`=)ฅ", "ヾ((๑˘ㅂ˘๑)ฅ", "(ฅ◑ω◑ฅ)",
            "(๑•̀ω•́ฅ)", "(ฅ>ω<*ฅ)", "(=^.^=)", "(=´ᴥ`)", "(=ↀωↀ=)", "(=^-ω-^=)", "ฅ(*°ω°*ฅ)",
            "ヽ(=^･ω･^=)丿", "(^•ᴥ•^)", "( Φ ω Φ )", "(=^x^=)", "ฅ( ̳• ◡ • ̳)ฅ", "o( =•ω•= )m",
            "~o( =∩ω∩= )m", "≡ω≡",
            // —— 开心 / 元气 ——
            "(=｡･ω･｡=)", "ฅ(=｡･ω･｡=)ฅ", "(=｀ω´=)", "(=＾● ⋏ ●＾=)", "(=^◕ᴥ◕^=)",
            "ฅ(^◕ᴥ◕^)ฅ", "(=◕ᆺ◕=)", "(⌒ω⌒)ฅ", "ฅ(๑•̀ω•́๑)ฅ", "(ฅ´∀`ฅ)",
            "ฅ(๑*▽*๑)ฅ", "(=｡•̀ᴗ-)✧", "ฅ(=｡•̀ᴗ-)✧ฅ", "(=´∀｀=)", "ฅ(=´∀｀=)ฅ",
            "(=´▽`=)", "(^・ω・^ )", "ฅ(^ω^ =)", "(●´ω｀●)ฅ", "₍=ᵔ ᵕ ᵔ=₎",
            "ฅ(=^‥^=)ฅ", "(^ᵔᴥᵔ^)",
            // —— 害羞 / 撒娇 ——
            "(〃∀〃)ฅ", "(⁄ ⁄•⁄ω⁄•⁄ ⁄)", "(ฅ`ω´ฅ)", "ฅ(´•ω•`)ฅ", "(=｡•́ω•̀｡=)",
            "ฅ( =ω= )ฅ", "(=^..^=)", "ฅ(^..^)ฅ",
            // —— 睡觉 / 慵懒 ——
            "(=ΦωΦ=)", "ฅ(=ΦωΦ=)ฅ", "(=_=)", "ฅ(=￣ω￣=)ฅ", "(=￣ω￣=)",
            "ฅ(=｡‥=)ฅ", "( ˘ω˘ )", "ヽ( ˘ω˘ )ゝ",
            // —— 傲娇 / 精神 ——
            "(=·̀ω·́=)", "(=^-ω-^)", "ฅ(=·̀ω·́=)ฅ", "(=`ω´=)", "(=｀ェ´=)",
            "ฅ(≧ω≦)ฅ", "(=￣L￣=)",
            // —— 惊讶 / 委屈 / 哭 ——
            "(=￣□￣=)", "ฅ(=￣□￣=)ฅ", "(=ﾟωﾟ)=", "(=;ω;=)", "ฅ(;ω;)ฅ",
            "(´;ω;`)ฅ", "(=ﾟ･ﾟ=)", "ฅ(=ﾟДﾟ=)ฅ", "(=ω=；)",
            // —— 好奇 / 发呆 / 日常 ——
            "(=^･ｪ･^=)", "(^･ｪ･^)", "(=ㅇㅅㅇ=)", "(=^‥^=)",
            "(๑ↀᆺↀ๑)", "ฅ(=^･ｪ･^=)ฅ", "(=´｡•ω•)ノ", "ฅ(^•ω•^ฅ)",
        )

        /** 默认排除的应用包名：输入法（IME）、桌面启动器、系统界面 —— 这些应用里的输入框绝不改写 */
        val DEFAULT_EXCLUDE_PACKAGES = listOf(
            "com.android.inputmethod.latin",
            "com.google.android.inputmethod.latin",
            "com.sohu.inputmethod.sogou",
            "com.baidu.input",
            "com.iflytek.inputmethod",
            "com.tencent.qqpinyin",
            "com.qq.pinyin",
            "com.touchtype.swiftkey",
            "com.aliyun.inputmethod",
            "com.android.systemui",
            "com.android.settings",
            "com.android.launcher3",
            "com.google.android.apps.nexuslauncher",
            "com.miui.home",
            "com.sec.android.app.launcher",
            "com.huawei.android.launcher",
            "com.oppo.launcher",
            "com.vivo.launcher",
            "com.meizu.flyme.launcher",
        )

        fun rulesToText(rules: List<Rule>): String =
            rules.filter { it.from.isNotEmpty() }.joinToString("\n") { it.display }

        fun parseRulesText(text: String): List<Rule> =
            text.lines().mapNotNull { Rule.parse(it) }
    }
}
