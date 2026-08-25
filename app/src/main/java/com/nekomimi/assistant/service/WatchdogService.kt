package com.nekomimi.assistant.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.nekomimi.assistant.MainActivity
import com.nekomimi.assistant.R
import com.nekomimi.assistant.engine.ConfigStore
import com.nekomimi.assistant.log.LogStore

/**
 * 看门狗前台服务 —— 掉线问题的核心防线：
 *  - 前台服务保活：无障碍服务连接时启动，防止 OEM 电池优化杀进程后服务不恢复；
 *  - 周期健康检查：确认系统仍启用本应用无障碍服务，掉线即发高优通知一键恢复；
 *  - 电池优化引导：检测到未加入电池白名单时持续提示，直到放行；
 *  - 通知栏快捷开关：暂停/恢复改写，状态常驻可见。
 */
class WatchdogService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val checkTask = object : Runnable {
        override fun run() {
            try {
                check()
            } catch (t: Throwable) {
                LogStore.e(TAG, "健康检查异常", t)
            }
            handler.postDelayed(this, CHECK_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE_PAUSE -> {
                val paused = ConfigStore.isPaused(this)
                ConfigStore.setPaused(this, !paused)
                LogStore.i(TAG, "暂停开关 -> ${!paused}")
            }
            ACTION_NEXT_PROFILE -> {
                val current = ConfigStore.activeProfileName(this)
                val profiles = ConfigStore.profiles(this)
                val next = profiles[(profiles.indexOf(current) + 1).mod(profiles.size)]
                ConfigStore.setActiveProfile(this, next)
                LogStore.i(TAG, "配置轮换 -> $next")
            }
        }
        startForegroundInternal()
        handler.removeCallbacks(checkTask)
        handler.post(checkTask)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(checkTask)
        LogStore.i(TAG, "看门狗服务停止")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundInternal() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIF_FGS_ID, buildStatusNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIF_FGS_ID, buildStatusNotification())
            }
        } catch (t: Throwable) {
            LogStore.e(TAG, "startForeground 失败", t)
        }
    }

    private fun check() {
        val enabled = isAccessibilityEnabled()
        val alive = NekoAccessibilityService.isRunning()
        LogStore.i(TAG, "健康检查: 系统已启用=$enabled 服务实例存活=$alive")
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (enabled) {
            nm.cancel(NOTIF_ALERT_ID)
        } else {
            nm.notify(NOTIF_ALERT_ID, buildAlertNotification())
            LogStore.w(TAG, "检测到无障碍服务掉线，已发送恢复通知")
        }
        if (!BatteryGuard.isIgnoringBatteryOptimizations(this)) {
            nm.notify(NOTIF_BATTERY_ID, buildBatteryNotification())
        } else {
            nm.cancel(NOTIF_BATTERY_ID)
        }
        nm.notify(NOTIF_FGS_ID, buildStatusNotification())
    }

    private fun isAccessibilityEnabled(): Boolean {
        return try {
            AccessibilityUtil.isEnabled(this)
        } catch (t: Throwable) {
            LogStore.e(TAG, "无障碍状态查询失败", t)
            false
        }
    }

    // ==================== 通知构建 ====================

    private fun createChannels() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_STATUS, "服务状态", NotificationManager.IMPORTANCE_LOW).apply {
                description = "常驻通知：显示无障碍服务状态与暂停开关"
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERT, "掉线提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "无障碍服务掉线时提醒一键恢复"
            },
        )
    }

    private fun appIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun togglePauseIntent(): PendingIntent = PendingIntent.getService(
        this, 1,
        Intent(this, WatchdogService::class.java).setAction(ACTION_TOGGLE_PAUSE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun nextProfileIntent(): PendingIntent = PendingIntent.getService(
        this, 2,
        Intent(this, WatchdogService::class.java).setAction(ACTION_NEXT_PROFILE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun a11ySettingsIntent(): PendingIntent = PendingIntent.getActivity(
        this, 3,
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun batterySettingsIntent(): PendingIntent = PendingIntent.getActivity(
        this, 4,
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun buildStatusNotification(): android.app.Notification {
        val paused = ConfigStore.isPaused(this)
        val enabled = isAccessibilityEnabled()
        val profile = ConfigStore.activeProfileName(this)
        val title = if (paused) "猫猫助手（已暂停）" else "猫猫助手运行中"
        val text = if (enabled) {
            "配置：$profile · 点击管理"
        } else {
            "无障碍服务已掉线 · 点击查看"
        }
        return NotificationCompat.Builder(this, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(appIntent())
            .addAction(0, if (paused) "恢复" else "暂停", togglePauseIntent())
            .addAction(0, "下一个配置", nextProfileIntent())
            .build()
    }

    private fun buildAlertNotification(): android.app.Notification =
        NotificationCompat.Builder(this, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("猫猫助手：无障碍服务已掉线")
            .setContentText("文本改写已失效，点击前往系统设置一键恢复")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(a11ySettingsIntent())
            .build()

    private fun buildBatteryNotification(): android.app.Notification =
        NotificationCompat.Builder(this, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("猫猫助手：建议允许后台运行")
            .setContentText("当前未加入电池优化白名单，服务可能被系统杀掉。点击前往设置")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(batterySettingsIntent())
            .build()

    companion object {
        private const val TAG = "Watchdog"
        const val ACTION_TOGGLE_PAUSE = "com.nekomimi.assistant.action.TOGGLE_PAUSE"
        const val ACTION_NEXT_PROFILE = "com.nekomimi.assistant.action.NEXT_PROFILE"
        private const val NOTIF_FGS_ID = 1
        private const val NOTIF_ALERT_ID = 2
        private const val NOTIF_BATTERY_ID = 3
        private const val CHECK_INTERVAL_MS = 60_000L
        private const val CHANNEL_STATUS = "status"
        private const val CHANNEL_ALERT = "alert"

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, WatchdogService::class.java))
                LogStore.i(TAG, "看门狗服务启动请求")
            } catch (t: Throwable) {
                LogStore.e(TAG, "看门狗启动失败", t)
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, WatchdogService::class.java))
            } catch (_: Throwable) {
            }
        }
    }
}

/** 电池优化白名单检测与引导 */
object BatteryGuard {
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true
        }
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(context.packageName)
        } catch (t: Throwable) {
            LogStore.e("BatteryGuard", "电池状态查询失败", t)
            true
        }
    }

    fun requestIgnoreIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
}
