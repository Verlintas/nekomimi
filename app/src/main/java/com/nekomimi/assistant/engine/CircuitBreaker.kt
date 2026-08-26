/*
 * 猫猫助手 (Nekomimi) — Android accessibility text-rewriting assistant
 * Copyright (C) 2026 Verlintas
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.nekomimi.assistant.engine

/**
 * 熔断器（纯 Kotlin，可注入时钟单测）——防异常风暴与写入死循环的冗余防线。
 *
 * 1. 包熔断：同一应用短时间连续处理异常达阈值 → 熔断 30 分钟跳过该应用（防崩溃风暴，
 *    也防某个应用每次事件都抛异常拖垮事件线程）。
 * 2. 写入风暴熔断：短时间连续写入达阈值 → 暂停处理 30 秒（防极端情况下改写死循环刷屏）。
 */
class CircuitBreaker(private val nowMs: () -> Long = System::currentTimeMillis) {

    private val pkgFailures = HashMap<String, MutableList<Long>>()
    private val pkgTrippedUntil = HashMap<String, Long>()
    private val writeTimes = ArrayDeque<Long>()
    private var stormTrippedUntil = 0L

    /** 该包是否处于熔断中（跳过处理） */
    fun isPackageBlocked(pkg: String): Boolean {
        if (pkg.isEmpty()) {
            return false
        }
        return nowMs() < (pkgTrippedUntil[pkg] ?: 0L)
    }

    /** 记录一次处理异常；窗口内达阈值自动熔断该包 */
    fun recordPackageFailure(pkg: String) {
        if (pkg.isEmpty()) {
            return
        }
        val now = nowMs()
        val list = pkgFailures.getOrPut(pkg) { mutableListOf() }
        list.add(now)
        list.removeAll { now - it > FAILURE_WINDOW_MS }
        if (list.size >= MAX_PACKAGE_FAILURES) {
            pkgTrippedUntil[pkg] = now + PACKAGE_TRIP_MS
            pkgFailures.remove(pkg)
        }
    }

    /** 处理成功：清零该包失败计数 */
    fun resetPackage(pkg: String) {
        if (pkg.isNotEmpty()) {
            pkgFailures.remove(pkg)
        }
    }

    /** 是否处于写入风暴熔断中 */
    fun isWriteStormActive(): Boolean = nowMs() < stormTrippedUntil

    /** 记录一次成功写入；短时间写入过多自动熔断（短暂暂停） */
    fun recordWriteSuccess() {
        val now = nowMs()
        writeTimes.addLast(now)
        while (writeTimes.isNotEmpty() && now - writeTimes.first() > WRITE_WINDOW_MS) {
            writeTimes.removeFirst()
        }
        if (writeTimes.size >= MAX_WRITES_PER_WINDOW) {
            stormTrippedUntil = now + STORM_TRIP_MS
            writeTimes.clear()
        }
    }

    /** 熔断状态快照（日志/调试用） */
    fun blockedPackages(): List<String> {
        val now = nowMs()
        return pkgTrippedUntil.filter { now < it.value }.keys.sorted()
    }

    companion object {
        const val FAILURE_WINDOW_MS = 60_000L
        const val MAX_PACKAGE_FAILURES = 5
        const val PACKAGE_TRIP_MS = 30 * 60_000L
        const val WRITE_WINDOW_MS = 3_000L
        const val MAX_WRITES_PER_WINDOW = 6
        const val STORM_TRIP_MS = 30_000L
    }
}
