package com.nekomimi.assistant.engine

import kotlinx.serialization.json.Json

/** Config 的 JSON 编解码（纯 Kotlin，可单测）——Profile 持久化用 */
object ConfigJson {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(config: Config): String =
        json.encodeToString(Config.serializer(), config)

    /** 解码失败返回 null（调用方可回退到备份配置） */
    fun decode(text: String): Config? = try {
        json.decodeFromString(Config.serializer(), text)
    } catch (_: Exception) {
        null
    }
}
