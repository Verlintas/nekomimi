package com.nekomimi.assistant.log

import android.content.Context

/**
 * 全局未捕获异常捕获：崩溃前把堆栈写入 LogStore（滚动日志 + 文件），
 * 然后交还系统默认处理器（不吞异常，保持系统崩溃行为）。
 * 长期运行排查必备：崩溃不再无痕。
 */
object CrashHandler {
    private var installed = false

    fun install(context: Context) {
        if (installed) {
            return
        }
        installed = true
        LogStore.init(context)
        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                LogStore.e("Crash", "未捕获异常 @${thread.name}", throwable)
            } catch (_: Throwable) {
            }
            default?.uncaughtException(thread, throwable)
        }
    }
}
