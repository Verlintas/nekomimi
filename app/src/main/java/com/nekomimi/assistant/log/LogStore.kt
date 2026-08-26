/*
 * 猫猫助手 (Nekomimi) — Android accessibility text-rewriting assistant
 * Copyright (C) 2026 Verlintas
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.nekomimi.assistant.log

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 滚动日志存储：内存环形缓冲 + 文件落盘。
 * 用于排查无障碍服务掉线/异常；UI 内可查看与导出。
 */
object LogStore {
    private const val MAX_ENTRIES = 500
    private const val LOG_FILE = "neko_log.txt"
    private const val MAX_FILE_BYTES = 1_000_000L

    private val buffer = ArrayDeque<String>()
    private var file: File? = null
    private val lock = Any()

    private val timestamp = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    fun init(context: Context) {
        synchronized(lock) {
            if (file == null) {
                file = File(context.filesDir, LOG_FILE)
            }
        }
    }

    fun i(tag: String, msg: String) {
        log("I", tag, msg, null)
    }

    fun w(tag: String, msg: String) {
        log("W", tag, msg, null)
    }

    fun e(tag: String, msg: String, t: Throwable? = null) {
        log("E", tag, msg, t)
    }

    private fun log(level: String, tag: String, msg: String, t: Throwable?) {
        val sb = StringBuilder()
        sb.append(timestamp.format(Date())).append(' ').append(level).append('/').append(tag)
            .append(": ").append(msg)
        if (t != null) {
            sb.append('\n').append(t.stackTraceToString())
        }
        val line = sb.toString()
        synchronized(lock) {
            buffer.addLast(line)
            while (buffer.size > MAX_ENTRIES) {
                buffer.removeFirst()
            }
            appendToFile(line)
        }
        android.util.Log.println(levelToAndroid(level), tag, msg)
    }

    private fun levelToAndroid(level: String): Int = when (level) {
        "E" -> android.util.Log.ERROR
        "W" -> android.util.Log.WARN
        else -> android.util.Log.INFO
    }

    private fun appendToFile(line: String) {
        val f = file ?: return
        try {
            f.parentFile?.mkdirs()
            // 日志轮转：超过 1MB 直接重建，防止文件无限增长
            if (f.length() > MAX_FILE_BYTES) {
                f.delete()
            }
            FileOutputStream(f, true).use { fos ->
                PrintWriter(fos.writer(Charsets.UTF_8), true).use { pw ->
                    pw.println(line)
                }
            }
        } catch (e: Exception) {
            // 日志写失败不阻断主流程
        }
    }

    fun entries(): List<String> = synchronized(lock) { buffer.toList() }

    fun clear() {
        synchronized(lock) {
            buffer.clear()
            file?.delete()
        }
    }

    fun logFile(context: Context): File {
        init(context)
        return File(context.filesDir, LOG_FILE)
    }
}
