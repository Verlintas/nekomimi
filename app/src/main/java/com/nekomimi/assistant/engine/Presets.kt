package com.nekomimi.assistant.engine

/**
 * 预设风格包与净化词库（纯 Kotlin，可单测）。
 * 一键导入为替换规则；规则文本格式与规则编辑框一致（re: 前缀/|随机候选/注释均支持）。
 */
object Presets {

    data class StylePack(
        val name: String,
        val description: String,
        val rulesText: String,
    )

    /** 萌系风格：本喵/主人/喵语气词 */
    val MENG_RULES = """
        # 萌系风格
        我=本喵
        你=主人
        谢谢=谢谢喵
        好的=好哒
        晚安=晚安喵
        早安=早安喵
        知道了=知道啦喵
        对不起=喵呜抱歉
        再见=拜拜喵
        喜欢=超喜欢
    """.trimIndent()

    /** 傲娇风格：嘴硬心软 */
    val TSUNDERE_RULES = """
        # 傲娇风格
        我=本大爷
        才不要=才、才不是想要
        谢谢=哼，勉强道谢
        对不起=哼，原谅你了
        喜欢=才不是喜欢你
        知道了=知道了啦，别啰嗦
        好的=哼，随便你
        晚安=晚安啦笨蛋
        早安=早、早安
        可爱=哼，一点都不
    """.trimIndent()

    /** 吐槽风格：互联网冲浪人设 */
    val ROAST_RULES = """
        # 吐槽风格
        好的=emmm 行吧
        不会=不会吧不会吧
        真的=就这？
        厉害=牛蛙牛蛙
        知道了=6
        好的呢=懂
        没错=确实
        厉害啊=这波操作可以
        谢谢=栓Q
        再见=退！退！退！
    """.trimIndent()

    /** 脏话净化：骂人词替换为无害猫语 */
    val CLEAN_RULES = """
        # 脏话净化（温和版）
        草=喵
        操=喵
        靠=喵
        妈的=喵的
        特么=喵喵
        卧槽=哇喵
        傻逼=小笨蛋
        煞笔=小笨蛋
        笨蛋=小傻瓜
        你妈=你家猫
        去死=去玩
        滚=走开喵
        神经病=脑回路清奇
        有病=有意思
        恶心=不想看
        恶心死了=辣眼睛
        废物=小废物
        垃圾=小垃圾
    """.trimIndent()

    val PACKS = listOf(
        StylePack("萌系", "本喵/主人/句句带喵", MENG_RULES),
        StylePack("傲娇", "才不是喜欢你", TSUNDERE_RULES),
        StylePack("吐槽", "就这？确实。", ROAST_RULES),
        StylePack("脏话净化", "骂人自动变喵语", CLEAN_RULES),
    )

    /** 校验预设文本可解析，返回有效规则数（UI 显示用） */
    fun validRuleCount(rulesText: String): Int =
        Config.parseRulesText(rulesText).size

    /** 覆盖模式：只保留预设规则 */
    fun asReplaceRules(rulesText: String): List<Rule> = Config.parseRulesText(rulesText)

    /** 追加模式：预设规则追加到现有规则之后 */
    fun asAppendRules(existing: List<Rule>, rulesText: String): List<Rule> =
        existing + Config.parseRulesText(rulesText)
}
