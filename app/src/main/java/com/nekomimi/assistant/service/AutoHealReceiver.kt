/*
 * 猫猫助手 (Nekomimi) — Android accessibility text-rewriting assistant
 * Copyright (C) 2026 Verlintas
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.nekomimi.assistant.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.nekomimi.assistant.log.LogStore

/**
 * 自愈接收器 —— 看门狗自身的冗余防线：
 *  - AlarmManager 定时唤醒（15 分钟）：即使前台服务与进程都被系统杀掉，
 *    闹钟仍会唤醒本进程，检查无障碍服务状态并重新拉起看门狗；
 *  - 开机广播：系统重启后无障碍服务会自动重连，但前台服务不会，
 *    开机时重新注册闹钟并拉起看门狗。
 */
class AutoHealReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        try {
            if (!AccessibilityUtil.isEnabled(context)) {
                // 用户未启用无障碍服务：无需保活，但开机广播仍需重新注册闹钟
                if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
                    schedule(context)
                }
                return
            }
            if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
                schedule(context)
            }
            val running = NekoAccessibilityService.isRunning()
            LogStore.i(TAG, "自愈检查: 系统启用=true 服务实例存活=$running")
            // 幂等拉起看门狗（已在运行则仅刷新；被杀了则重新启动前台服务）
            WatchdogService.start(context)
        } catch (t: Throwable) {
            LogStore.e(TAG, "自愈检查异常", t)
        }
    }

    companion object {
        private const val TAG = "AutoHeal"
        private const val INTERVAL_MS = 15 * 60_000L
        private const val ALARM_REQUEST_CODE = 100

        /** 注册周期自愈闹钟（幂等；系统重启后需重新注册） */
        fun schedule(context: Context) {
            try {
                val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                    ?: return
                val pi = PendingIntent.getBroadcast(
                    context,
                    ALARM_REQUEST_CODE,
                    Intent(context, AutoHealReceiver::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                am.setInexactRepeating(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + INTERVAL_MS,
                    INTERVAL_MS,
                    pi,
                )
                LogStore.i(TAG, "自愈闹钟已注册（${INTERVAL_MS / 60000} 分钟）")
            } catch (t: Throwable) {
                LogStore.e(TAG, "自愈闹钟注册失败", t)
            }
        }
    }
}
