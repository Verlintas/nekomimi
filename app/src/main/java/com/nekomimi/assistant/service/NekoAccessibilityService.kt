package com.nekomimi.assistant.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.nekomimi.assistant.engine.Config
import com.nekomimi.assistant.engine.ConfigStore
import com.nekomimi.assistant.engine.RuleMode
import com.nekomimi.assistant.engine.TextProcessor
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
    private var lastTextChangeTime = 0L

    /** 提示词防护状态 */
    private var lastEmptyObservedTime = 0L
    private var lastEmptyPkg = ""
    private var sendResetUntil = 0L
    private var sendResetPkg = ""
    private var lastPlaceholderText = ""
    private var lastPlaceholderPkg = ""
    private var lastPlaceholderTime = 0L
    private var lastConfigReloadTime = 0L

    /** 上次定位到的输入框节点缓存（包名 + 过期时间），节点访问异常自动失效重扫 */
    private var cachedInput: AccessibilityNodeInfo? = null
    private var cachedInputPkg = ""
    private var cachedInputTime = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        try {
            LogStore.init(this)
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
            LogStore.i(TAG, "无障碍服务已连接")
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
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        clearCachedInput()
        WatchdogService.stop(this)
        LogStore.i(TAG, "服务已断开 (onUnbind)")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        clearCachedInput()
        LogStore.i(TAG, "服务销毁 (onDestroy)")
        super.onDestroy()
    }

    // ==================== 事件处理 ====================

    private fun handleEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: ""
        if (pkg.isEmpty() || pkg == packageName || ConfigStore.isPaused(this)) {
            return
        }
        val now = System.currentTimeMillis()
        // 每 5 秒或窗口切换时重载配置，保证 UI 保存后尽快生效
        if (lastConfigReloadTime == 0L || now - lastConfigReloadTime > 5000L) {
            cfg = ConfigStore.load(this)
            lastConfigReloadTime = now
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

    private fun onWindowChanged(now: Long) {
        processing = false
        userOriginal = ""
        lastSet = ""
        lastWriteTime = 0L
        lastTextChangeTime = now
        cfg = ConfigStore.load(this)
        lastConfigReloadTime = now
        clearCachedInput()
    }

    private fun onViewClicked(event: AccessibilityEvent, pkg: String, now: Long) {
        val src = event.source
        if (src == null) {
            return
        }
        try {
            if (cfg.enableSendFallback && isSendButton(src)) {
                sendResetUntil = now
                sendResetPkg = pkg
                doProcess(src, true)
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
        if (cfg.processingMode == Config.MODE_REALTIME) {
            // 流式输入防抖：连续变化间隔小于稳定阈值视为未成型
            val gap = now - lastTextChangeTime
            lastTextChangeTime = now
            if (cfg.stableDelayMs > 0 && gap < cfg.stableDelayMs) {
                return
            }
            val src = event.source
            try {
                doProcess(src, false)
            } finally {
                src?.recycle()
            }
        } else {
            // 标点触发模式：句末为标点才处理
            val text = after?.toString()?.trim() ?: return
            if (isPunctuationEnding(text)) {
                val src = event.source
                try {
                    doProcess(src, false)
                } finally {
                    src?.recycle()
                }
            }
        }
    }

    // ==================== 核心处理 ====================

    private fun doProcess(eventSource: AccessibilityNodeInfo?, isSendClick: Boolean) {
        if (processing) {
            return
        }
        processing = true
        var inp: AccessibilityNodeInfo? = null
        try {
            val now = System.currentTimeMillis()
            // 1) 优先复用事件 source 节点（零扫描）
            if (eventSource != null && isUsableForInput(eventSource, cfg)) {
                inp = AccessibilityNodeInfo.obtain(eventSource)
            }
            // 2) 缓存节点
            if (inp == null) {
                inp = resolveInputNode()
            }
            if (inp == null) {
                return
            }
            val raw = inp.text?.toString()?.trim() ?: ""
            if (raw.isEmpty()) {
                noteEmpty(currentInputPkg(), now)
                return
            }
            if (isPlaceholderText(inp, raw) || placeholderHit(raw, currentInputPkg(), now)) {
                LogStore.i(TAG, "处理级拦截疑似提示词: $raw")
                rememberPlaceholder(raw, currentInputPkg(), now)
                return
            }
            // 写入回显跳过：我们自己写入后紧随的回显事件
            if (lastWriteTime > 0 && now - lastWriteTime < ECHO_WINDOW_MS && raw == lastSet) {
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
                }
            } else {
                lastSet = target
            }
        } catch (t: Throwable) {
            LogStore.e(TAG, "doProcess 异常", t)
        } finally {
            inp?.recycle()
            processing = false
        }
    }

    // ==================== 节点解析（轻量化） ====================

    /** 当前事件包名（解析输入框时同步更新），用于提示词时序判定 */
    private fun currentInputPkg(): String = cachedInputPkg

    /**
     * 定位输入框：缓存命中（同包名 + TTL 内 + 节点仍可用）则零扫描返回；
     * 否则在应用窗口内做带预算的深度优先检索。
     */
    private fun resolveInputNode(): AccessibilityNodeInfo? {
        val now = System.currentTimeMillis()
        val cached = cachedInput
        if (cached != null && cachedInputPkg.isNotEmpty() && now - cachedInputTime < CACHE_TTL_MS) {
            try {
                if (isUsableForInput(cached, cfg)) {
                    return AccessibilityNodeInfo.obtain(cached)
                }
            } catch (_: Throwable) {
                clearCachedInput() // 节点已失效
            }
        }
        val found = findEditableInAppWindows()
        if (found != null) {
            return found
        }
        return null
    }

    /** 在非输入法、非覆盖层的应用窗口中查找可编辑输入框（带节点/深度预算） */
    private fun findEditableInAppWindows(): AccessibilityNodeInfo? {
        val budget = intArrayOf(MAX_NODES)
        var windows = emptyList<AccessibilityWindowInfo>()
        try {
            windows = getWindows() ?: emptyList()
        } catch (_: Throwable) {
        }
        for (w in windows) {
            if (w == null) {
                continue
            }
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
        return null
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
                cls.contains("EditText") || cls.contains("TextInput") || cls.contains("TextField")
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

    /** 综合判定：时序信号（刚清空/刚发送 3s 内）+ 记忆匹配（10s）+ 模式库 */
    private fun isPlaceholderLike(text: String, pkg: String, now: Long, allowTiming: Boolean): Boolean {
        if (allowTiming) {
            val recentEmpty = pkg == lastEmptyPkg && now - lastEmptyObservedTime < EMPTY_WINDOW_MS
            val recentSend = pkg == sendResetPkg && now < sendResetUntil + EMPTY_WINDOW_MS
            if (recentEmpty || recentSend) {
                return true
            }
        }
        return isPlaceholderMemory(text, pkg, now) || isPlaceholderPattern(text)
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
            }
            ok
        } catch (_: Throwable) {
            false
        }
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
        private const val MAX_NODES = 3000
        private const val MAX_DEPTH = 50

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
