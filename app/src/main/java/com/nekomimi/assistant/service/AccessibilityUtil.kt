package com.nekomimi.assistant.service

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

/** 无障碍服务状态查询与跳转（UI 与 Watchdog 共用） */
object AccessibilityUtil {
    fun isEnabled(context: Context): Boolean {
        return try {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val list = am.getEnabledAccessibilityServiceList(
                android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK,
            )
            val myName = NekoAccessibilityService::class.java.name
            list.any { info ->
                val si = info.resolveInfo.serviceInfo
                si.packageName == context.packageName &&
                    (si.name == myName || si.name.endsWith("NekoAccessibilityService"))
            }
        } catch (t: Throwable) {
            com.nekomimi.assistant.log.LogStore.e("A11yUtil", "无障碍状态查询失败", t)
            false
        }
    }

    fun openSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (t: Throwable) {
            com.nekomimi.assistant.log.LogStore.e("A11yUtil", "打开无障碍设置失败", t)
        }
    }

    fun openBatterySettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (t: Throwable) {
            com.nekomimi.assistant.log.LogStore.e("A11yUtil", "打开电池设置失败", t)
        }
    }

    fun openBatteryRequest(context: Context) {
        try {
            context.startActivity(BatteryGuard.requestIgnoreIntent(context))
        } catch (t: Throwable) {
            openBatterySettings(context)
        }
    }
}
