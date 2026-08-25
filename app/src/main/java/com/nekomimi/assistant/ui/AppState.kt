package com.nekomimi.assistant.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nekomimi.assistant.engine.Config
import com.nekomimi.assistant.engine.ConfigStore

/** 全局 UI 状态：配置 + 暂停开关（保存即写 SharedPreferences，服务端读取） */
class AppState(private val context: Context) {
    var config by mutableStateOf(ConfigStore.load(context))
        private set

    var paused by mutableStateOf(ConfigStore.isPaused(context))
        private set

    fun save(newConfig: Config) {
        config = newConfig
        ConfigStore.save(context, newConfig)
    }

    fun updatePaused(value: Boolean) {
        paused = value
        ConfigStore.setPaused(context, value)
    }

    fun reload() {
        config = ConfigStore.load(context)
        paused = ConfigStore.isPaused(context)
    }
}
