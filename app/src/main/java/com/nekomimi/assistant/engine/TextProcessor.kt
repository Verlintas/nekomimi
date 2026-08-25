package com.nekomimi.assistant.engine

import kotlin.random.Random

/**
 * 文本改写引擎（纯 Kotlin，JVM 可测）：
 * 1. 按顺序应用替换规则（字面 / 正则 / 随机）
 * 2. 断句追加（按 [，,。！!？?\s]+ 分句，句末追加自定义文本）
 * 3. 句末附加随机颜文字
 *
 * 另提供 stripDecorations：从已改写文本中剥离追加文本与颜文字，
 * 用于增量跟踪用户原始输入（用户继续输入时恢复原文再重新改写）。
 */
object TextProcessor {
    private val RANDOM = Random.Default
    private val SENTENCE_SPLIT = Regex("([，,。！!？?\\s]+)")

    fun process(original: String?, config: Config): String? {
        if (original == null) {
            return null
        }
        if (original.isBlank()) {
            return original
        }
        var text = original.trim()
        for (rule in config.rules) {
            if (rule.from.isEmpty() || rule.candidates.isEmpty()) {
                continue
            }
            text = when (rule.mode) {
                RuleMode.LITERAL -> replaceLiteral(text, rule)
                RuleMode.REGEX -> replaceRegex(text, rule)
            }
        }
        if (config.enableAppend) {
            text = appendPerSentence(text, appendCandidates(config.appendText))
        }
        if (config.enableRandomEmoticon) {
            val em = pick(config.activeEmoticons)
            if (em != null) {
                text = "$text $em"
            }
        }
        // 动态占位符展开：{time} {date} {week} {random} 等（规则/追加文本里都可用）
        return PlaceholderExpander.expand(text)
    }

    /** 校验规则是否可用（UI 诊断用），返回错误描述；null = 可用 */
    fun validateRule(rule: Rule): String? {
        if (rule.from.isEmpty()) {
            return "原词为空"
        }
        if (rule.candidates.isEmpty()) {
            return "替换词为空"
        }
        if (rule.mode == RuleMode.REGEX) {
            try {
                Regex(rule.from)
            } catch (e: Exception) {
                return "正则无效: ${e.message}"
            }
        }
        return null
    }

    private fun replaceLiteral(text: String, rule: Rule): String {
        if (rule.candidates.size == 1) {
            return text.replace(rule.from, rule.candidates[0])
        }
        val pattern = Regex(Regex.escape(rule.from))
        return pattern.replace(text) { pick(rule.candidates) ?: "" }
    }

    private fun replaceRegex(text: String, rule: Rule): String {
        val pattern = try {
            java.util.regex.Pattern.compile(rule.from)
        } catch (e: Exception) {
            return text // 无效正则：跳过该规则
        }
        val matcher = pattern.matcher(text)
        val sb = StringBuffer()
        while (matcher.find()) {
            val chosen = pick(rule.candidates) ?: ""
            matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(expandBackrefs(matcher, chosen)))
        }
        matcher.appendTail(sb)
        return sb.toString()
    }

    /** 解析替换模板中的 $1 / ${1} 反向引用（其余 $ 按字面输出，$$ 转义为字面 $） */
    private fun expandBackrefs(m: java.util.regex.Matcher, template: String): String {
        if ('$' !in template) {
            return template
        }
        val sb = StringBuilder()
        var i = 0
        while (i < template.length) {
            val c = template[i]
            if (c == '$' && i + 1 < template.length) {
                val next = template[i + 1]
                if (next == '$') {
                    sb.append('$')
                    i += 2
                    continue
                }
                if (next == '{') {
                    val close = template.indexOf('}', i + 2)
                    if (close > 0) {
                        val g = template.substring(i + 2, close).toIntOrNull()
                        if (g != null && g > 0 && g <= m.groupCount()) {
                            sb.append(m.group(g) ?: "")
                            i = close + 1
                            continue
                        }
                    }
                } else if (next.isDigit()) {
                    var j = i + 1
                    while (j < template.length && template[j].isDigit()) {
                        j++
                    }
                    val g = template.substring(i + 1, j).toInt()
                    if (g > 0 && g <= m.groupCount()) {
                        sb.append(m.group(g) ?: "")
                        i = j
                        continue
                    }
                }
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    /** 追加文本解析：含 | 时按随机候选处理（喵|喵呜|喵喵），否则按字面 */
    private fun appendCandidates(appendText: String): List<String> {
        if ('|' !in appendText) {
            return listOf(appendText)
        }
        return appendText.split("|").map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun appendPerSentence(text: String, candidates: List<String>): String {
        val suffix = candidates.firstOrNull { it.isNotEmpty() } ?: return text
        val parts = mutableListOf<String>()
        val seps = mutableListOf<String>()
        var lastEnd = 0
        for (m in SENTENCE_SPLIT.findAll(text)) {
            parts.add(text.substring(lastEnd, m.range.first))
            seps.add(m.value)
            lastEnd = m.range.last + 1
        }
        if (lastEnd < text.length) {
            parts.add(text.substring(lastEnd))
        } else if (parts.isNotEmpty() && lastEnd == text.length) {
            parts.add("")
        }
        if (parts.isEmpty()) {
            parts.add(text)
        }
        val sb = StringBuilder()
        for (i in parts.indices) {
            val part = parts[i].trim()
            if (part.isNotEmpty()) {
                // 多候选时每句随机选一个
                sb.append(part).append(pick(candidates) ?: suffix)
            }
            if (i < seps.size) {
                sb.append(seps[i])
            }
        }
        val result = sb.toString().trim()
        return if (result.isEmpty()) text + suffix else result
    }

    /**
     * 从已改写文本中剥离装饰（颜文字 + 断句追加文本），尽量还原用户原始输入。
     * 启发式：仅用于增量跟踪兜底；常规路径用前缀差分即可精确还原。
     */
    fun stripDecorations(text: String, config: Config): String {
        var result = text
        for (em in config.activeEmoticons.sortedByDescending { it.length }) {
            if (em.isEmpty()) {
                continue
            }
            result = result.replace(Regex(" *" + Regex.escape(em)), "")
        }
        val appendCands = appendCandidates(config.appendText)
        if (appendCands.isNotEmpty() && config.enableAppend) {
            // 追加文本的特征：紧跟句子内容之后，且后面紧跟标点/空白/结尾；
            // 多候选（喵|喵呜）时逐个剥离
            val pattern = Regex("(?:" + appendCands.joinToString("|") { Regex.escape(it) } + ")(?=[，,。！!？?\\s]|$)")
            result = pattern.replace(result, "")
        }
        return result.trim()
    }

    private fun pick(list: List<String>): String? =
        if (list.isEmpty()) null else list[RANDOM.nextInt(list.size)]
}
