package com.nekomimi.assistant.engine

import android.content.Context
import android.content.SharedPreferences
import com.nekomimi.assistant.log.LogStore

/**
 * Config 的 SharedPreferences 持久化（服务与 UI 共用）。
 *
 * 多套配置 Profile：
 *  - 每个配置存为 JSON 文本，键 `profile:<名字>`；
 *  - `active_profile` 记录当前激活配置；
 *  - 旧版（v0.1.x 单配置字段）数据首次读取时自动迁移为 default profile。
 */
object ConfigStore {
    private const val TAG = "ConfigStore"
    private const val PREFS = "nekomimi_config"
    private const val KEY_ACTIVE_PROFILE = "active_profile"
    private const val KEY_PROFILE_PREFIX = "profile:"
    private const val KEY_CONFIG_EPOCH = "config_epoch"
    const val DEFAULT_PROFILE = "default"
    const val MAX_PROFILE_NAME = 16

    /** 配置版本号：任何保存/切换/删除都会递增，服务据此重置增量跟踪状态 */
    fun configEpoch(ctx: Context): Long =
        prefs(ctx).getLong(KEY_CONFIG_EPOCH, 0L)

    private fun bumpEpoch(ctx: Context) {
        val sp = prefs(ctx)
        sp.edit().putLong(KEY_CONFIG_EPOCH, sp.getLong(KEY_CONFIG_EPOCH, 0L) + 1L).apply()
    }

    // ============ 旧版字段（v0.1.x 迁移用） ============
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
    private const val KEY_PAUSED = "paused"

    // ============ 当前配置（激活 Profile） ============

    fun load(ctx: Context): Config = loadProfile(ctx, activeProfileName(ctx))

    fun save(ctx: Context, config: Config) {
        saveProfile(ctx, activeProfileName(ctx), config)
        bumpEpoch(ctx)
    }

    /** 全局暂停（通知栏快捷开关），暂停期间不处理任何输入 */
    fun isPaused(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_PAUSED, false)

    fun setPaused(ctx: Context, paused: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_PAUSED, paused).apply()
    }

    // ============ Profile 管理 ============

    fun activeProfileName(ctx: Context): String =
        prefs(ctx).getString(KEY_ACTIVE_PROFILE, DEFAULT_PROFILE) ?: DEFAULT_PROFILE

    fun setActiveProfile(ctx: Context, name: String) {
        val target = if (name in profiles(ctx)) name else DEFAULT_PROFILE
        prefs(ctx).edit().putString(KEY_ACTIVE_PROFILE, target).apply()
        bumpEpoch(ctx)
    }

    /** 全部配置名（default 始终存在） */
    fun profiles(ctx: Context): List<String> {
        val names = prefs(ctx).all.keys
            .filter { it.startsWith(KEY_PROFILE_PREFIX) }
            .map { it.removePrefix(KEY_PROFILE_PREFIX) }
            .sorted()
        return if (DEFAULT_PROFILE in names) names else listOf(DEFAULT_PROFILE) + names
    }

    /** 新建配置（复制当前配置），名字非法/已存在返回 null */
    fun createProfile(ctx: Context, name: String, from: Config): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_PROFILE_NAME || trimmed in profiles(ctx)) {
            return null
        }
        saveProfile(ctx, trimmed, from)
        return trimmed
    }

    /** 删除配置；删除的是当前配置时自动切回 default。default 不可删除。 */
    fun deleteProfile(ctx: Context, name: String) {
        if (name == DEFAULT_PROFILE) {
            return
        }
        prefs(ctx).edit().remove(KEY_PROFILE_PREFIX + name).apply()
        if (activeProfileName(ctx) == name) {
            setActiveProfile(ctx, DEFAULT_PROFILE)
        } else {
            bumpEpoch(ctx)
        }
    }

    fun loadProfile(ctx: Context, name: String): Config {
        val sp = prefs(ctx)
        val json = sp.getString(KEY_PROFILE_PREFIX + name, null)
        if (json != null) {
            val decoded = ConfigJson.decode(json)
            if (decoded != null) {
                return decoded
            }
            // 主配置损坏 → 尝试文件备份恢复（冗余设计：配置永不丢失）
            val backup = readBackup(ctx)
            if (backup != null) {
                LogStore.w(TAG, "配置 [$name] 解析失败，已从备份恢复")
                saveProfile(ctx, name, backup)
                return backup
            }
            LogStore.w(TAG, "配置 [$name] 解析失败且无备份，使用默认配置")
            return Config()
        }
        if (name == DEFAULT_PROFILE) {
            // 旧版数据迁移：首次加载 default 且旧字段存在时
            val legacy = readLegacy(sp)
            if (legacy != null) {
                saveProfile(ctx, DEFAULT_PROFILE, legacy)
                clearLegacy(sp)
                return legacy
            }
        }
        return Config()
    }

    fun saveProfile(ctx: Context, name: String, config: Config) {
        prefs(ctx).edit().putString(KEY_PROFILE_PREFIX + name, ConfigJson.encode(config)).apply()
        // 冗余：同步写文件备份（配置损坏时恢复用）
        writeBackup(ctx, config)
    }

    // ============ 文件备份（配置冗余） ============

    private const val BACKUP_FILE = "config_backup.json"

    private fun backupFile(ctx: Context): java.io.File =
        java.io.File(ctx.filesDir, BACKUP_FILE)

    private fun writeBackup(ctx: Context, config: Config) {
        try {
            backupFile(ctx).writeText(ConfigJson.encode(config))
        } catch (_: Exception) {
            // 备份失败不阻断主流程
        }
    }

    private fun readBackup(ctx: Context): Config? {
        return try {
            val f = backupFile(ctx)
            if (!f.exists()) {
                null
            } else {
                ConfigJson.decode(f.readText())
            }
        } catch (_: Exception) {
            null
        }
    }

    // ============ 旧版迁移 ============

    private fun readLegacy(sp: SharedPreferences): Config? {
        if (!sp.contains(KEY_RULES) && !sp.contains(KEY_ENABLE_APPEND)) {
            return null
        }
        return Config(
            rules = Config.parseRulesText(sp.getString(KEY_RULES, "") ?: ""),
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

    private fun clearLegacy(sp: SharedPreferences) {
        val legacyKeys = listOf(
            KEY_RULES, KEY_ENABLE_APPEND, KEY_APPEND_TEXT, KEY_ENABLE_EMOTICON,
            KEY_CUSTOM_EMOTICONS, KEY_PROCESSING_MODE, KEY_TARGET_PACKAGES,
            KEY_EXCLUDE_PACKAGES, KEY_ENABLE_SEND_FALLBACK, KEY_ONLY_FOCUSED, KEY_STABLE_DELAY,
        )
        val edit = sp.edit()
        for (k in legacyKeys) {
            edit.remove(k)
        }
        edit.apply()
    }

    private fun splitLines(s: String): List<String> =
        s.lines().map { it.trim() }.filter { it.isNotEmpty() }

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
