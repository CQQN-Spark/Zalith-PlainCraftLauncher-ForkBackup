/*
 * Zalith Launcher 2 — PCL2 UI port
 *
 * PCL2 的「主页预设」清单与「你知道吗 / 回声洞」本地数据。
 *
 * 预设清单逐项取自 PCL2 `PageLaunchRight.xaml.vb:44-108` 的 `UiCustomPreset`
 * Select Case：每个预设要么是一段内置 XAML 内容（你知道吗 / 回声洞），
 * 要么是一个远程地址（网页或 PCL 主页 XAML 文件）。名称沿用 PCL2 语言文件
 * 的 `LangSetHomePagePresetPageA~Q`。
 *
 * 「你知道吗 / 回声洞」的随机内容在 PCL 里由闭源的 `PageOtherTest` 提供，
 * 这里内置一组同风格的中文文案，每次刷新随机抽一条。
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.movtery.zalithlauncher.ui.pcl2.data

import kotlin.random.Random

/** 一个主页预设条目。`url == null` 表示本地内容（你知道吗 / 回声洞）。 */
data class PclHomePresetEntry(
    val id: Int,
    val name: String,
    /** 远程主页地址（网页或 PCL 主页 XAML）；null = 本地内容。 */
    val url: String?,
) {
    /** 是否是本地内容预设（不联网）。 */
    val isLocal: Boolean get() = url == null
}

object PclHomePresets {

    /** 与 PCL2 `ComboCustomPreset` 一致的预设清单（id 对应 `UiCustomPreset`）。 */
    val entries: List<PclHomePresetEntry> = listOf(
        PclHomePresetEntry(0, "你知道吗？", null),
        PclHomePresetEntry(1, "回声洞", null),
        PclHomePresetEntry(2, "Minecraft 新闻（作者：最亮的信标）", "https://mcnews.meloong.com"),
        PclHomePresetEntry(
            4, "简单主页（作者：MFn233）",
            "https://pclhomeplazaoss.lingyunawa.top:26994/d/Homepages/MFn233/Custom.xaml",
        ),
        PclHomePresetEntry(5, "每日整合包推荐（作者：wkea）", "https://pclsub.sodamc.com/"),
        PclHomePresetEntry(6, "Minecraft 皮肤推荐（作者：wkea）", "https://forgepixel.com/pcl_sub_file"),
        PclHomePresetEntry(7, "OpenBMCLAPI 仪表盘 Lite（作者：Silverteal、Mxmilu666）", "https://pcl-bmcl.milu.ink/"),
        PclHomePresetEntry(
            8, "PCL 主页市场（作者：凌云）",
            "https://pclhomeplazaoss.lingyunawa.top:26994/p/Homepages/Homepage.Market/Custom.xaml",
        ),
        PclHomePresetEntry(
            9, "PCL 新闻速报（作者：Joker2184）",
            "https://pclhomeplazaoss.lingyunawa.top:26994/d/Homepages/Joker2184/UpdateHomepage.xaml",
        ),
        PclHomePresetEntry(
            10, "PCL 新功能说明书（作者：WForst-Breeze）",
            "https://raw.gitcode.com/WForst-Breeze/WhatsNewPCL/raw/main/Custom.xaml",
        ),
        PclHomePresetEntry(11, "OpenMCIM 仪表盘（作者：SALTWOOD）", "https://files.mcimirror.top/PCL"),
        PclHomePresetEntry(
            12, "杂志主页（作者：CreeperIsASpy）",
            "http://118.195.192.193:26995/d/magazine-homepage-pcl/Custom.xaml",
        ),
        PclHomePresetEntry(13, "PCL GitHub 仪表盘（作者：Deep-Dark-Forest）", "https://ddf.pcl-community.top/Custom.xaml"),
        PclHomePresetEntry(
            14, "Minecraft 更新摘要（作者：pynickle，部分由 AI 生成）",
            "https://raw.gitcode.com/ENC_Euphony/PCL-AI-Summary-HomePage/raw/master/Custom.xaml",
        ),
        PclHomePresetEntry(15, "今日新闻热点（作者：EYicheng，部分由 AI 生成）", "https://pcl.wyc-w.top/index.xaml"),
        PclHomePresetEntry(16, "Minecraft 芝士站（作者：awaEric233，部分由 AI 生成）", "https://www.xxag.top/mkss"),
        PclHomePresetEntry(17, "Bangumi 番剧主页（作者：wzyaeu）", "https://bangumi.p.kaphia.qzz.io"),
    )

    fun of(id: Int): PclHomePresetEntry? = entries.firstOrNull { it.id == id }

    fun default(): PclHomePresetEntry = entries.first()

    // ---------------------------------------------------------------- 你知道吗

    private val HINTS = listOf(
        "按住 Ctrl 再点击启动按钮，可以跳过启动前的全部检测直接进游戏（PCL 彩蛋）。",
        "Minecraft 里的苦力怕是做猪模型时搞错了长宽高的产物，却成了最经典的怪物。",
        "在 Java 版里给一只羊命名为「jeb_」，它的羊毛就会循环变色。",
        "末影人讨厌被盯着看；当然，它们更讨厌水。",
        "Minecraft 世界的高度上限曾是 256 格，现在已扩展到 384 格。",
        "游戏里的「唱片 13」和「唱片 cat」是最早加入的两张音乐唱片。",
        "村庄的钟被敲响时，村民会立刻躲回自己的房子——这是对付袭击的前置警报。",
        "下界要塞的宝藏箱里可以开出「下界之种」……开玩笑的，是石英和金锭。",
        "用剪刀可以安全收集盛开的花，但用剪刀剪蜂巢还是要先给蜜蜂「散个烟」。",
        "Minecraft 的背景音乐由 C418 创作，《Sweden》是其中最著名的一首。",
        "潜影贝关上壳时，免疫箭与近战——但挡不住爆炸和药水。",
        "在重生锚里放满荧石，就能在下界设置重生点。",
        "夜魅会在玩家连续三天不睡觉后出现——所以早点睡觉不是坏事。",
        "盾牌可以格挡苦力怕的爆炸伤害，时机得当几乎毫发无损。",
        "附魔台的文字其实是标准银河字母（Standard Galactic Alphabet）。",
        "下界合金锭需要在熔炉里烧远古残骸获得碎片，再和金锭合成。",
        "狐狸捡到物品时会优先选附魔过的——它们也识货。",
        "海晶灯的光照等级是 15，是水下建筑最亮的照明选择。",
        "使用经验修补附魔，手中的工具会吸收掉落的经验球自动修复。",
    )

    // ---------------------------------------------------------------- 回声洞

    // 回声洞的默认文案已删除（原先内置了几句假留言），现在只从官网联网抓取。
    private val CAVES = emptyList<String>()

    /** 随机一条「你知道吗」。 */
    fun randomHint(): String = HINTS[Random.nextInt(HINTS.size)]

    /** 随机一条「回声洞」。默认内容已删，本地无内容时返回空串。 */
    fun randomCave(): String = if (CAVES.isEmpty()) "" else CAVES[Random.nextInt(CAVES.size)]

    /** 按类型随机一条本地文案（`hint` / `cave`），联网抓取时的兜底。 */
    fun randomLocal(type: String): String = if (type == "cave") randomCave() else randomHint()
}
