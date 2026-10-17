/*
 * Zalith Launcher 2 — PCL2 UI port
 *
 * PCL2 皮肤自己的设置（对应 PCL 的 `个性化 → 标题栏 / 功能隐藏` 与 `背景图片 → 叠加彩色背景`）。
 *
 * 取值口径与 PCL 的设置项一一对应（`PageSetupUI.xaml` + `PageSetup/Settings.vb`）：
 *   `UiLogoType`    0 = 无 / 1 = 默认 / 2 = 文本 / 3 = 图片
 *   `UiLogoText`    标题栏文本
 *   `UiLogoLeft`    标题栏居左
 *   `UiBackgroundColorful`  叠加彩色背景（关掉就是 PCL 那个 #F5F5F5 纯色底）
 *   `UiHiddenPage*` 隐藏顶部导航的版块、以及设置/更多的子页面
 *
 * **与 Zalith 的衔接**：PCL 没有的项（背景不透明度 / 背景模糊）直接用 Zalith 的设置
 * （`AllSettings.launcherBackgroundOpacity` / `AllSettings.backgroundBlur`），
 * 所以两边改同一个值；PCL 独有的项存进 Zalith 的 MMKV（不污染 Zalith 的设置界面）。
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.movtery.zalithlauncher.ui.pcl2.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.movtery.zalithlauncher.setting.launcherMMKV

/** `PageSetupUI.xaml` 的 `UiLogoType`。 */
enum class PclLogoType { None, Default, Text, Picture }

/** 可隐藏的版块 / 子页面的键（与 PCL 的 `UiHiddenPage*` 设置一一对应）。 */
object PclHiddenKeys {
    const val HOME = "launch"
    const val DOWNLOAD = "download"
    const val LINK = "link"
    const val SETUP = "setup"
    const val OTHER = "other"

    // 设置 子页面（UiHiddenSetupLaunch / Link / Ui / System）
    const val SETUP_GAME = "setup.launch"
    const val SETUP_LINK = "setup.link"
    const val SETUP_UI = "setup.ui"
    const val SETUP_SYSTEM = "setup.system"

    // 设置 子页面（Zalith 原生独有，PCL2 没有这四页）
    const val SETUP_CONTROL = "setup.control"
    const val SETUP_GAMEPAD = "setup.gamepad"
    const val SETUP_JAVA = "setup.java"
    const val SETUP_CONTROL_MANAGE = "setup.controlManage"

    // 更多 子页面（UiHiddenOtherHelp / About / Test / Feedback / Vote）
    const val OTHER_HELP = "other.help"
    const val OTHER_ABOUT = "other.about"
    const val OTHER_TOOLS = "other.tools"
    const val OTHER_FEEDBACK = "other.feedback"
    const val OTHER_VOTE = "other.vote"
}

object PclSkinSettings {

    private const val KEY_LOGO_TYPE = "pcl2.ui.logoType"
    private const val KEY_LOGO_TEXT = "pcl2.ui.logoText"
    private const val KEY_LOGO_LEFT = "pcl2.ui.logoLeft"
    private const val KEY_COLORFUL = "pcl2.ui.backgroundColorful"
    private const val KEY_HIDDEN = "pcl2.ui.hidden"
    private const val KEY_UI_SCALE = "pcl2.ui.scale"

    /** 「界面缩放」的自动档：按窗口尺寸挑一个最接近 PCL 观感的档位。 */
    const val UI_SCALE_AUTO = -1

    /** 自动档的上限。PCL 的窗口是 850×500，放大到窗口里放得下就停。 */
    const val UI_SCALE_MAX = 1.25f

    /** 手动可选档位（百分比）。 */
    val UI_SCALE_PRESETS = listOf(50, 75, 100, 110, 125, 150)

    var logoType by mutableStateOf(PclLogoType.Default)
        private set
    var logoText by mutableStateOf("")
        private set
    var logoLeft by mutableStateOf(true)
        private set

    /** `UiBackgroundColorful`：关掉就用 PCL 的 `#F5F5F5` 纯色底。 */
    var colorfulBackground by mutableStateOf(true)
        private set

    /** 被隐藏的版块 / 子页面（见 [PclHiddenKeys]）。 */
    var hidden by mutableStateOf<Set<String>>(emptySet())
        private set

    /**
     * 「界面缩放」：`UI_SCALE_AUTO` 或百分比（100 / 110 / 125 / 150）。
     *
     * PCL 是桌面程序，Windows 的显示缩放会让「字与框一起放大」（WPF 按 DIP 整体缩放）；
     * 手机上则是靠 density 表达同样的东西。这里把系数乘进 `PclTheme` 的 density，
     * 于是整套界面（连同字号）等比放大 —— 字变小到看不清笔画时，直接调大这一档就行。
     */
    var uiScale by mutableStateOf(UI_SCALE_AUTO)
        private set

    fun isHidden(key: String): Boolean = key in hidden

    /** 设置顶层「主页」是不可隐藏的（隐藏了就没有可去的地方了）。 */
    fun setHidden(key: String, value: Boolean) {
        hidden = if (value) hidden + key else hidden - key
    }

    fun applyLogoType(value: PclLogoType) {
        logoType = value
    }

    fun applyLogoText(value: String) {
        logoText = value
    }

    fun applyLogoLeft(value: Boolean) {
        logoLeft = value
    }

    fun applyColorful(value: Boolean) {
        colorfulBackground = value
    }

    fun applyUiScale(value: Int) {
        uiScale = value
    }

    /**
     * 实际生效的缩放系数。
     *
     * 自动档把界面等比放大到「PCL 的 850×500 窗口占满当前窗口」为止，上限 [UI_SCALE_MAX]、
     * 下限 1（手机窗口比 850×500 小，就保持原样，不做缩小 —— 缩小只会更看不清）。
     */
    fun resolveScale(windowWidthDp: Float, windowHeightDp: Float): Float {
        if (uiScale != UI_SCALE_AUTO) return uiScale / 100f
        if (windowWidthDp <= 0f || windowHeightDp <= 0f) return 1f
        return minOf(windowWidthDp / 850f, windowHeightDp / 500f).coerceIn(1f, UI_SCALE_MAX)
    }

    fun load(context: Context) {
        val mmkv = launcherMMKV()
        logoType = PclLogoType.entries.getOrNull(mmkv.getInt(KEY_LOGO_TYPE, PclLogoType.Default.ordinal))
            ?: PclLogoType.Default
        logoText = mmkv.getString(KEY_LOGO_TEXT, "").orEmpty()
        logoLeft = mmkv.getBoolean(KEY_LOGO_LEFT, true)
        colorfulBackground = mmkv.getBoolean(KEY_COLORFUL, true)
        hidden = mmkv.getString(KEY_HIDDEN, "").orEmpty()
            .split('|')
            .filter { it.isNotBlank() }
            .toSet()
        uiScale = mmkv.getInt(KEY_UI_SCALE, UI_SCALE_AUTO)
    }

    fun persist(context: Context) {
        launcherMMKV().apply {
            putInt(KEY_LOGO_TYPE, logoType.ordinal)
            putString(KEY_LOGO_TEXT, logoText)
            putBoolean(KEY_LOGO_LEFT, logoLeft)
            putBoolean(KEY_COLORFUL, colorfulBackground)
            putString(KEY_HIDDEN, hidden.joinToString("|"))
            putInt(KEY_UI_SCALE, uiScale)
        }
    }
}


/**
 * 离线登录的用户名（PCL 的 `ComboName` 会记住上次输入的名字）。
 *
 * Zalith 只在「启动游戏」时才把名字落成账户，所以在没建账户之前名字是悬空的 ——
 * 这里单独存一份，退出应用再打开还在。
 */
object PclLoginSettings {
    private const val KEY_OFFLINE_NAME = "pcl2.login.offlineName"

    var offlineName by mutableStateOf("")
        private set

    fun applyOfflineName(value: String) {
        offlineName = value
    }

    fun load(context: Context) {
        offlineName = launcherMMKV().getString(KEY_OFFLINE_NAME, "").orEmpty()
    }

    fun persist(context: Context) {
        launcherMMKV().putString(KEY_OFFLINE_NAME, offlineName)
    }
}
