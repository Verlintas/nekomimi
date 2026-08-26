/*
 * 猫猫助手 (Nekomimi) — Android accessibility text-rewriting assistant
 * Copyright (C) 2026 Verlintas
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.nekomimi.assistant.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nekomimi.assistant.engine.Config
import com.nekomimi.assistant.engine.ConfigStore

/** 全局 UI 状态：配置 + 暂停开关 + 多套 Profile（保存即写 SharedPreferences，服务端读取） */
class AppState(private val context: Context) {
    var config by mutableStateOf(ConfigStore.load(context))
        private set

    var paused by mutableStateOf(ConfigStore.isPaused(context))
        private set

    var activeProfile by mutableStateOf(ConfigStore.activeProfileName(context))
        private set

    /** 版本号：reload/切换后递增，UI 用它强制重组刷新（如服务状态） */
    var version by mutableIntStateOf(0)
        private set

    val profiles: List<String>
        get() = ConfigStore.profiles(context)

    fun save(newConfig: Config) {
        config = newConfig
        ConfigStore.save(context, newConfig)
    }

    fun updatePaused(value: Boolean) {
        paused = value
        ConfigStore.setPaused(context, value)
    }

    /** 切换配置：加载该配置到内存，立即生效 */
    fun switchProfile(name: String) {
        if (name == activeProfile) {
            return
        }
        ConfigStore.setActiveProfile(context, name)
        activeProfile = name
        config = ConfigStore.load(context)
        version++
    }

    /** 新建配置（复制当前），返回是否成功 */
    fun createProfile(name: String): Boolean {
        val created = ConfigStore.createProfile(context, name, config) ?: return false
        switchProfile(created)
        return true
    }

    fun deleteProfile(name: String) {
        ConfigStore.deleteProfile(context, name)
        activeProfile = ConfigStore.activeProfileName(context)
        config = ConfigStore.load(context)
        version++
    }

    /** 从系统侧刷新（如无障碍开关状态变化后回到应用） */
    fun refresh() {
        config = ConfigStore.load(context)
        paused = ConfigStore.isPaused(context)
        activeProfile = ConfigStore.activeProfileName(context)
        version++
    }
}
