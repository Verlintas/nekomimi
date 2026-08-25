package com.nekomimi.assistant.engine

import android.content.Context
import android.content.SharedPreferences

/** Config 的 SharedPreferences 持久化（服务与 UI 共用） */
object ConfigStore {
    private const val PREFS = "nekomimi_config"
    private const val KEY_RULES = "rules"
    private const val KEY_ENABLE_APPEND = "enable_append"
    private const val KEY_APPEND_TEXT = "append_text"
    private const val KEY_ENABLE_EMOTICON = "enable_emoticon"
    private const val KEY_CUSTOM_EMOTICONS = "custom_emoticons"
    private const val KEY_PROCESSING_MODE = "processing_mode"
    private const val KEY_TARGET_PACKAGES = "target_packages"
    private const val KEY_EXCLUDE_PACKAGES = "exclude_packages"
    private const val KEY_ENABLE_SEND_FALLBACK = "enable_send_fallback"
    private const val KEY_ONLY_FOCUSED = "only_focused"
    private const val KEY_STABLE_DELAY = "stable_delay_ms"

    fun load(ctx: Context): Config {
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val rulesText = sp.getString(KEY_RULES, "") ?: ""
        return Config(
            rules = Config.parseRulesText(rulesText),
            enableAppend = sp.getBoolean(KEY_ENABLE_APPEND, true),
            appendText = sp.getString(KEY_APPEND_TEXT, "喵") ?: "喵",
            enableRandomEmoticon = sp.getBoolean(KEY_ENABLE_EMOTICON, true),
            customEmoticons = splitLines(sp.getString(KEY_CUSTOM_EMOTICONS, "") ?: ""),
            processingMode = sp.getString(KEY_PROCESSING_MODE, Config.MODE_PUNCTUATION)
                ?: Config.MODE_PUNCTUATION,
            targetPackages = splitLines(sp.getString(KEY_TARGET_PACKAGES, "") ?: ""),
            excludePackages = splitLines(sp.getString(KEY_EXCLUDE_PACKAGES, "") ?: ""),
            enableSendFallback = sp.getBoolean(KEY_ENABLE_SEND_FALLBACK, true),
            onlyProcessFocused = sp.getBoolean(KEY_ONLY_FOCUSED, true),
            stableDelayMs = sp.getInt(KEY_STABLE_DELAY, 800).coerceAtLeast(0),
        )
    }

    fun save(ctx: Context, config: Config) {
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.edit()
            .putString(KEY_RULES, Config.rulesToText(config.rules))
            .putBoolean(KEY_ENABLE_APPEND, config.enableAppend)
            .putString(KEY_APPEND_TEXT, config.appendText)
            .putBoolean(KEY_ENABLE_EMOTICON, config.enableRandomEmoticon)
            .putString(KEY_CUSTOM_EMOTICONS, config.customEmoticons.joinToString("\n"))
            .putString(KEY_PROCESSING_MODE, config.processingMode)
            .putString(KEY_TARGET_PACKAGES, config.targetPackages.joinToString("\n"))
            .putString(KEY_EXCLUDE_PACKAGES, config.excludePackages.joinToString("\n"))
            .putBoolean(KEY_ENABLE_SEND_FALLBACK, config.enableSendFallback)
            .putBoolean(KEY_ONLY_FOCUSED, config.onlyProcessFocused)
            .putInt(KEY_STABLE_DELAY, config.stableDelayMs)
            .apply()
    }

    private fun splitLines(s: String): List<String> =
        s.lines().map { it.trim() }.filter { it.isNotEmpty() }
}
