package com.nekomimi.assistant.engine

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/**
 * 动态占位符展开（纯 Kotlin，JVM 可测）。
 *
 * 支持：
 *  - {time}         当前时间 HH:mm
 *  - {time:格式}     自定义时间格式（Java SimpleDateFormat 语法，如 {time:HH:mm:ss}）
 *  - {date}         当前日期 M月d日
 *  - {week}         星期X
 *  - {random}       0~99 随机数
 *  - {random:a-b}   a~b 随机数（含两端）
 *
 * 未知占位符原样保留。
 */
object PlaceholderExpander {
    private val RANDOM = Random.Default
    private val PATTERN = Regex("\\{([a-zA-Z]+)(?::([^}]+))?\\}")

    fun expand(text: String, now: Long = System.currentTimeMillis()): String {
        if ('{' !in text) {
            return text
        }
        val date = Date(now)
        return PATTERN.replace(text) { m ->
            val name = m.groupValues[1].lowercase(Locale.ROOT)
            val arg = m.groupValues[2].takeIf { it.isNotEmpty() }
            when (name) {
                "time" -> arg?.let { tryFormat(it, date) } ?: SimpleDateFormat("HH:mm", Locale.US).format(date)
                "date" -> SimpleDateFormat("M月d日", Locale.CHINA).format(date)
                "week" -> "星期" + SimpleDateFormat("E", Locale.CHINA).format(date).removePrefix("周") // 星期X
                "random" -> randomIn(arg)
                else -> m.value // 未知占位符保留原样
            }
        }
    }

    private fun tryFormat(pattern: String, date: Date): String = try {
        SimpleDateFormat(pattern, Locale.US).format(date)
    } catch (_: IllegalArgumentException) {
        SimpleDateFormat("HH:mm", Locale.US).format(date)
    }

    /** {random} → 0~99；{random:a-b} → a~b（含两端，区间顺序任意） */
    private fun randomIn(arg: String?): String {
        if (arg != null) {
            val parts = arg.split("-")
            if (parts.size == 2) {
                val x = parts[0].toIntOrNull()
                val y = parts[1].toIntOrNull()
                if (x != null && y != null && x != y) {
                    val lo = minOf(x, y)
                    val hi = maxOf(x, y)
                    return (lo + RANDOM.nextInt(hi - lo + 1)).toString()
                }
            }
        }
        return RANDOM.nextInt(100).toString()
    }
}
