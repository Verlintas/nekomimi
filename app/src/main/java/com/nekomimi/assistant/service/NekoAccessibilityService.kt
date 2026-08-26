/*
 * 猫猫助手 (Nekomimi) — Android accessibility text-rewriting assistant
 * Copyright (C) 2026 Verlintas
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.nekomimi.assistant.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.nekomimi.assistant.engine.Config
import com.nekomimi.assistant.engine.ConfigStore
import com.nekomimi.assistant.engine.CircuitBreaker
import com.nekomimi.assistant.engine.TextProcessor
import com.nekomimi.assistant.log.CrashHandler
import com.nekomimi.assistant.log.LogStore
import java.util.Locale

/**
 * 猫猫助手无障碍服务（v2 重写版）。
 *
 * 相比 v1 的稳定性改进：
 *  - 轻量化事件处理：优先复用事件自带的 source 节点 + 节点缓存，全树扫描带节点数/深度预算，
 *    杜绝实时模式下每敲一个字全窗扫描导致的 ANR；
 *  - 全局异常兜底：事件/回调解入 try-catch，异常写入 LogStore（不崩溃、可排查）；
 *  - 实例句柄暴露给 WatchdogService 做健康检测（M4）。
 */
class NekoAccessibilityService : AccessibilityService() {

    private var cfg: Config = Config()

    /** 增量跟踪：用户原始输入（不含装饰）与上次写入的改写结果 */
    private var userOriginal = ""
    private var lastSet = ""
    private var processing = false
    private var lastWriteTime = 0L

    /** 提示词防护状态 */
    private var lastEmptyObservedTime = 0L
    private var lastEmptyPkg = ""
    private var sendResetUntil = 0L
    private var sendResetPkg = ""
    private var lastPlaceholderText = ""
    private var lastPlaceholderPkg = ""
    private var lastPlaceholderTime = 0L
    private var lastConfigReloadTime = 0L
    private var lastConfigEpoch = 0L

    /** 上次定位到的输入框节点缓存（包名 + 过期时间），节点访问异常自动失效重扫 */
    private var cachedInput: AccessibilityNodeInfo? = null
    private var cachedInputPkg = ""
    private var cachedInputTime = 0L

    /** 实时模式防抖定时器：输入停止 stableDelayMs 后自动处理（节点已失效，走缓存/扫描路径） */
    private val mainHandler = Handler(Looper.getMainLooper())
    private val debounceRunnable = object : Runnable {
        override fun run() {
            try {
                doProcess(null, false)
            } catch (t: Throwable) {
                LogStore.e(TAG, "防抖处理异常", t)
            }
        }
    }

    /** 熔断器：包异常风暴 / 写入风暴的自动修复防线 */
    private val circuitBreaker = CircuitBreaker()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        try {
            LogStore.init(this)
            CrashHandler.install(this)
            cfg = ConfigStore.load(this)
            setServiceInfo(
                AccessibilityServiceInfo().apply {
                    eventTypes = (AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                            or AccessibilityEvent.TYPE_VIEW_CLICKED
                            or AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED)
                    feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
                    // FLAG_DEFAULT 无公开常量（值 1）
                    flags = (0x00000001
                            or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                            or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS)
                    notificationTimeout = 100L
                    packageNames = null // 监听所有应用，作用范围由配置动态决定
                },
            )
            // 启动看门狗前台服务：保活 + 掉线检测 + 电池白名单引导
            WatchdogService.start(this)
            LogStore.i(
                TAG,
                "无障碍服务已连接：模式=${cfg.processingMode} 防抖=${cfg.stableDelayMs}ms 仅聚焦=${cfg.onlyProcessFocused} 追加=${cfg.enableAppend}/${cfg.appendText} 颜文字=${cfg.enableRandomEmoticon} 目标应用=${cfg.targetPackages.size} 排除=${cfg.excludePackages.size}",
            )
        } catch (t: Throwable) {
            LogStore.e(TAG, "onServiceConnected 异常", t)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        try {
            handleEvent(event)
        } catch (t: Throwable) {
            LogStore.e(TAG, "onAccessibilityEvent 异常", t)
        }
    }

    override fun onInterrupt() {
        processing = false
        mainHandler.removeCallbacks(debounceRunnable)
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        mainHandler.removeCallbacks(debounceRunnable)
        clearCachedInput()
        WatchdogService.stop(this)
        LogStore.i(TAG, "服务已断开 (onUnbind)")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        mainHandler.removeCallbacks(debounceRunnable)
        clearCachedInput()
        LogStore.i(TAG, "服务销毁 (onDestroy)")
        super.onDestroy()
    }

    // ==================== 事件处理 ====================

    private fun handleEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: ""
        if (pkg.isEmpty() || pkg == packageName) {
            return
        }
        if (ConfigStore.isPaused(this)) {
            // 暂停时取消挂起的防抖，避免暂停后仍改写一次
            processing = false
            mainHandler.removeCallbacks(debounceRunnable)
            return
        }
        val now = System.currentTimeMillis()
        // 每 5 秒或窗口切换时重载配置，保证 UI 保存后尽快生效
        if (lastConfigReloadTime == 0L || now - lastConfigReloadTime > 5000L) {
            cfg = ConfigStore.load(this)
            lastConfigReloadTime = now
            // 配置版本号变化（保存/切换 Profile）时重置增量跟踪，避免旧跟踪状态污染新配置
            val epoch = ConfigStore.configEpoch(this)
            if (epoch != lastConfigEpoch) {
                lastConfigEpoch = epoch
                resetTracking()
            }
        }
        if (!cfg.shouldHandlePackage(pkg)) {
            return
        }
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> onWindowChanged(now)
            AccessibilityEvent.TYPE_VIEW_CLICKED -> onViewClicked(event, pkg, now)
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> onTextChanged(event, pkg, now)
        }
    }

    /** 重置增量跟踪与节点缓存（窗口切换/配置切换/暂停时调用） */
    private fun resetTracking() {
        processing = false
        mainHandler.removeCallbacks(debounceRunnable)
        userOriginal = ""
        lastSet = ""
        lastWriteTime = 0L
        clearCachedInput()
    }

    private fun onWindowChanged(now: Long) {
        resetTracking()
        cfg = ConfigStore.load(this)
        lastConfigReloadTime = now
        lastConfigEpoch = ConfigStore.configEpoch(this)
    }

    private fun onViewClicked(event: AccessibilityEvent, pkg: String, now: Long) {
        val src = event.source
        if (src == null) {
            return
        }
        try {
            // 发送兜底仅对实时模式有意义（打字时不加颜文字，发送时补上）；
            // 标点模式每次触发已处理完毕，兜底只会造成"发送后又填充"。
            if (cfg.enableSendFallback
                && cfg.processingMode == Config.MODE_REALTIME
                && isSendButton(src)
            ) {
                sendResetUntil = now
                sendResetPkg = pkg
                doProcess(src, true, pkg)
            }
        } finally {
            src.recycle()
        }
    }

    private fun onTextChanged(event: AccessibilityEvent, pkg: String, now: Long) {
        val after = event.text?.firstOrNull()
        val afterEmpty = after.isNullOrEmpty()
        if (afterEmpty) {
            noteEmpty(pkg, now)
        } else if (isPlaceholderLike(after.toString(), pkg, now, allowTiming = true)) {
            LogStore.i(TAG, "事件级拦截疑似提示词: $after")
            rememberPlaceholder(after.toString(), pkg, now)
            return
        }
        val text = if (afterEmpty) readNodeText() else after.toString().trim()
        if (text.isEmpty()) {
            return
        }
        if (cfg.processingMode == Config.MODE_REALTIME) {
            // 流式输入防抖（定时器版）：每次文本变化重置计时器，输入停止 stableDelayMs 后处理；
            // 事件带文本且句末为标点 → 立即处理（句子已结束）；
            // afterEmpty（IME 组合中）绝不立即处理，避免写入打断输入法组合。
            mainHandler.removeCallbacks(debounceRunnable)
            if (cfg.stableDelayMs <= 0 || (!afterEmpty && isPunctuationEnding(text))) {
                processWithSource(event, afterEmpty)
            } else {
                mainHandler.postDelayed(debounceRunnable, cfg.stableDelayMs.toLong())
            }
        } else {
            // 标点触发模式：句末为标点才处理。
            // 事件无文本时走延迟处理（避免打断 IME 组合）。
            if (afterEmpty) {
                mainHandler.removeCallbacks(debounceRunnable)
                mainHandler.postDelayed(debounceRunnable, SAFE_DELAY_MS)
            } else if (isPunctuationEnding(text)) {
                processWithSource(event, false)
            }
        }
    }

    private fun processWithSource(event: AccessibilityEvent, nullSource: Boolean) {
        val src = if (nullSource) null else event.source
        try {
            doProcess(src, false, event.packageName?.toString() ?: "")
        } finally {
            src?.recycle()
        }
    }

    // ==================== 核心处理 ====================

    /** 事件无文本时，从聚焦输入框节点读取当前文本（部分应用的事件不带文本）。
     *  仅走缓存 + findFocus 快速路径，绝不触发全树扫描——空文本事件在 IME 组合期间
     *  频繁出现，全树扫描会导致 ANR。 */
    private fun readNodeText(): String {
        val node = resolveInputNodeFast() ?: return ""
        return try {
            node.text?.toString()?.trim() ?: ""
        } catch (_: Throwable) {
            ""
        } finally {
            node.recycle()
        }
    }

    /** 零遍历输入框定位：缓存命中优先，否则活动窗口 findFocus(FOCUS_INPUT) */
    private fun resolveInputNodeFast(): AccessibilityNodeInfo? {
        val now = System.currentTimeMillis()
        val cached = cachedInput
        if (cached != null && cachedInputPkg.isNotEmpty() && now - cachedInputTime < CACHE_TTL_MS) {
            try {
                if (isUsableForInput(cached, cfg)) {
                    return AccessibilityNodeInfo.obtain(cached)
                }
            } catch (_: Throwable) {
                clearCachedInput()
            }
        }
        val root = try {
            rootInActiveWindow
        } catch (_: Throwable) {
            null
        } ?: return null
        try {
            val wpkg = root.packageName?.toString() ?: ""
            if (wpkg.isEmpty() || wpkg == packageName || !cfg.shouldHandlePackage(wpkg)) {
                return null
            }
            val focused = try {
                root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            } catch (_: Throwable) {
                null
            } ?: return null
            if (isEditableNode(focused) && isUsableForInput(focused, cfg)) {
                val result = AccessibilityNodeInfo.obtain(focused)
                cacheInput(focused, wpkg)
                focused.recycle()
                return result
            }
            focused.recycle()
            return null
        } finally {
            root.recycle()
        }
    }

    private fun doProcess(eventSource: AccessibilityNodeInfo?, isSendClick: Boolean, expectedPkg: String = "") {
        if (processing) {
            return
        }
        // 熔断检查：包异常风暴/写入风暴期间跳过处理（自动修复，日志可查）
        if (circuitBreaker.isPackageBlocked(expectedPkg) || circuitBreaker.isWriteStormActive()) {
            return
        }
        processing = true
        val startMs = System.currentTimeMillis()
        var inp: AccessibilityNodeInfo? = null
        try {
            val now = System.currentTimeMillis()
            // 1) 优先复用事件 source 节点（零扫描）
            if (eventSource != null && isUsableForInput(eventSource, cfg)) {
                inp = AccessibilityNodeInfo.obtain(eventSource)
            }
            // 2) 缓存节点（带包名校验，防止误用其他应用的缓存）
            if (inp == null) {
                inp = resolveInputNode(expectedPkg)
            }
            if (inp == null) {
                return
            }
            val raw = inp.text?.toString()?.trim() ?: ""
            val inputPkg = expectedPkg.ifEmpty { currentInputPkg() }
            if (raw.isEmpty()) {
                noteEmpty(inputPkg, now)
                return
            }
            // 超长文本保护：跳过异常大的输入，防止极端内容拖垮事件线程
            if (raw.length > MAX_PROCESS_LENGTH) {
                return
            }
            if (isPlaceholderText(inp, raw) || placeholderHit(raw, inputPkg, now)) {
                LogStore.i(TAG, "处理级拦截疑似提示词: $raw")
                rememberPlaceholder(raw, inputPkg, now)
                return
            }
            // 写入回显跳过：我们自己写入后紧随的回显事件
            if (lastWriteTime > 0 && now - lastWriteTime < ECHO_WINDOW_MS && raw == lastSet) {
                return
            }
            // 发送兜底：文本已是我们写入的最终形式，不再重复写。
            // 微信等应用点发送时输入框可能尚未清空，重复写入会造成"发送后又填充一段"。
            if (isSendClick && raw == lastSet) {
                return
            }
            // 删除抑制：用户删掉了我们追加的装饰（"喵~"/颜文字）时，尊重删除、不补写，
            // 只重置跟踪状态（继续输入会正常恢复处理）。
            // 否则用户删掉装饰后防抖会立刻补回，形成"删了又补"的死循环。
            if (lastSet.isNotEmpty() && raw.length < lastSet.length
                && TextProcessor.stripDecorations(raw, cfg)
                == TextProcessor.stripDecorations(lastSet, cfg)
            ) {
                userOriginal = TextProcessor.stripDecorations(raw, cfg)
                lastSet = ""
                LogStore.i(TAG, "检测到删除装饰，跳过写回: $raw")
                return
            }
            // 增量跟踪用户原始输入
            userOriginal = when {
                lastSet.isEmpty() -> TextProcessor.stripDecorations(raw, cfg)
                raw.startsWith(lastSet) -> userOriginal + raw.substring(lastSet.length)
                else -> TextProcessor.stripDecorations(raw, cfg)
            }
            if (userOriginal.isBlank()) {
                return
            }
            // 实时模式打字时不追加随机颜文字（发送兜底时补上）
            val effective = if (cfg.processingMode == Config.MODE_REALTIME
                && cfg.enableRandomEmoticon && !isSendClick
            ) cfg.copy(enableRandomEmoticon = false) else cfg
            val target = TextProcessor.process(userOriginal, effective) ?: return
            if (target != raw) {
                LogStore.i(TAG, "改写: $raw -> $target")
                if (setText(inp, target)) {
                    lastSet = target
                    lastWriteTime = now
                    circuitBreaker.recordWriteSuccess()
                    circuitBreaker.resetPackage(expectedPkg)
                } else {
                    circuitBreaker.recordPackageFailure(expectedPkg)
                }
            } else {
                lastSet = target
                circuitBreaker.resetPackage(expectedPkg)
            }
        } catch (t: Throwable) {
            circuitBreaker.recordPackageFailure(expectedPkg)
            LogStore.e(TAG, "doProcess 异常", t)
        } finally {
            inp?.recycle()
            processing = false
            // 耗时监控：单次处理超过 200ms 记录（诊断 ANR 风险）
            val cost = System.currentTimeMillis() - startMs
            if (cost > 200) {
                LogStore.i(TAG, "doProcess 耗时 ${cost}ms")
            }
        }
    }

    // ==================== 节点解析（轻量化） ====================

    /** 当前事件包名（解析输入框时同步更新），用于提示词时序判定 */
    private fun currentInputPkg(): String = cachedInputPkg

    /**
     * 定位输入框：缓存命中（同包名 + TTL 内 + 节点仍可用）则零扫描返回；
     * 否则在应用窗口内做带预算的深度优先检索。
     */
    private fun resolveInputNode(expectedPkg: String = ""): AccessibilityNodeInfo? {
        val now = System.currentTimeMillis()
        val cached = cachedInput
        if (cached != null && cachedInputPkg.isNotEmpty()
            && (expectedPkg.isEmpty() || cachedInputPkg == expectedPkg)
            && now - cachedInputTime < CACHE_TTL_MS
        ) {
            try {
                if (isUsableForInput(cached, cfg)) {
                    return AccessibilityNodeInfo.obtain(cached)
                }
            } catch (_: Throwable) {
                clearCachedInput() // 节点已失效
            }
        }
        val scanStart = System.currentTimeMillis()
        val found = findEditableInAppWindows()
        val cost = System.currentTimeMillis() - scanStart
        if (found != null) {
            if (cost > 50) {
                LogStore.i(TAG, "输入框扫描命中，耗时 ${cost}ms")
            }
            return found
        }
        if (cost > 50) {
            LogStore.i(TAG, "输入框扫描未命中，耗时 ${cost}ms")
        }
        return null
    }

    /** 在非输入法、非覆盖层的应用窗口中查找可编辑输入框（带节点/深度预算） */
    private fun findEditableInAppWindows(): AccessibilityNodeInfo? {
        val scanStart = System.currentTimeMillis()
        val budget = intArrayOf(MAX_NODES)
        var windows = emptyList<AccessibilityWindowInfo>()
        try {
            windows = getWindows() ?: emptyList()
        } catch (_: Throwable) {
        }
        for (w in windows) {
            val type = try {
                w.type
            } catch (_: Throwable) {
                continue
            }
            if (type == AccessibilityWindowInfo.TYPE_INPUT_METHOD
                || type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY
            ) {
                continue
            }
            val root = try {
                w.root
            } catch (_: Throwable) {
                null
            }
            if (root == null) {
                continue
            }
            try {
                val wpkg = root.packageName?.toString() ?: ""
                if (wpkg.isEmpty() || wpkg == packageName || !cfg.shouldHandlePackage(wpkg)) {
                    continue
                }
                // 优先 findFocus(FOCUS_INPUT)：零遍历直接拿到聚焦输入框。
                // 微信/QQ 聊天页节点树动辄数千节点，全树遍历预算可能耗尽扫不到底部的输入框。
                val focused = try {
                    root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                } catch (_: Throwable) {
                    null
                }
                if (focused != null) {
                    if (isEditableNode(focused) && isUsableForInput(focused, cfg)) {
                        val result = AccessibilityNodeInfo.obtain(focused)
                        cacheInput(focused, wpkg)
                        focused.recycle()
                        return result
                    }
                    focused.recycle()
                }
                val found = findEditable(root, cfg.onlyProcessFocused, 0, budget)
                if (found != null && isUsableForInput(found, cfg)) {
                    val result = AccessibilityNodeInfo.obtain(found)
                    cacheInput(found, wpkg)
                    found.recycle()
                    return result
                }
                found?.recycle()
            } finally {
                root.recycle()
            }
            if (budget[0] <= 0) {
                break
            }
        }
        // 兜底：活动窗口（同样受作用范围约束，绝不误改输入法）
        val root = try {
            rootInActiveWindow
        } catch (_: Throwable) {
            null
        }
        if (root != null) {
            try {
                val wpkg = root.packageName?.toString() ?: ""
                if (wpkg.isNotEmpty() && wpkg != packageName && cfg.shouldHandlePackage(wpkg)) {
                    val found = findEditable(root, cfg.onlyProcessFocused, 0, budget)
                    if (found != null && isUsableForInput(found, cfg)) {
                        val result = AccessibilityNodeInfo.obtain(found)
                        cacheInput(found, wpkg)
                        found.recycle()
                        return result
                    }
                    found?.recycle()
                }
            } finally {
                root.recycle()
            }
        }
        logScanMiss(System.currentTimeMillis() - scanStart)
        return null
    }

    /** 检索失败诊断日志（10 秒节流），便于排查"某应用不生效"的原因 */
    private var lastScanLogTime = 0L
    private fun logScanMiss(costMs: Long) {
        val now = System.currentTimeMillis()
        if (now - lastScanLogTime < 10_000) {
            return
        }
        lastScanLogTime = now
        LogStore.w(
            TAG,
            "未找到输入框（耗时 ${costMs}ms；仅处理聚焦=${cfg.onlyProcessFocused}，目标=${cfg.targetPackages.size} 个包名）——若目标应用持续不生效，请关闭设置里的「仅处理聚焦的输入框」试试",
        )
    }

    /** 深度优先查找可编辑节点：isEditable 优先，类名兜底；跳过密码框；受节点数/深度预算约束 */
    private fun findEditable(
        n: AccessibilityNodeInfo,
        focusedOnly: Boolean,
        depth: Int,
        budget: IntArray,
    ): AccessibilityNodeInfo? {
        if (n == null || depth > MAX_DEPTH || budget[0] <= 0) {
            return null
        }
        budget[0]--
        if (isEditableNode(n) && (!focusedOnly || isFocusedSafe(n))) {
            return AccessibilityNodeInfo.obtain(n)
        }
        val count = try {
            n.childCount
        } catch (_: Throwable) {
            0
        }
        for (i in 0 until count) {
            val child = try {
                n.getChild(i)
            } catch (_: Throwable) {
                null
            }
            if (child != null) {
                val r = findEditable(child, focusedOnly, depth + 1, budget)
                child.recycle()
                if (r != null) {
                    return r
                }
            }
            if (budget[0] <= 0) {
                break
            }
        }
        return null
    }

    private fun cacheInput(node: AccessibilityNodeInfo, pkg: String) {
        clearCachedInput()
        cachedInput = AccessibilityNodeInfo.obtain(node)
        cachedInputPkg = pkg
        cachedInputTime = System.currentTimeMillis()
    }

    private fun clearCachedInput() {
        cachedInput?.recycle()
        cachedInput = null
        cachedInputPkg = ""
        cachedInputTime = 0L
    }

    // ==================== 节点判定 ====================

    private fun isUsableForInput(n: AccessibilityNodeInfo, cfg: Config): Boolean {
        return try {
            if (n == null || n.isPassword) {
                false
            } else if (cfg.onlyProcessFocused && !n.isFocused) {
                false
            } else {
                true
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun isEditableNode(n: AccessibilityNodeInfo): Boolean {
        return try {
            if (n.isPassword) {
                false
            }
            if (n.isEditable) {
                true
            } else {
                val cls = n.className?.toString() ?: ""
                // 放宽到包含 Edit/TextInput/TextField/Editor：覆盖微信 MMEditText、
                // QQ AIOEditText 等自定义输入控件（isEditable 未上报时靠类名兜底）
                cls.contains("Edit") || cls.contains("TextInput") || cls.contains("TextField")
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun isFocusedSafe(n: AccessibilityNodeInfo): Boolean = try {
        n.isFocused
    } catch (_: Throwable) {
        false
    }

    private fun isPunctuationEnding(s: String): Boolean {
        if (s.isEmpty()) {
            return false
        }
        return when (s.last()) {
            '。', '！', '!', '？', '?', ' ' -> true
            else -> false
        }
    }

    // ==================== 提示词防护 ====================

    private fun noteEmpty(pkg: String, now: Long) {
        lastEmptyObservedTime = now
        lastEmptyPkg = pkg
    }

    private fun rememberPlaceholder(text: String, pkg: String, now: Long) {
        lastPlaceholderText = text
        lastPlaceholderPkg = pkg
        lastPlaceholderTime = now
    }

    /**
     * 综合判定：记忆匹配（10s 内拦截过的同文本）或模式库命中。
     * 时序信号（刚清空/刚发送 3s 内）不再单独拦截——否则微信/QQ 等正常聊天
     * 发送后 3 秒内打新消息会被全部误拦（用户实测"微信没反应"的主因）。
     * 时序信号仅作为置信度增强：配合模式库命中才拦截。
     */
    private fun isPlaceholderLike(text: String, pkg: String, now: Long, allowTiming: Boolean): Boolean {
        val patternHit = isPlaceholderPattern(text)
        if (allowTiming && patternHit) {
            val recentEmpty = pkg == lastEmptyPkg && now - lastEmptyObservedTime < EMPTY_WINDOW_MS
            val recentSend = pkg == sendResetPkg && now < sendResetUntil + EMPTY_WINDOW_MS
            if (recentEmpty || recentSend) {
                return true
            }
        }
        return isPlaceholderMemory(text, pkg, now) || patternHit
    }

    private fun isPlaceholderMemory(text: String, pkg: String, now: Long): Boolean {
        val t = text.trim()
        if (t.length < 2 || t.length > 40) {
            return false
        }
        return pkg == lastPlaceholderPkg
                && now - lastPlaceholderTime < PLACEHOLDER_MEMORY_MS
                && t == lastPlaceholderText
    }

    private fun isPlaceholderPattern(text: String): Boolean {
        val t = text.trim()
        if (t.length < 2 || t.length > 40) {
            return false
        }
        return PLACEHOLDER_PATTERNS.any { t.contains(it) }
    }

    /** 处理级兜底：只认记忆匹配或「模式命中 + 输入框刚被清空」 */
    private fun placeholderHit(raw: String, pkg: String, now: Long): Boolean {
        val memory = isPlaceholderMemory(raw, pkg, now)
        val patternAfterEmpty = isPlaceholderPattern(raw)
                && pkg == lastEmptyPkg
                && now - lastEmptyObservedTime < EMPTY_WINDOW_MS
        return memory || patternAfterEmpty
    }

    /** 节点文本与其 hint（提示词）一致时视为占位文本 */
    private fun isPlaceholderText(n: AccessibilityNodeInfo, text: String): Boolean {
        if (text.isBlank()) {
            return false
        }
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                val hint = n.hintText
                if (hint != null && hint.isNotEmpty() && text.trim() == hint.toString().trim()) {
                    return true
                }
            } catch (_: Throwable) {
            }
        }
        return false
    }

    // ==================== 写回 / 发送识别 ====================

    /** 通过无障碍 ACTION_SET_TEXT 写回文本，并把光标移到末尾 */
    private fun setText(n: AccessibilityNodeInfo, text: String): Boolean {
        return try {
            val b = Bundle()
            b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            val ok = n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)
            if (ok) {
                val a = Bundle()
                a.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, text.length)
                a.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, text.length)
                n.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, a)
            } else {
                logWriteFail()
            }
            ok
        } catch (_: Throwable) {
            logWriteFail()
            false
        }
    }

    /** 写回失败日志（10 秒节流）——部分应用（如个别版本微信）不支持 ACTION_SET_TEXT 时是静默失败 */
    private var lastWriteFailTime = 0L
    private fun logWriteFail() {
        val now = System.currentTimeMillis()
        if (now - lastWriteFailTime < 10_000) {
            return
        }
        lastWriteFailTime = now
        LogStore.w(TAG, "ACTION_SET_TEXT 写回失败（应用可能不支持该无障碍操作，无法改写）")
    }

    /** 通用发送按钮识别：可点击、非输入框、类名像按钮、文本/描述含发送关键词 */
    private fun isSendButton(n: AccessibilityNodeInfo): Boolean {
        return try {
            if (n.isEditable || !n.isClickable) {
                return false
            }
            val cls = n.className?.toString() ?: ""
            if (!cls.contains("Button") && !cls.contains("Image") && !cls.contains("TextView")) {
                return false
            }
            val t = n.text?.toString() ?: ""
            val d = n.contentDescription?.toString() ?: ""
            val s = (t + " " + d).lowercase(Locale.ROOT)
            if (s.isBlank()) {
                return false
            }
            SEND_KEYWORDS.any { s.contains(it) }
        } catch (_: Throwable) {
            false
        }
    }

    companion object {
        private const val TAG = "NekoSvc"
        private const val CACHE_TTL_MS = 3000L
        private const val ECHO_WINDOW_MS = 600L
        private const val EMPTY_WINDOW_MS = 3000L
        private const val PLACEHOLDER_MEMORY_MS = 10000L
        private const val MAX_NODES = 20000
        private const val MAX_DEPTH = 60
        /** 单次处理的最大文本长度（防极端输入拖垮事件线程） */
        private const val MAX_PROCESS_LENGTH = 2000
        /** 事件无文本时的延迟处理间隔（避免打断 IME 组合） */
        private const val SAFE_DELAY_MS = 300L

        private val SEND_KEYWORDS = arrayOf(
            "发送", "送出", "提交", "发表", "发布", "回复", "评论",
            "send", "submit", "enter", "post", "reply", "comment", "ok", "done", "➤",
        )

        private val PLACEHOLDER_PATTERNS = arrayOf(
            "说点什么", "说两句", "说点啥", "输入消息", "写评论", "添加评论",
            "发个友善的", "善语结善缘", "请输入", "评论一下", "留下你的",
            "说说你的", "留言", "吐槽一下", "回复一下", "讲两句", "想说什么",
        )

        @Volatile
        var instance: NekoAccessibilityService? = null
            private set

        /** 健康检测（WatchdogService 使用） */
        fun isRunning(): Boolean = instance != null
    }
}
