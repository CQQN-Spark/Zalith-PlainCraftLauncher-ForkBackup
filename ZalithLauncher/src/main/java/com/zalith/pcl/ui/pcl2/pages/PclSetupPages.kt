/*
 * Zalith Launcher 2 — PCL2 UI port
 *
 * 设置页的四张子页面，沿用 PCL2 原始 XAML 的卡片结构与行标签：
 *   PageSetupLaunch.xaml   启动
 *   PageSetupLink.xaml     联机
 *   PageSetupUI.xaml       个性化
 *   PageSetupSystem.xaml   其他（启动器）
 *
 * 与纯外观版的关键区别：本文件的每一个控件都直接读写 Zalith 的真实设置系统
 * （AllSettings / Renderers / DriverPluginManager / RuntimesManager），
 * 不存在只改本地状态、不落盘的“空壳”控件。
 *
 * PCL2 有、而 Zalith 后端并不存在对应能力的卡片（离线皮肤、背景音乐自定义、
 * 主页自定义、功能隐藏、设置导入导出、节点状态等）已经从页面中移除，
 * 以免留下点了没反应的假控件；其余控件全部接上真实设置。
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.zalith.pcl.ui.pcl2

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.materialkolor.PaletteStyle
import com.zalith.pcl.game.download.assets.platform.Platform
import com.zalith.pcl.game.plugin.driver.DriverPluginManager
import com.zalith.pcl.game.plugin.renderer_v2.RendererV2Data
import com.zalith.pcl.game.renderer.Renderers
import com.zalith.pcl.game.version.installed.GraphicsApi
import com.zalith.pcl.setting.AllSettings
import com.zalith.pcl.ui.pcl2.data.PclSkinSettings
import com.zalith.pcl.ui.pcl2.data.PclLogoType
import com.zalith.pcl.ui.pcl2.data.PclHiddenKeys
import com.zalith.pcl.ui.pcl2.components.PclBottomSpacer
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.zalith.pcl.setting.enums.ActionMenuSide
import com.zalith.pcl.setting.enums.AppLanguage
import com.zalith.pcl.setting.enums.BackgroundBlur
import com.zalith.pcl.setting.enums.DarkMode
import com.zalith.pcl.setting.enums.MirrorSourceType
import com.zalith.pcl.setting.enums.ResolutionRule
import com.zalith.pcl.setting.enums.applyLanguage
import com.zalith.pcl.setting.unit.AbstractSettingUnit
import com.zalith.pcl.ui.pcl2.components.PclCard
import com.zalith.pcl.ui.pcl2.components.PclButton
import com.zalith.pcl.ui.pcl2.components.PclCheckBox
import com.zalith.pcl.ui.pcl2.components.PclColorState
import com.zalith.pcl.ui.pcl2.components.PclComboBox
import com.zalith.pcl.ui.pcl2.components.PclConfirmDialog
import com.zalith.pcl.ui.pcl2.components.PclExtraTextButton
import com.zalith.pcl.ui.pcl2.components.PclHint
import com.zalith.pcl.ui.pcl2.components.PclHintTheme
import com.zalith.pcl.ui.pcl2.components.PclIcons
import com.zalith.pcl.ui.pcl2.components.PclIconButton
import com.zalith.pcl.ui.pcl2.components.PclRendererConfigDialog
import com.zalith.pcl.ui.pcl2.components.PclImageLoader
import com.zalith.pcl.ui.pcl2.components.PclRadioBox
import com.zalith.pcl.ui.pcl2.components.PclSlider
import com.zalith.pcl.ui.pcl2.components.PclText
import com.zalith.pcl.ui.pcl2.components.PclTextBox
import com.zalith.pcl.ui.pcl2.data.LocalPclRuntime
import com.zalith.pcl.ui.pcl2.PclHintHost
import com.zalith.pcl.ui.pcl2.data.PclAppearance
import com.zalith.pcl.ui.pcl2.data.PclBackgroundSuit
import com.zalith.pcl.ui.pcl2.data.PclHomeType
import com.zalith.pcl.ui.pcl2.data.PclSettings
import com.zalith.pcl.ui.pcl2.pages.pclInstanceVersionPage
import com.zalith.pcl.ui.pcl2.theme.PclDimens
import com.zalith.pcl.ui.pcl2.theme.pclPalette
import com.zalith.pcl.utils.animation.TransitionAnimationType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** 设置项一行的标签宽度（原 XAML 为 `SharedSizeGroup="Name"` 自适应列）。 */
// PCL 设置行：标签列 Width="Auto" + SharedSizeGroup="Name1"，标签自身 Margin="0,0,20,0"。
// Compose 没有共享尺寸组，这里用一个固定宽度近似「本页最宽标签」。
private val LabelWidth = 140.dp

/** 从 `content://` 里取显示名（拿不到就返回 null，导入时会用默认文件名）。 */
private fun queryDisplayName(context: Context, uri: Uri): String? =
    runCatching { DocumentFile.fromSingleUri(context, uri)?.name }.getOrNull()

/** 设置项行：标签 + 控件（`Grid ColumnDefinitions Auto / 1*`）。 */
@Composable
private fun SetupRow(
    label: String,
    modifier: Modifier = Modifier,
    control: @Composable () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PclText(text = label, modifier = Modifier.width(LabelWidth).padding(end = 20.dp))
        Box(modifier = Modifier.weight(1f)) { control() }
    }
}

/* ============================================================================
 *  通用设置绑定控件（读写真实设置，落盘到 MMKV）
 * ==========================================================================*/

/** 布尔设置 → 复选框。 */
@Composable
private fun SettingCheck(
    text: String,
    unit: AbstractSettingUnit<Boolean>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    PclCheckBox(
        text = text,
        checked = unit.state,
        modifier = modifier,
        enabled = enabled,
        onCheckedChange = { unit.save(it) },
    )
}

/** 字符串设置 → 文本框（本地持有输入态，避免每次按键回写引起的跳光标）。 */
@Composable
private fun SettingTextBox(
    unit: AbstractSettingUnit<String>,
    modifier: Modifier = Modifier,
    hint: String = "",
    singleLine: Boolean = true,
    enabled: Boolean = true,
) {
    var text by remember(unit) { mutableStateOf(unit.state) }
    PclTextBox(
        value = text,
        modifier = modifier,
        hint = hint,
        singleLine = singleLine,
        enabled = enabled,
        onValueChange = {
            text = it
            unit.save(it)
        },
    )
}

/** 任意可选值 → 下拉框（选中即落盘）。 */
@Composable
private fun <T> SettingCombo(
    label: String,
    items: List<T>,
    selected: T?,
    modifier: Modifier = Modifier,
    labelOf: (T) -> String = { it.toString() },
    onSelect: (T) -> Unit,
) {
    SetupRow(label) {
        PclComboBox(
            items = items,
            selected = selected,
            modifier = Modifier.fillMaxWidth().then(modifier),
            label = { item -> item?.let(labelOf).orEmpty() },
            onSelect = onSelect,
        )
    }
}

/** 整数设置 → 滑杆（控件使用 0..1 的比例，这里换算成实际区间）。 */
@Composable
private fun SettingSlider(
    unit: AbstractSettingUnit<Int>,
    min: Int,
    max: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (max <= min) return
    val value = unit.state.coerceIn(min, max)
    PclSlider(
        value = ((value - min).toFloat() / (max - min)).coerceIn(0f, 1f),
        modifier = modifier,
        enabled = enabled,
        onValueChange = { fraction ->
            unit.save((min + fraction * (max - min)).roundToInt().coerceIn(min, max))
        },
    )
}

/** 读取设备内存：已用 / 总量（MB）。 */
private fun memoryStats(context: Context): Pair<Int, Int> {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        ?: return 0 to 0
    val info = ActivityManager.MemoryInfo()
    am.getMemoryInfo(info)
    val totalMb = (info.totalMem / (1024L * 1024L)).toInt()
    val availMb = (info.availMem / (1024L * 1024L)).toInt()
    return (totalMb - availMb).coerceAtLeast(0) to totalMb
}

/* ============================================================================
 *  PageSetupLaunch —— 启动
 * ==========================================================================*/

@Composable
fun PclSetupLaunchPage() {
    val context = LocalContext.current
    val nav = LocalPclNav.current

    PclContent {
        // ---- 启动选项（全部为 AllSettings 中的真实条目）
        PclCard( modifier = Modifier.fillMaxWidth(),title = PclStrings.LangPageSetupLaunchLaunchOptions) {
            Column {
                SettingCombo(
                    label = PclStrings.LangPageSetupLaunchLaunchVersionIsolation,
                    items = listOf(true, false),
                    selected = AllSettings.versionIsolation.state,
                    labelOf = { if (it) "Turn on version isolation" else "Turn off version isolation" },
                    onSelect = { AllSettings.versionIsolation.save(it) },
                )
                SetupRow(PclStrings.LangPageSetupLaunchLaunchCustomInfo) {
                    SettingTextBox(AllSettings.versionCustomInfo, Modifier.fillMaxWidth())
                }
                SettingCheck("Does not check game integrity (skips file verification, starts faster)", AllSettings.skipGameIntegrityCheck)
                SettingCheck("Game page full screen", AllSettings.gameFullScreen)
                SettingCheck("Rendering using SurfaceView", AllSettings.useSurfaceView)
                SettingCheck("continuous performance mode", AllSettings.sustainedPerformance)
            }
        }

        Spacer(Modifier.height(PclDimens.cardGap))

        // ---- 渲染器（Zalith 的后端能力）
        PclRenderingCard()

        Spacer(Modifier.height(PclDimens.cardGap))

        // ---- 内存分配
        PclMemoryCard(context)

        Spacer(Modifier.height(PclDimens.cardGap))

        // ---- Java 运行环境
        PclJavaCard()

        Spacer(Modifier.height(PclDimens.cardGap))

        // ---- 高级选项（IsSwapped=True → 默认折叠）
        PclAdvancedCard()

        Spacer(Modifier.height(PclDimens.cardGap))

        // ---- 跳转版本独立设置
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            PclExtraTextButton(
                text = PclStrings.LangPageSetupLaunchGotoVersionSetting,
                logo = PclIcons.arrowRight,
                logoScale = 0.9f,
                onClick = { nav.push(pclInstanceVersionPage()) },
            )
        }
        Spacer(Modifier.height(PclDimens.cardGap))
        PclHint(
            text = "The above setting items directly read and write the real configuration of Zalith, and are saved and effective immediately after modification.",
            theme = PclHintTheme.Blue,
        )
        Spacer(Modifier.height(PclDimens.cardGap))
    }
}

/** 渲染器 / Vulkan 驱动 / 图形 API / 分辨率。 */
@Composable
private fun PclRenderingCard() {
    val rendererOptions = remember { PclSettings.rendererOptions() }
    val rendererIds = listOf("") + rendererOptions.map { it.first }
    val rendererNames = mapOf("" to "automatic selection") + rendererOptions.associate { it.first to it.second }

    val driverOptions = remember {
        runCatching { DriverPluginManager.getDriverList().map { it.id to it.name } }
            .getOrDefault(emptyList())
    }
    val currentDriver = AllSettings.vulkanDriver.state
    val driverIds = (listOf(currentDriver) + driverOptions.map { it.first }).distinct()
    val driverNames = driverOptions.toMap() + (currentDriver to
        (driverOptions.firstOrNull { it.first == currentDriver }?.second ?: currentDriver))

    // 新一代渲染器插件（MobileGlues 这类）会带一组可配置环境变量。
    // 原生界面在「全局渲染器」右侧有枚齿轮，点开就是 `RendererV2ConfigDialog`；
    // PCL 界面之前完全没有这个入口，装了插件也够不着 MOBILEGL_BACKEND_TYPE 之类的开关。
    val currentRendererId = AllSettings.renderer.state
    val rendererEnvUnits = remember(currentRendererId) {
        runCatching {
            Renderers.getRenderers()
                .filterIsInstance<RendererV2Data>()
                .firstOrNull { it.getUniqueIdentifier() == currentRendererId }
                ?.env
                ?.getConfigurableUnits()
                ?.takeIf { it.isNotEmpty() }
        }.getOrNull()
    }
    var showRendererConfig by remember { mutableStateOf(false) }

    PclCard( modifier = Modifier.fillMaxWidth(),title = "Renderer") {
        Column {
            SetupRow("Renderer") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PclComboBox(
                        items = rendererIds,
                        selected = AllSettings.renderer.state,
                        modifier = Modifier.weight(1f),
                        // PclComboBox 的 label 收到的是可空项（列表里允许出现 null），
                        // 所以这里要先兜住 null 再查名字。
                        label = { id -> id?.let { rendererNames[it] ?: it.ifEmpty { "automatic selection" } }.orEmpty() },
                        onSelect = { AllSettings.renderer.save(it) },
                    )
                    // 只有「选中新一代渲染器插件 + 它确实有可配置项」时才出现，和原生一致
                    if (rendererEnvUnits != null) {
                        Spacer(Modifier.width(10.dp))
                        PclIconButton(
                            logo = PclIcons.setupGear,
                            size = 30.dp,
                            logoScale = 0.95f,
                            contentDescription = "Renderer configuration",
                            onClick = { showRendererConfig = true },
                        )
                    }
                }
            }
            SettingCombo(
                label = "Vulkan driver",
                items = driverIds,
                selected = currentDriver,
                labelOf = { driverNames[it] ?: it },
                onSelect = { AllSettings.vulkanDriver.save(it) },
            )
            SettingCombo(
                label = "Graphics API",
                items = GraphicsApi.entries.toList(),
                selected = AllSettings.graphicsApi.state,
                labelOf = {
                    when (it) {
                        GraphicsApi.DEFAULT -> "Follow game settings"
                        GraphicsApi.DEFAULT_OPENGL -> "DefaultOpenGL"
                        else -> it.displayName
                    }
                },
                onSelect = { AllSettings.graphicsApi.save(it) },
            )
            SettingCombo(
                label = "Resolution rules",
                items = ResolutionRule.entries.toList(),
                selected = AllSettings.resolutionRule.state,
                labelOf = { if (it == ResolutionRule.PERCENTAGE) "Scale by screen percentage" else "Custom width and height" },
                onSelect = { AllSettings.resolutionRule.save(it) },
            )
            SetupRow("Resolution (%)") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SettingSlider(
                        unit = AllSettings.resolutionRatio,
                        min = 25,
                        max = 300,
                        modifier = Modifier.weight(1f),
                    )
                    PclText(
                        text = "${AllSettings.resolutionRatio.state}%",
                        modifier = Modifier.width(56.dp).padding(start = 12.dp),
                    )
                }
            }
            if (AllSettings.resolutionRule.state == ResolutionRule.CUSTOM) {
                SetupRow("custom width") {
                    SettingSlider(AllSettings.customResolutionWidth, 320, 7680, Modifier.fillMaxWidth())
                }
                SetupRow("Custom height") {
                    SettingSlider(AllSettings.customResolutionHeight, 240, 4320, Modifier.fillMaxWidth())
                }
            }
            Spacer(Modifier.height(6.dp))
            SettingCheck("Shader log output (for debugging)", AllSettings.dumpShaders)
            SettingCheck("Zink vertical sync", AllSettings.vsyncInZink)
            SettingCheck("Using the system Vulkan driver (Zink)", AllSettings.zinkPreferSystemDriver)
            SettingCheck("Sodium/Iris automatically switches to compatible renderer", AllSettings.autoRendererCompat)
            Spacer(Modifier.height(4.dp))
            PclHint(
                text = "The renderer and driver list comes from the plug-ins actually installed on the device. When the plug-in is not installed, only the built-in Turnip driver is displayed.",
                theme = PclHintTheme.Blue,
            )
            PclHint(
                text = "\"Sodium / Iris automatic switching\" only takes effect when the renderer is \"automatic selection\": if Sodium and Iris are installed in the mods" +
                        "This type of module requires desktop OpenGL 3.3+, and the currently parsed renderer belongs to the GL4ES translation layer" +
                        "(It will get stuck on the loading interface without reporting an error). The launcher will automatically switch to Kopper Zink to start the game this time." +
                        "Your settings will not be changed. ",
                theme = PclHintTheme.Blue,
            )
        }
    }

    // 渲染器配置弹窗（环境变量）。放在卡片外层：`PclDialogContainer` 自带 Dialog 层级，
    // 塞进 `Column` 里也不会被卡片裁剪，但放外层更清楚。
    if (showRendererConfig && rendererEnvUnits != null) {
        PclRendererConfigDialog(
            units = rendererEnvUnits,
            onDismiss = { showRendererConfig = false },
        )
    }
}

/** 内存分配：自动 / 自定义 + 真实内存条。 */
@Composable
private fun PclMemoryCard(context: Context) {
    val p = pclPalette
    val limitMb = remember { PclSettings.memoryLimitMb(context) }
    val setting = AllSettings.ramAllocation
    val ramAuto = setting.state == null
    val ramMb = setting.state ?: (limitMb / 2)
    val fraction = PclSettings.ramToFraction(ramMb, limitMb)
    val (usedMb, totalMb) = remember { memoryStats(context) }

    PclCard( modifier = Modifier.fillMaxWidth(),title = PclStrings.LangPageSetupLaunchLaunchMem) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PclRadioBox(
                    text = PclStrings.LangPageSetupLaunchLaunchMemAutoAdjust,
                    checked = ramAuto,
                    modifier = Modifier.width(140.dp),
                    onCheckedChange = { if (it) setting.save(null) },
                )
                PclRadioBox(
                    text = PclStrings.LangPageSetupLaunchLaunchMemCustom,
                    checked = !ramAuto,
                    onCheckedChange = { if (it) setting.save(ramMb) },
                )
            }
            PclSlider(
                value = if (ramAuto) 0.5f else fraction,
                enabled = !ramAuto,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                onValueChange = { f -> setting.save(PclSettings.fractionToRam(f, limitMb)) },
            )

            Spacer(Modifier.height(11.dp))
            val usedRatio = if (totalMb > 0) usedMb.toFloat() / totalMb else 0f
            val gameRatio = if (totalMb > 0) ramMb.toFloat() / totalMb else 0f
            PclRamBar(
                usedRatio = usedRatio.coerceIn(0f, 1f),
                gameRatio = gameRatio.coerceIn(0f, 1f - usedRatio.coerceIn(0f, 1f)),
            )
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    PclText(
                        text = PclStrings.LangPageSetupLaunchLaunchMemUsedMem,
                        fontSize = 11,
                        color = p.color1.copy(alpha = 0.7f),
                    )
                    PclText(text = "%.1f GB / %.1f GB".format(usedMb / 1024f, totalMb / 1024f), fontSize = 16)
                }
                Column {
                    PclText(
                        text = PclStrings.LangPageSetupLaunchLaunchMemMemForGame,
                        fontSize = 11,
                        color = p.color1.copy(alpha = 0.7f),
                    )
                    PclText(
                        text = if (ramAuto) "Automatic (approx. %.2f GB)".format(ramMb / 1024f)
                        else "%.2f GB".format(ramMb / 1024f),
                        fontSize = 16,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            PclHint(
                text = "The memory that can be allocated natively to a launcher is limited to $limitMb MB. ",
                theme = PclHintTheme.Blue,
            )
        }
    }
}

/** Java 运行环境。 */
@Composable
private fun PclJavaCard() {
    var runtimes by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    LaunchedEffect(Unit) {
        runtimes = withContext(Dispatchers.IO) { PclSettings.javaRuntimeOptions() }
    }
    val ids = listOf("") + runtimes.map { it.first }
    val names = mapOf("" to PclStrings.LangPageSetupLaunchLaunchJavaAutoChoice) +
        runtimes.associate { it.first to it.second }

    PclCard( modifier = Modifier.fillMaxWidth(),title = PclStrings.LangPageSetupLaunchLaunchJava) {
        Column {
            SettingCheck("Automatically select the appropriate Java environment", AllSettings.autoPickJavaRuntime)
            SettingCombo(
                label = "Java environment",
                items = ids,
                selected = AllSettings.javaRuntime.state,
                labelOf = { names[it] ?: it.ifEmpty { PclStrings.LangPageSetupLaunchLaunchJavaAutoChoice } },
                onSelect = { AllSettings.javaRuntime.save(it) },
            )
            if (runtimes.isEmpty()) {
                Spacer(Modifier.height(4.dp))
                PclHint(
                    text = "No Java runtime has been detected on the device. You can download and install it outside this page and then come back here to select it.",
                    theme = PclHintTheme.Yellow,
                )
            }
        }
    }
}

/** 高级选项：JVM 参数 / 日志。 */
@Composable
private fun PclAdvancedCard() {
    PclCard(modifier = Modifier.fillMaxWidth(),
        title = PclStrings.LangPageSetupLaunchLaunchAdvanceOptions,
        collapsible = true,
        initiallyCollapsed = true,
    ) {
        Column {
            SetupRow(PclStrings.LangPageSetupLaunchLaunchAdvanceOptionsJVMHead) {
                SettingTextBox(AllSettings.jvmArgs, Modifier.fillMaxWidth(), singleLine = false)
            }
            SetupRow("Log font size") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SettingSlider(AllSettings.logTextSize, 5, 20, Modifier.weight(1f))
                    PclText(
                        text = "${AllSettings.logTextSize.state}",
                        modifier = Modifier.width(40.dp).padding(start = 12.dp),
                    )
                }
            }
            SetupRow("Log buffer refresh interval (milliseconds)") {
                SettingSlider(AllSettings.logBufferFlushInterval, 100, 1000, Modifier.fillMaxWidth())
            }
            SettingCheck("Automatically display the log when starting the game (until the game starts rendering)", AllSettings.showLogAutomatic)
            Spacer(Modifier.height(5.dp))
            PclHint(
                text = "The JVM parameters will be appended to the end of the game startup command line as they are, so please fill them in carefully.",
                theme = PclHintTheme.Yellow,
            )
        }
    }
}

/** `PanRamDisplay` —— 已用（ColorBrush3→ColorBrush2 渐变）/ 游戏（ColorBrush3 @0.5）/ 空闲（ColorBrush6 @0.7）。 */
@Composable
private fun PclRamBar(usedRatio: Float, gameRatio: Float) {
    val p = pclPalette
    Row(modifier = Modifier.fillMaxWidth().height(8.dp)) {
        Box(
            modifier = Modifier
                .weight(usedRatio.coerceAtLeast(0.01f))
                .height(4.dp)
                .background(Brush.horizontalGradient(listOf(p.color3, p.color2))),
        )
        Box(
            modifier = Modifier
                .weight(gameRatio.coerceAtLeast(0.01f))
                .height(4.dp)
                .background(p.color3.copy(alpha = 0.5f)),
        )
        Box(
            modifier = Modifier
                .weight((1f - usedRatio - gameRatio).coerceAtLeast(0.01f))
                .height(4.dp)
                .background(p.color6.copy(alpha = 0.7f)),
        )
    }
}

/* ============================================================================
 *  PageSetupLink —— 联机（Zalith 的陶瓦联机）
 * ==========================================================================*/

@Composable
fun PclSetupLinkPage() {
    PclContent {
        PclCard( modifier = Modifier.fillMaxWidth(),title = PclStrings.LangPageSetupLinkConnectionOptions) {
            Column {
                SettingCheck("Enable Terracotta Online", AllSettings.enableTerracotta)
                Spacer(Modifier.height(6.dp))
                PclHint(
                    text = "Terracotta connection allows you to play with friends without a public IP, provided by Zalith's built-in connection module.",
                    theme = PclHintTheme.Blue,
                )
            }
        }
        Spacer(Modifier.height(PclDimens.cardGap))

        PclCard(modifier = Modifier.fillMaxWidth(),
            title = PclStrings.LangPageSetupLinkAdvancedSettings,
            collapsible = true,
        ) {
            Column {
                SettingCheck("Using a custom EasyTier server node", AllSettings.enableTerracottaNodes)
                SetupRow(PclStrings.LangPageSetupLinkCustomPeer) {
                    SettingTextBox(
                        unit = AllSettings.terracottaNodes,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = AllSettings.enableTerracottaNodes.state,
                    )
                }
                Spacer(Modifier.height(10.dp))
                PclHint(
                    text = "Please separate multiple nodes with commas or semicolons; if left blank, the default public node will be used.",
                    theme = PclHintTheme.Blue,
                )
            }
        }
        Spacer(Modifier.height(PclDimens.cardGap))
    }
}

/* ============================================================================
 *  PageSetupUI —— 个性化
 * ==========================================================================*/

/**
 * 「功能隐藏」里的一项：勾上就藏起来（存 [PclSkinSettings]）。
 *
 * 勾/取消都当场给一条提示：勾上之后界面上少的是一整个入口，用户很可能
 * 既不知道「为什么不见了」，也不知道「怎么找回来」—— 提示里把恢复手势一并写清楚。
 */
@Composable
private fun PclSkinHiddenCheck(key: String, label: String) {
    val context = LocalContext.current
    PclCheckBox(
        text = label,
        checked = PclSkinSettings.isHidden(key),
        onCheckedChange = { checked ->
            PclSkinSettings.setHidden(key, checked)
            PclSkinSettings.persist(context)
            if (checked) {
                PclHintHost.push(
                    "\"$label\" is hidden. " +
                            "Hidden entrances can be temporarily displayed," +
                            "Go to \"Settings → Personalization → Function Hide\" and uncheck it to restore it.",
                    PclHintTheme.Yellow,
                )
            } else {
                PclHintHost.push("\"$label\" has been restored.", PclHintTheme.Green)
            }
        },
    )
}

/** 一行「标签 + 滑杆 + 数值」，直接读写 Zalith 的整数设置项。 */
@Composable
private fun SettingSliderRow(
    label: String,
    unit: AbstractSettingUnit<Int>,
    min: Int,
    max: Int,
) {
    SetupRow(label) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PclSlider(
                value = ((unit.state - min).toFloat() / (max - min)).coerceIn(0f, 1f),
                modifier = Modifier.weight(1f),
                onValueChange = { unit.save(min + ((max - min) * it).roundToInt()) },
            )
            PclText(
                text = "${unit.state}",
                modifier = Modifier.width(48.dp).padding(start = 12.dp),
                fontSize = 12,
                color = pclPalette.gray2,
            )
        }
    }
}

@Composable
fun PclSetupUiPage() {
    val context = LocalContext.current

    // 「清空背景音乐」的二次确认（PCL 原版也是先弹一个警告框再删）。
    var showClearMusic by remember { mutableStateOf(false) }

    // 「选择背景图片」：PCL 是往文件夹里丢图片（`BtnBackgroundOpen` 打开文件夹），
    // Android 上换成系统图片选择器，选完复制进应用目录的 `PCL/Background`。
    val backgroundPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = PclAppearance.importBackground(context, uri, queryDisplayName(context, uri))
        if (ok) {
            PclImageLoader.invalidate()
            PclImageLoader.load(PclAppearance.backgroundImage)
            PclAppearance.persist(context)
            PclHintHost.push("The background image is set.", PclHintTheme.Green)
        } else {
            PclHintHost.push("Background image import failed.", PclHintTheme.Red)
        }
    }

    // 「更改图片」（标题栏）：`UiLogoType = 3`
    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = PclAppearance.importLogoImage(context, uri, queryDisplayName(context, uri))
        if (ok) {
            PclImageLoader.invalidate()
            PclImageLoader.load(PclAppearance.logoImage)
            PclAppearance.persist(context)
            PclHintHost.push("Title bar image has been set.", PclHintTheme.Green)
        } else {
            PclHintHost.push("The title bar image failed to import.", PclHintTheme.Red)
        }
    }

    // 「导入音乐」：PCL 的「打开文件夹」，Android 上换成可多选的音乐选择器
    val musicPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult
        val count = PclAppearance.importMusic(context, uris)
        if (count > 0) {
            PclAppearance.persist(context)
            // 导入 + 开播都在 importMusic 里做完了（正在播时不打断当前这首），
            // 这里只按实际结果给一句说明 —— 免得再出现「提示导入成功、实际一首没进列表」。
            val total = PclAppearance.musicFiles(context).size
            when {
                PclAppearance.musicPlaying ->
                    PclHintHost.push(
                        "$count pieces of background music have been imported and \"${PclAppearance.musicTitle}\" is playing.",
                        PclHintTheme.Green,
                    )
                total > 0 ->
                    PclHintHost.push("$count pieces of background music have been imported and started playing.", PclHintTheme.Green)
                else ->
                    PclHintHost.push(
                        "$count files imported, but none are in a recognized audio format" +
                                "(Supports mp3/ogg/wav/m4a/aac/flac/opus).",
                        PclHintTheme.Red,
                    )
            }
        } else {
            PclHintHost.push("Music import failed.", PclHintTheme.Red)
        }
    }

    // 「读取本地文件」的主页：选一个 .html 导入到 `PCL/Custom`
    val homePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = PclAppearance.importHomeFile(context, uri, queryDisplayName(context, uri))
        if (ok) {
            PclAppearance.persist(context)
            PclHintHost.push("The local home page has been imported.", PclHintTheme.Green)
        } else {
            PclHintHost.push("Import of local home page failed.", PclHintTheme.Red)
        }
    }

    PclContent {
        // ---- 基础（PCL `PageSetupUI.xaml:16` 的 CardLauncher，Title 是 `LangSetTheme` =「基础」）
        PclCard(modifier = Modifier.fillMaxWidth(), title = "Base") {
            Column {
                // ---- 界面缩放
                // PCL 是桌面程序，字号跟着 Windows 的显示缩放走；Android 上没有这个入口，
                // 所以这里补一档：整张界面（含字号）等比放大，字小到看不清笔画时直接调它。
                SetupRow("Interface scaling") {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PclRadioBox(
                            text = "automatic",
                            checked = PclSkinSettings.uiScale == PclSkinSettings.UI_SCALE_AUTO,
                            onCheckedChange = {
                                PclSkinSettings.applyUiScale(PclSkinSettings.UI_SCALE_AUTO)
                                PclSkinSettings.persist(context)
                            },
                        )
                        PclSkinSettings.UI_SCALE_PRESETS.forEach { percent ->
                            PclRadioBox(
                                text = "$percent%",
                                checked = PclSkinSettings.uiScale == percent,
                                onCheckedChange = {
                                    PclSkinSettings.applyUiScale(percent)
                                    PclSkinSettings.persist(context)
                                },
                            )
                        }
                    }
                }
                PclHint(
                    text = "\"Interface scaling\" is based on the behavior of PCL under Windows display scaling:" +
                            "The entire interface (font size, controls, spacing) is enlarged proportionally, and it will not be like the system \"font size\"" +
                            "Only enlarge the text and burst the fixed height container. " +
                            "50% / 75% is reserved for situations where \"the window is already small and you want to make the interface more compact\".",
                    theme = PclHintTheme.Blue,
                )
                PclHint(
                    text = "The following cards (background image/background music/title bar/home page/function hiding) are all really in effect;" +
                            "Please change Zalith’s native appearance settings such as dark mode, language, and animation in Zalith’s own settings.",
                    theme = PclHintTheme.Blue,
                )
            }
        }
        Spacer(Modifier.height(PclDimens.cardGap))

        // ---- 背景图片（PCL `PageSetupUI.xaml:284` 的 CardBackground）
        // PCL 是让用户往 `PCL/Background` 文件夹里丢图片；Android 没有稳定的「打开该文件夹」
        // 入口，所以改成用系统文件选择器把图片**导入**到应用私有的同名文件夹，
        // 之后的行为与 PCL 一致：每次打开启动器随机取一张，用「自适应方式」铺开。
        PclCard(modifier = Modifier.fillMaxWidth(), title = PclStrings.LangSetBackgroundPic) {
            Column {
                SetupRow(PclStrings.LangSetBackgroundPicAdaptive) {
                    PclComboBox(
                        items = PclBackgroundSuit.entries,
                        selected = PclAppearance.backgroundSuit,
                        modifier = Modifier.fillMaxWidth(),
                        label = { it?.label.orEmpty() },
                        onSelect = {
                            PclAppearance.applyBackgroundSuit(it)
                            PclAppearance.persist(context)
                        },
                    )
                }
                SettingSliderRow(
                    label = PclStrings.LangSetBackgroundPicOpacity,
                    unit = AllSettings.launcherBackgroundOpacity,
                    min = 20, max = 100,
                )
                SettingSliderRow(
                    label = PclStrings.LangSetBackgroundPicBlur,
                    unit = AllSettings.backgroundBlur,
                    min = 0, max = 40,
                )
                Spacer(Modifier.height(6.dp))
                PclCheckBox(
                    text = PclStrings.LangSetBackgroundPicColorfulBack,
                    checked = PclSkinSettings.colorfulBackground,
                    onCheckedChange = {
                        PclSkinSettings.applyColorful(it)
                        PclSkinSettings.persist(context)
                    },
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(15.dp),
                ) {
                    PclButton(
                        text = "Select background image",
                        colorState = PclColorState.Highlight,
                        onClick = { backgroundPicker.launch("image/*") },
                    )
                    PclButton(
                        text = PclStrings.LangSetBackgroundPicRefresh,
                        onClick = {
                            PclAppearance.refreshBackground(context)
                            PclAppearance.persist(context)
                            PclImageLoader.invalidate()
                        },
                    )
                    PclButton(
                        text = PclStrings.LangSetBackgroundPicEmpty,
                        colorState = PclColorState.Red,
                        onClick = {
                            PclAppearance.clearBackground()
                            PclAppearance.persist(context)
                            PclImageLoader.invalidate()
                        },
                    )
                }
                PclHint(
                    text = "\"Opacity\" and \"Background Blur\" use Zalith's settings of the same name (`launcherBackgroundOpacity` /" +
                            "`backgroundBlur`), the same value is changed on both sides." +
                            "The background blur is Gaussian blur implemented by the launcher itself (layered convolution, pre-calculated by the CPU and then amplified by the GPU)." +
                            "So it doesn’t rely on Android 12’s real-time blur, and all versions behave consistently." +
                            "The PCL method of \"filling folders\" is replaced by \"selecting background images\" on Android:" +
                            "The image will be copied into `PCL/Background` in the application directory, and then \"Refresh Background Image\" will randomly select one.",
                    theme = PclHintTheme.Blue,
                )
            }
        }
        Spacer(Modifier.height(PclDimens.cardGap))

        // ---- 背景音乐（PCL `PageSetupUI.xaml:404` 的 CardMusic）
        PclCard(modifier = Modifier.fillMaxWidth(), title = PclStrings.LangSetBackgroundMusicTitle) {
            Column {
                SetupRow(PclStrings.LangSetBackgroundMusicVolume) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PclSlider(
                            value = PclAppearance.musicVolume / 100f,
                            modifier = Modifier.weight(1f),
                            onValueChange = {
                                PclAppearance.applyMusicVolume((it * 100).roundToInt())
                                PclAppearance.persist(context)
                            },
                        )
                        PclText(
                            text = "${PclAppearance.musicVolume}",
                            modifier = Modifier.width(48.dp).padding(start = 12.dp),
                            fontSize = 12,
                            color = pclPalette.gray2,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                PclCheckBox(
                    text = PclStrings.LangSetBackgroundMusicPlayRandom,
                    checked = PclAppearance.musicRandom,
                    onCheckedChange = {
                        PclAppearance.applyMusicRandom(it)
                        PclAppearance.persist(context)
                    },
                )
                PclCheckBox(
                    text = PclStrings.LangSetBackgroundMusicPlayOnStart,
                    checked = PclAppearance.musicAuto,
                    onCheckedChange = {
                        PclAppearance.applyMusicAuto(it)
                        PclAppearance.persist(context)
                    },
                )
                PclCheckBox(
                    text = PclStrings.LangSetBackgroundMusicPlayDuringGaming,
                    checked = PclAppearance.musicStart,
                    onCheckedChange = {
                        PclAppearance.applyMusicStart(it)
                        PclAppearance.persist(context)
                    },
                )
                PclCheckBox(
                    text = PclStrings.LangSetBackgroundMusicPlayDuringLaunching,
                    checked = PclAppearance.musicStop,
                    onCheckedChange = {
                        PclAppearance.applyMusicStop(it)
                        PclAppearance.persist(context)
                    },
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(15.dp),
                ) {
                    PclButton(
                        text = "Import music",
                        colorState = PclColorState.Highlight,
                        onClick = { musicPicker.launch("audio/*") },
                    )
                    PclButton(
                        text = if (PclAppearance.musicPlaying) "Pause playback" else "Start playing",
                        onClick = { PclAppearance.toggleMusic(context) },
                    )
                    PclButton(text = "Next song") { PclAppearance.next(context, auto = true) }
                    PclButton(
                        text = PclStrings.LangSetBackgroundMusicRefresh,
                        onClick = {
                            PclAppearance.refreshMusic(context)
                            PclAppearance.persist(context)
                        },
                    )
                    PclButton(
                        text = PclStrings.LangSetBackgroundMusicEmpty,
                        colorState = PclColorState.Red,
                        onClick = { showClearMusic = true },
                    )
                }
                PclHint(
                    text = "Same background picture: PCL’s method is to drop music into the `PCL/Music` folder, and replace it on Android" +
                            "\"Import music\" (multiple selections available), the behavior will be the same after importing - random play, volume, and auto-play are all available." +
                            "Current status:" + if (PclAppearance.musicPlaying) {
                        "Now playing \"${PclAppearance.musicTitle}\""
                    } else if (PclAppearance.musicTitle.isNotBlank()) {
                        "Suspended \"${PclAppearance.musicTitle}\""
                    } else {
                        "Not playing"
                    },
                    theme = PclHintTheme.Blue,
                )
            }
        }
        Spacer(Modifier.height(PclDimens.cardGap))

        // ---- 标题栏（PCL `PageSetupUI.xaml:484` 的 CardLogo）
        PclCard(modifier = Modifier.fillMaxWidth(), title = PclStrings.LangSetTitle) {
            Column {
                SetupRow(PclStrings.LangSetThemeTitle) {
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        listOf(
                            PclLogoType.None to PclStrings.LangSetNone,
                            PclLogoType.Default to PclStrings.LangSetDefault,
                            PclLogoType.Text to PclStrings.LangSetText,
                        ).forEach { (type, label) ->
                            PclRadioBox(
                                text = label,
                                checked = PclSkinSettings.logoType == type,
                                onCheckedChange = {
                                    PclSkinSettings.applyLogoType(type)
                                    PclSkinSettings.persist(context)
                                },
                            )
                        }
                        PclRadioBox(
                            text = PclStrings.LangSetPicture,
                            checked = PclSkinSettings.logoType == PclLogoType.Picture,
                            onCheckedChange = {
                                PclSkinSettings.applyLogoType(PclLogoType.Picture)
                                PclSkinSettings.persist(context)
                            },
                        )
                    }
                }
                if (PclSkinSettings.logoType == PclLogoType.Text) {
                    SetupRow(PclStrings.LangSetTitleContent) {
                        PclTextBox(
                            value = PclSkinSettings.logoText,
                            modifier = Modifier.fillMaxWidth(),
                            hint = "PCL",
                            onValueChange = {
                                PclSkinSettings.applyLogoText(it)
                                PclSkinSettings.persist(context)
                            },
                        )
                    }
                }
                SetupRow("") {
                    PclCheckBox(
                        text = PclStrings.LangSetTitleLeft,
                        checked = PclSkinSettings.logoLeft,
                        onCheckedChange = {
                            PclSkinSettings.applyLogoLeft(it)
                            PclSkinSettings.persist(context)
                        },
                    )
                }
                // PanLogoChange：`UiLogoType = 3` 时出现的「更改图片 / 清空图片」
                if (PclSkinSettings.logoType == PclLogoType.Picture) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(15.dp),
                    ) {
                        PclButton(
                            text = PclStrings.LangSetTitlePicChange,
                            colorState = PclColorState.Highlight,
                            onClick = { logoPicker.launch("image/*") },
                        )
                        PclButton(
                            text = PclStrings.LangSetTitlePicEmpty,
                            colorState = PclColorState.Red,
                            onClick = {
                                PclAppearance.clearLogoImage()
                                PclAppearance.persist(context)
                                PclImageLoader.invalidate()
                            },
                        )
                    }
                }
                PclHint(
                    text = "\"None/Default/Text\" will take effect directly; \"Picture\" will use the picture selected in the \"Change Picture\" below" +
                            "(When no image is selected, return to the PCL stroke logo first). " +
                            "So it is only visible here when the title bar has a position.",
                    theme = PclHintTheme.Blue,
                )
            }
        }
        Spacer(Modifier.height(PclDimens.cardGap))

        // ---- 主页（PCL `PageSetupUI.xaml:569` 的 CardCustom）
        // PCL 的「主页」= 用自定义内容替换启动页右侧整块（左边账户 / 版本面板照旧）。
        // 这里支持「联网更新」（输入网址）与「读取本地文件」（导入一个 html）；
        // 「预设」要拉 PCL 官方的主页市场列表，本移植版没有那份数据，所以先置灰。
        PclCard(modifier = Modifier.fillMaxWidth(), title = PclStrings.LangSetHomePageTitle) {
            Column {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PclRadioBox(
                        text = PclStrings.LangSetHomePageEmpty,
                        checked = PclAppearance.homeType == PclHomeType.EMPTY,
                        onCheckedChange = {
                            PclAppearance.applyHomeType(PclHomeType.EMPTY)
                            PclAppearance.persist(context)
                        },
                    )
                    PclRadioBox(
                        text = PclStrings.LangSetHomePagePreset,
                        checked = false,
                        enabled = false,
                        onCheckedChange = {},
                    )
                    PclRadioBox(
                        text = PclStrings.LangSetHomePageLocal,
                        checked = PclAppearance.homeType == PclHomeType.LOCAL,
                        onCheckedChange = {
                            PclAppearance.applyHomeType(PclHomeType.LOCAL)
                            PclAppearance.persist(context)
                        },
                    )
                    PclRadioBox(
                        text = PclStrings.LangSetHomePageOnline,
                        checked = PclAppearance.homeType == PclHomeType.ONLINE,
                        onCheckedChange = {
                            PclAppearance.applyHomeType(PclHomeType.ONLINE)
                            PclAppearance.persist(context)
                        },
                    )
                }
                if (PclAppearance.homeType == PclHomeType.ONLINE) {
                    Spacer(Modifier.height(10.dp))
                    SetupRow(PclStrings.LangSetHomePageDownload) {
                        PclTextBox(
                            value = PclAppearance.homeNet,
                            modifier = Modifier.fillMaxWidth(),
                            hint = "https://",
                            onValueChange = {
                                PclAppearance.applyHomeNet(it)
                                PclAppearance.persist(context)
                            },
                        )
                    }
                }
                if (PclAppearance.homeType == PclHomeType.LOCAL) {
                    Spacer(Modifier.height(10.dp))
                    PclButton(
                        text = "Select local home page file",
                        colorState = PclColorState.Highlight,
                        onClick = { homePicker.launch("*/*") },
                    )
                    if (PclAppearance.homeLocalFile != null) {
                        Spacer(Modifier.height(6.dp))
                        PclText(
                            text = "Current: ${PclAppearance.homeLocalFile?.substringAfterLast('/')}",
                            color = pclPalette.gray2,
                            fontSize = 12,
                        )
                    }
                }
                if (PclAppearance.homeUrl != null) {
                    PclHint(
                        text = PclStrings.LangSetHomePageCustomWarn,
                        theme = PclHintTheme.Yellow,
                    )
                }
                PclHint(
                    text = "After setting, the right side of the \"Homepage\" section will be replaced by this customized homepage (the account and version panels on the left remain unchanged)." +
                            "There is a \"Refresh Home Page\" at the top. " +
                            "This ported version does not have that data, so it is grayed out first.",
                    theme = PclHintTheme.Blue,
                )
            }
        }
        Spacer(Modifier.height(PclDimens.cardGap))

        // ---- 功能隐藏（PCL `PageSetupUI.xaml:723` 的 CardSwitch）
        //
        // 勾上之后对应入口会从界面上消失，而取消勾选的开关就在这里 —— 于是右下角
        // 留了个「连点 10 次」的手势，把这些入口**临时**放出来（见 `PclHiddenPeekGesture`）。
        PclCard(modifier = Modifier.fillMaxWidth(), title = PclStrings.LangSetFuncHidden) {
            Column {
                PclSkinHiddenCheck(key = PclHiddenKeys.DOWNLOAD, label = PclStrings.LangSetFuncHiddenDownload)
                PclSkinHiddenCheck(key = PclHiddenKeys.LINK, label = PclStrings.LangSetFuncHiddenLink)
                PclSkinHiddenCheck(key = PclHiddenKeys.SETUP, label = PclStrings.LangSetFuncHiddenSettings)
                PclSkinHiddenCheck(key = PclHiddenKeys.OTHER, label = PclStrings.LangSetFuncHiddenMore)
                Spacer(Modifier.height(6.dp))
                PclText(
                    text = PclStrings.LangSetFuncHiddenSettingsSubPage,
                    color = pclPalette.gray2,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                PclSkinHiddenCheck(PclHiddenKeys.SETUP_GAME, PclStrings.LangSetFuncHiddenSettingsGame)
                PclSkinHiddenCheck(PclHiddenKeys.SETUP_LINK, PclStrings.LangSetFuncHiddenSettingsLink)
                PclSkinHiddenCheck(PclHiddenKeys.SETUP_UI, PclStrings.LangSetFuncHiddenSettingsPersonalize)
                PclSkinHiddenCheck(PclHiddenKeys.SETUP_SYSTEM, PclStrings.LangSetFuncHiddenSettingsLaunch)
                Spacer(Modifier.height(6.dp))
                PclText(
                    text = PclStrings.LangSetFuncHiddenMoreSubPage,
                    color = pclPalette.gray2,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                PclSkinHiddenCheck(PclHiddenKeys.OTHER_HELP, PclStrings.LangSetFuncHiddenMoreHelp)
                PclSkinHiddenCheck(PclHiddenKeys.OTHER_ABOUT, PclStrings.LangSetFuncHiddenMoreAbout)
                PclSkinHiddenCheck(PclHiddenKeys.OTHER_TOOLS, PclStrings.LangSetFuncHiddenMoreTool)
                PclSkinHiddenCheck(PclHiddenKeys.OTHER_FEEDBACK, PclStrings.LangSetFuncHiddenMoreFeedback)
                PclSkinHiddenCheck(PclHiddenKeys.OTHER_VOTE, PclStrings.LangSetFuncHiddenMoreVote)
                PclHint(
                    text = "Check it to hide the corresponding entrance (the top navigation row, and the Settings/More subpages)." +
                            "The \"main page\" is not allowed to be hidden, otherwise there will be nowhere to go.",
                    theme = PclHintTheme.Blue,
                )
                PclHint(
                    text = "After checking it off, the entrance disappears from the interface. " +
                            "${PclSkinSettings.PEEK_TAP_COUNT} times, all hidden entrances will reappear" +
                            "${PclSkinSettings.PEEK_DURATION_MS / 1000} seconds, take advantage of this time to come back and uncheck it;" +
                            "The checked status will not be changed due to this temporary display.",
                    theme = PclHintTheme.Yellow,
                )
            }
        }
        PclBottomSpacer()
    }

    // 清空背景音乐：真的把 `PCL/Music` 里的音频文件删掉（PCL 是删整个 Musics 目录）。
    if (showClearMusic) {
        PclConfirmDialog(
            title = "Clear background music",
            content = "Are you sure you want to delete all background music? ",
            confirmText = "delete",
            danger = true,
            onConfirm = {
                val deleted = PclAppearance.clearMusic(context)
                PclAppearance.persist(context)
                PclHintHost.push(
                    if (deleted > 0) {
                        "$deleted background music deleted."
                    } else {
                        "There are no files in the background music folder that can be deleted."
                    },
                    if (deleted > 0) PclHintTheme.Green else PclHintTheme.Yellow,
                )
            },
            onDismiss = { showClearMusic = false },
        )
    }
}

/* ============================================================================
 *  PageSetupSystem —— 其他（启动器）
 * ==========================================================================*/

@Composable
fun PclSetupSystemPage() {
    val context = LocalContext.current

    PclContent {
        // ---- 界面风格（PCL2 / 原生）
        PclCard( modifier = Modifier.fillMaxWidth(),title = "Interface style") {
            Column {
                SettingCheck("Use PCL2 style interface as the launcher main interface", AllSettings.usePcl2Ui)
                Spacer(Modifier.height(6.dp))
                PclHint(
                    text = "After closing and restarting the launcher, the main interface will switch back to the Zalith native interface;" +
                            "You can return here at any time in the native interface \"Settings → Launcher → Switch to PCL2 interface\"." +
                            "(Only one icon is kept on the desktop. Press this switch to decide which interface to enter.)",
                    theme = PclHintTheme.Blue,
                )
            }
        }

        Spacer(Modifier.height(PclDimens.cardGap))

        // ---- 下载源
        PclCard( modifier = Modifier.fillMaxWidth(),title = PclStrings.LangPageSetupSystemDownload) {
            Column {
                SettingCombo(
                    label = "Game content download source",
                    items = MirrorSourceType.entries.toList(),
                    selected = AllSettings.gameDownloadSource.state,
                    labelOf = { context.getString(it.textRes) },
                    onSelect = { AllSettings.gameDownloadSource.save(it) },
                )
                SettingCombo(
                    label = "Resource platform download source",
                    items = MirrorSourceType.entries.toList(),
                    selected = AllSettings.assetPlatformSource.state,
                    labelOf = { context.getString(it.textRes) },
                    onSelect = { AllSettings.assetPlatformSource.save(it) },
                )
                Spacer(Modifier.height(6.dp))
                PclHint(
                    text = "\u201cAutomatically\u201d will select the initial download source according to the current network environment, and adaptively change the source when it fails.",
                    theme = PclHintTheme.Blue,
                )
            }
        }

        Spacer(Modifier.height(PclDimens.cardGap))

        // ---- Mod 与资源搜索平台
        PclCard( modifier = Modifier.fillMaxWidth(),title = PclStrings.LangPageSetupSystemDownloadModTitle) {
            Column {
                SettingCombo(
                    label = "A platform to search for mods",
                    items = Platform.entries.toList(),
                    selected = AllSettings.searchModPlatform.state,
                    labelOf = { it.displayName },
                    onSelect = { AllSettings.searchModPlatform.save(it) },
                )
                SettingCombo(
                    label = "A platform to search for integration packages",
                    items = Platform.entries.toList(),
                    selected = AllSettings.searchModpackPlatform.state,
                    labelOf = { it.displayName },
                    onSelect = { AllSettings.searchModpackPlatform.save(it) },
                )
                SettingCombo(
                    label = "A platform to search for resource packages",
                    items = Platform.entries.toList(),
                    selected = AllSettings.searchResourcePackPlatform.state,
                    labelOf = { it.displayName },
                    onSelect = { AllSettings.searchResourcePackPlatform.save(it) },
                )
                SettingCombo(
                    label = "Search platform for light and shadow",
                    items = Platform.entries.toList(),
                    selected = AllSettings.searchShadersPlatform.state,
                    labelOf = { it.displayName },
                    onSelect = { AllSettings.searchShadersPlatform.save(it) },
                )
            }
        }

        Spacer(Modifier.height(PclDimens.cardGap))

        // ---- 游戏内悬浮窗
        PclCard( modifier = Modifier.fillMaxWidth(),title = "In-game floating window") {
            Column {
                SettingCheck("Display real-time frame rate (FPS)", AllSettings.showFPS)
                SettingCheck("Display memory usage", AllSettings.showMemory)
                SettingCheck("Show menu floating ball", AllSettings.showMenuBall)
                SetupRow("Floating ball opacity (%)") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SettingSlider(AllSettings.menuBallOpacity, 20, 100, Modifier.weight(1f))
                        PclText(
                            text = "${AllSettings.menuBallOpacity.state}%",
                            modifier = Modifier.width(52.dp).padding(start = 12.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(PclDimens.cardGap))

        // ---- 启动器信息（真实数据）
        PclLauncherInfoCard()

        Spacer(Modifier.height(PclDimens.cardGap))

        // ---- 调试选项（IsSwapped=True → 默认折叠）
        PclCard(modifier = Modifier.fillMaxWidth(),
            title = PclStrings.LangPageSetupSystemDebug,
            collapsible = true,
            initiallyCollapsed = true,
        ) {
            Column {
                SettingCombo(
                    label = "Main interface operation menu location",
                    items = ActionMenuSide.entries.toList(),
                    selected = AllSettings.launcherActionMenuSide.state,
                    labelOf = { if (it == ActionMenuSide.START) "Reading direction starting side" else "Reading direction end side" },
                    onSelect = { AllSettings.launcherActionMenuSide.save(it) },
                )
                SettingCheck("Automatically expand task menu when opening launcher", AllSettings.launcherTaskMenuExpanded)
                Spacer(Modifier.height(6.dp))
                PclHint(
                    text = "Disabled native library plug-ins: ${AllSettings.disableNativeLibPlugins.state.size}.",
                    theme = PclHintTheme.Blue,
                )
            }
        }

        Spacer(Modifier.height(PclDimens.cardGap))
    }
}

/** 启动器 / 设备真实信息。 */
@Composable
private fun PclLauncherInfoCard() {
    val context = LocalContext.current
    val p = pclPalette
    val runtime = LocalPclRuntime.current
    val versions by runtime.versions.collectAsStateWithLifecycle()
    val accounts by runtime.accounts.collectAsStateWithLifecycle()
    val current by runtime.currentVersion.collectAsStateWithLifecycle()
    val (usedMb, totalMb) = remember { memoryStats(context) }

    val info = listOf(
        "Installed version" to "${versions.size}",
        "Logged in account" to "${accounts.size}",
        "Current version" to (current?.getVersionName() ?: "Not selected"),
        "Device memory" to "%.1f GB / %.1f GB".format(usedMb / 1024f, totalMb / 1024f),
        "Device architecture" to (System.getProperty("os.arch") ?: "unknown"),
    )

    PclCard( modifier = Modifier.fillMaxWidth(),title = "Launcher information") {
        Column {
            info.forEach { (label, value) ->
                SetupRow(label) {
                    PclText(text = value, fontSize = 13, color = p.color1)
                }
            }
            Spacer(Modifier.height(8.dp))
            PclHint(
                text = "The above information is read from the current device and installed game version.",
                theme = PclHintTheme.Blue,
            )
        }
    }
}
