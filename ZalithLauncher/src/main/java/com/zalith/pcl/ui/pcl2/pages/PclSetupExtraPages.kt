/*
 * Zalith Launcher 2 — PCL2 UI port
 *
 * 设置页里 Zalith 原生独有的四张子页面。PCL2 自己的设置页只有
 * 启动 / 联机 / 个性化 / 其他（`Pages/PageSetup/PageSetup{Launch,Link,UI,System}.xaml`），
 * 这四页是原生启动器才有的能力，所以在移植版里用 **PCL 的卡片与列表项** 重做：
 *
 *   控制设置  ← 原生 `ui/screens/content/settings/ControlSettingsScreen.kt`（22 项）
 *   手柄设置  ← 原生 `ui/screens/content/settings/GamepadSettingsScreen.kt`（7 项）
 *   Java 管理 ← 原生 `ui/screens/content/settings/JavaManageScreen.kt`
 *               列表版式取自 PCL2 `PageSetupLaunch.UpdateJavaList()`
 *               ——「一行一个 Java，行尾是打开目录 / 删除两枚小按钮」
 *   控制管理  ← 原生 `ui/screens/content/settings/ControlManageScreen.kt`
 *
 * 功能、数据、落盘全部复用同一套后端，没有只改本地状态、不落盘的“空壳”控件：
 *   AllSettings（全部设置项）、RuntimesManager（Java 运行库）、ControlManager（控制布局）、
 *   GamepadViewModel + remapperMMKV（手柄映射配置）、
 *   EventViewModel（实体键盘按键捕获、控制布局导入与「打开目录」的事件通道，
 *   与原生界面走的是**同一条** Activity 级事件通道）。
 *
 * 与原生界面的差别只在版式：原生是 Material3 的 `SettingsCard`（标题 + 摘要 + 控件），
 * 这里是 PCL 的 `PclCard` + `PclCheckBox` / `PclComboBox` / `PclSlider` / `PclListItem`。
 * 原生每一项的「标题 + 摘要」在这里保留成「PCL 控件 + 其下方的灰色小字」。
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.zalith.pcl.ui.pcl2

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zalith.pcl.bridge.CursorShape
import com.zalith.pcl.contract.MediaPickerContract
import com.zalith.pcl.context.copyLocalFile
import com.zalith.pcl.context.getFileName
import com.zalith.pcl.game.control.ControlData
import com.zalith.pcl.game.control.ControlManager
import com.zalith.pcl.game.control.openDocumentStream
import com.zalith.pcl.game.multirt.Runtime
import com.zalith.pcl.game.multirt.RuntimesManager
import com.zalith.pcl.path.PathManager
import com.zalith.pcl.setting.AllSettings
import com.zalith.pcl.setting.enums.GamepadInputMode
import com.zalith.pcl.setting.enums.GestureActionType
import com.zalith.pcl.setting.enums.MouseControlMode
import com.zalith.pcl.setting.unit.AbstractSettingUnit
import com.zalith.pcl.ui.activities.startEditorActivity
import com.zalith.pcl.ui.control.GamepadBindingKeyboard
import com.zalith.pcl.ui.control.gamepad.GamepadMap
import com.zalith.pcl.ui.control.gamepad.JoystickMode
import com.zalith.pcl.ui.control.gamepad.getNameByGamepadEvent
import com.zalith.pcl.ui.control.gamepad.remapperMMKV
import com.zalith.pcl.ui.control.gyroscope.isGyroscopeAvailable
import com.zalith.pcl.ui.control.mouse.MouseHotspotEditorDialog
import com.zalith.pcl.ui.control.mouse.MousePointer
import com.zalith.pcl.ui.control.mouse.getMouseFile
import com.zalith.pcl.ui.pcl2.components.PclBottomSpacer
import com.zalith.pcl.ui.pcl2.components.PclButton
import com.zalith.pcl.ui.pcl2.components.PclCard
import com.zalith.pcl.ui.pcl2.components.PclCheckBox
import com.zalith.pcl.ui.pcl2.components.PclColorState
import com.zalith.pcl.ui.pcl2.components.PclComboBox
import com.zalith.pcl.ui.pcl2.components.PclConfirmDialog
import com.zalith.pcl.ui.pcl2.components.PclHint
import com.zalith.pcl.ui.pcl2.components.PclHintTheme
import com.zalith.pcl.ui.pcl2.components.PclIcons
import com.zalith.pcl.ui.pcl2.components.PclInputDialog
import com.zalith.pcl.ui.pcl2.components.PclListItem
import com.zalith.pcl.ui.pcl2.components.PclListItemButton
import com.zalith.pcl.ui.pcl2.components.PclListItemType
import com.zalith.pcl.ui.pcl2.components.PclNoticeDialog
import com.zalith.pcl.ui.pcl2.components.PclPillButton
import com.zalith.pcl.ui.pcl2.components.PclSlider
import com.zalith.pcl.ui.pcl2.components.PclText
import com.zalith.pcl.ui.pcl2.theme.PclDimens
import com.zalith.pcl.ui.pcl2.theme.pclPalette
import com.zalith.pcl.utils.file.checkExtensionOrThrow
import com.zalith.pcl.utils.image.isImageFile
import com.zalith.pcl.utils.logging.Logger
import com.zalith.pcl.viewmodel.EventViewModel
import com.zalith.pcl.viewmodel.GamepadViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/* ============================================================================
 *  通用小部件（与 PclSetupPages.kt 里那套同形；那边是 private，这边另起一份）
 * ==========================================================================*/

/** 设置项标签列宽度，与 `PclSetupPages.LabelWidth` 一致。 */
private val ExtraLabelWidth = 140.dp

/**
 * 控制布局「最近一次导入」的记录。
 *
 * 放文件级单例而不是 `remember`：`nav.push()` 进二级页会让当前 composable 整个离开组合，
 * 回来时 `remember` 全部作废 —— 而用户恰恰是「导入完切走、再切回来看结果」。
 *
 * 这个字段只为一件事存在：**导入没反应时，屏幕上得有东西说明卡在哪一步**
 * （没选到文件 / 读不到流 / 解析失败 / 写盘失败）。否则「列表空白」根本无从定位。
 */
private object PclControlImportLog {
    val text = mutableStateOf<String?>(null)
    fun set(value: String) { text.value = value }
}

/** 控制布局相关操作的日志标签（导入失败原因要能在「首页 → 日志」里看到）。 */
private const val LOG_TAG = "PclSetupExtraPages"

/** 摘要小字用的灰色（PCL 的 `ColorBrushGray3`）。`pclPalette` 本身是 @Composable，所以这里也用取值器。 */
private val summaryColor: Color
    @Composable get() = pclPalette.gray3

/** 设置项一行：`标签（固定宽）…… 控件`。 */
@Composable
private fun ExtraRow(
    label: String,
    modifier: Modifier = Modifier,
    control: @Composable () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PclText(text = label, modifier = Modifier.width(ExtraLabelWidth).padding(end = 20.dp))
        Box(modifier = Modifier.weight(1f)) { control() }
    }
}

/** 摘要小字，缩进与控件列对齐。 */
@Composable
private fun ExtraSummary(text: String) {
    PclText(
        text = text,
        modifier = Modifier.padding(start = ExtraLabelWidth + 20.dp, top = 2.dp, bottom = 6.dp),
        color = summaryColor,
        fontSize = 12,
    )
}

/** 复选项 + 灰色说明（原生每一项的 title / summary 对）。 */
@Composable
private fun ExtraCheck(
    text: String,
    unit: AbstractSettingUnit<Boolean>,
    summary: String = "",
    enabled: Boolean = true,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        PclCheckBox(
            text = text,
            checked = unit.state,
            enabled = enabled,
            onCheckedChange = { unit.save(it) },
        )
        if (summary.isNotEmpty()) {
            PclText(
                text = summary,
                modifier = Modifier.padding(top = 2.dp),
                color = summaryColor,
                fontSize = 12,
            )
        }
    }
}

/** 下拉框（标签在左、控件在右，选中即落盘）。 */
@Composable
private fun <T> ExtraCombo(
    label: String,
    items: List<T>,
    selected: T?,
    labelOf: (T) -> String = { it.toString() },
    enabled: Boolean = true,
    onSelect: (T) -> Unit,
) {
    ExtraRow(label) {
        PclComboBox(
            items = items,
            selected = selected,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            label = { item -> item?.let(labelOf).orEmpty() },
            onSelect = onSelect,
        )
    }
}

/** 整数滑杆：控件用 0..1 比例，这里换算成实际区间，右侧显示当前值。 */
@Composable
private fun ExtraSlider(
    label: String,
    unit: AbstractSettingUnit<Int>,
    min: Int,
    max: Int,
    suffix: String = "",
    enabled: Boolean = true,
) {
    if (max <= min) return
    val value = unit.state.coerceIn(min, max)
    ExtraRow(label) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PclSlider(
                value = ((value - min).toFloat() / (max - min)).coerceIn(0f, 1f),
                modifier = Modifier.weight(1f),
                enabled = enabled,
                onValueChange = { fraction ->
                    unit.save((min + fraction * (max - min)).roundToInt().coerceIn(min, max))
                },
            )
            PclText(
                text = "$value$suffix",
                modifier = Modifier.width(62.dp).padding(start = 12.dp),
                color = if (enabled) null else summaryColor,
            )
        }
    }
}

/* ============================================================================
 *  控制设置 —— 原生 ControlSettingsScreen
 * ==========================================================================*/

/** 控制设置里 8 种虚拟鼠标指针（顺序与原生一致）。 */
private val pointerShapes = listOf(
    CursorShape.Arrow to "Virtual mouse pointer (default)",
    CursorShape.Hand to "Virtual mouse pointer (hand shape)",
    CursorShape.IBeam to "Virtual mouse pointer (input cursor)",
    CursorShape.CrossHair to "Virtual mouse pointer (cross)",
    CursorShape.ResizeNS to "Virtual mouse pointer (up and down)",
    CursorShape.ResizeEW to "Virtual mouse pointer (left and right)",
    CursorShape.ResizeAll to "Virtual mouse pointer (movement)",
    CursorShape.NotAllowed to "Virtual mouse pointer (disabled)",
)

/**
 * 指针行内的三种操作，是原生 `MousePointerOperation` 的子集。
 *
 * ⚠️ 这里**刻意没有** `Reset` 中间态：本移植的 `PclConfirmDialog` 在点「确定」后
 * 会先调 `onConfirm`、紧接着再调 `onDismiss`，两次状态写入落在同一帧 ——
 * 中间态永远不会被组合到，`when` 里对应的分支一次也不会执行。
 * 重置的活直接在 `onConfirm` 里做。
 */
private sealed interface PointerOp {
    data object None : PointerOp
    data object PreReset : PointerOp
    data object Hotspot : PointerOp
}

@Composable
fun PclSetupControlPage() {
    val context = LocalContext.current
    val eventViewModel: EventViewModel = viewModel()
    val gyroscopeReady = remember { isGyroscopeAvailable(context) }

    // ---- 实体键盘「呼出输入法」的按键捕获。
    // 走 Activity 级事件通道：MainActivity 收到 StartKeyCapture 后把硬件按键
    // 通过 OnKeyDown 发回来 —— 与原生 `PhysicalKeyImeTrigger` 完全同一条路径。
    var capturing by remember { mutableStateOf(false) }
    if (capturing) {
        LaunchedEffect(Unit) {
            eventViewModel.sendEvent(EventViewModel.Event.Key.StartKeyCapture)
            eventViewModel.events
                .filterIsInstance<EventViewModel.Event.Key.OnKeyDown>()
                .collect { event ->
                    capturing = false
                    AllSettings.physicalKeyImeCode.save(event.key.keyCode)
                }
        }
        LaunchedEffect(Unit) {
            // 兜底：15 s 内没按下任何键就自动退出捕获，别让「正在捕获」永远挂着。
            delay(15_000)
            if (capturing) capturing = false
        }
        DisposableEffect(Unit) {
            onDispose { eventViewModel.sendEvent(EventViewModel.Event.Key.StopKeyCapture) }
        }
    }

    PclContent {
        // ---- 实体鼠标
        PclCard(modifier = Modifier.fillMaxWidth(), title = "physical mouse") {
            Column {
                ExtraCheck(
                    text = "Physical mouse control",
                    unit = AllSettings.physicalMouseMode,
                    summary = "When connecting a physical mouse, whether to disable the virtual mouse pointer and directly use the system mouse pointer for control.",
                )
                Spacer(Modifier.height(8.dp))
                ExtraRow("Call out input method keys") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val code = AllSettings.physicalKeyImeCode.state
                        PclButton(
                            text = when {
                                capturing -> "Please press the key you want to bind..."
                                code == null -> "Click the bind button"
                                else -> "Bound keys: ${keyCodeName(code)}"
                            },
                            colorState = if (capturing) PclColorState.Highlight else PclColorState.Normal,
                            onClick = { capturing = !capturing },
                        )
                        if (code != null && !capturing) {
                            Spacer(Modifier.width(8.dp))
                            PclButton(
                                text = "Clear",
                                colorState = PclColorState.Red,
                                onClick = { AllSettings.physicalKeyImeCode.save(null) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                PclHint(
                    text = "After binding, pressing this button in the game can call up the input method." +
                            "If the device recognizes the right button of the physical mouse as \"Return\" and the middle button as \"Return to Desktop\"," +
                            "You can go to System Settings > Developer Options to turn on \"Disable Permission Monitoring\", which will most likely solve the problem.",
                    theme = PclHintTheme.Blue,
                )
            }
        }

        Spacer(Modifier.height(PclDimens.cardGap))

        // ---- 虚拟鼠标指针
        PclCard(modifier = Modifier.fillMaxWidth(), title = "virtual mouse pointer") {
            Column {
                ExtraSlider(
                    label = "Virtual mouse size (Dp)",
                    unit = AllSettings.mouseSize,
                    min = 5,
                    max = 50,
                )
                Spacer(Modifier.height(4.dp))
                PclHint(
                    text = "Click on any item to replace it with your own picture; \"Set Hotspot\" is used to specify the image" +
                            "The point that truly represents the pointer position; \"Reset\" returns it to the image that comes with the launcher.",
                    theme = PclHintTheme.Blue,
                )
                Spacer(Modifier.height(8.dp))
                pointerShapes.forEach { (shape, title) ->
                    PclPointerRow(title = title, shape = shape)
                }
            }
        }

        Spacer(Modifier.height(PclDimens.cardGap))

        // ---- 虚拟鼠标
        PclCard(modifier = Modifier.fillMaxWidth(), title = "virtual mouse") {
            Column {
                ExtraCheck(
                    text = "Hide virtual mouse",
                    unit = AllSettings.hideMouse,
                    summary = "In some scenarios you may want to hide the virtual mouse; this setting will not affect physical mouse control or sliding control.",
                )
                ExtraCheck(
                    text = "Touchpad operation",
                    unit = AllSettings.enableMouseClick,
                    summary = "Clicking on the screen is regarded as the left mouse button, and long pressing is regarded as a long press of the left button;" +
                            "This setting will not affect physical mouse control and click control.",
                )
                Spacer(Modifier.height(6.dp))
                ExtraCombo(
                    label = "Virtual mouse control mode",
                    items = MouseControlMode.entries.toList(),
                    selected = AllSettings.mouseControlMode.state,
                    labelOf = { context.getString(it.nameRes) },
                    onSelect = { AllSettings.mouseControlMode.save(it) },
                )
                ExtraSummary("Choose how the virtual mouse operates: slide the screen to move the cursor, or click on the screen to specify a location.")
                ExtraSlider(
                    label = "Virtual mouse sensitivity",
                    unit = AllSettings.cursorSensitivity,
                    min = 25,
                    max = 300,
                    suffix = "%",
                )
                ExtraSlider(
                    label = "Capture mouse sliding sensitivity",
                    unit = AllSettings.mouseCaptureSensitivity,
                    min = 25,
                    max = 300,
                    suffix = "%",
                )
                ExtraSlider(
                    label = "Delay triggered by virtual mouse long press",
                    unit = AllSettings.mouseLongPressDelay,
                    min = 100,
                    max = 1000,
                    suffix = " ms",
                )
            }
        }

        Spacer(Modifier.height(PclDimens.cardGap))

        // ---- 手势
        PclCard(modifier = Modifier.fillMaxWidth(), title = "gesture") {
            Column {
                ExtraCheck(
                    text = "gesture control",
                    unit = AllSettings.gestureControl,
                    summary = "Supports interaction in the game through gestures such as clicks, long presses, and two-finger slide scrolling, similar to Bedrock Edition.",
                )
                Spacer(Modifier.height(6.dp))
                ExtraCombo(
                    label = "Action type triggered by click",
                    items = GestureActionType.entries.toList(),
                    selected = AllSettings.gestureTapMouseAction.state,
                    labelOf = { context.getString(it.nameRes) },
                    onSelect = { AllSettings.gestureTapMouseAction.save(it) },
                )
                ExtraSummary("Select the mouse button action that should be triggered when the screen is tapped.")
                ExtraCombo(
                    label = "Operation type triggered by long press",
                    items = GestureActionType.entries.toList(),
                    selected = AllSettings.gestureLongPressMouseAction.state,
                    labelOf = { context.getString(it.nameRes) },
                    onSelect = { AllSettings.gestureLongPressMouseAction.save(it) },
                )
                ExtraSummary("Select the mouse button action that should be triggered when long pressing the screen.")
                ExtraSlider(
                    label = "Delay triggered by gesture long press",
                    unit = AllSettings.gestureLongPressDelay,
                    min = 100,
                    max = 1000,
                    suffix = " ms",
                )
            }
        }

        Spacer(Modifier.height(PclDimens.cardGap))

        // ---- 陀螺仪
        PclCard(modifier = Modifier.fillMaxWidth(), title = "gyroscope") {
            Column {
                ExtraCheck(
                    text = "Gyro control",
                    unit = AllSettings.gyroscopeControl,
                    summary = "Control the perspective in the game by turning your device.",
                    enabled = gyroscopeReady,
                )
                if (!gyroscopeReady) {
                    Spacer(Modifier.height(6.dp))
                    PclHint(text = "If the gyroscope is not detected on the current device, this setting will not take effect.", theme = PclHintTheme.Red)
                }
                val on = gyroscopeReady && AllSettings.gyroscopeControl.state
                Spacer(Modifier.height(6.dp))
                ExtraSlider(
                    label = "Gyro control sensitivity",
                    unit = AllSettings.gyroscopeSensitivity,
                    min = 25,
                    max = 300,
                    suffix = "%",
                    enabled = on,
                )
                ExtraSlider(
                    label = "Gyroscope sampling rate",
                    unit = AllSettings.gyroscopeSampleRate,
                    min = 5,
                    max = 50,
                    enabled = on,
                )
                ExtraCheck(
                    text = "Gyroscope numerical smoothing",
                    unit = AllSettings.gyroscopeSmoothing,
                    summary = "Smoothing the values ​​detected by the gyroscope reduces jitter but increases latency.",
                    enabled = on,
                )
                ExtraSlider(
                    label = "Smoothing window size",
                    unit = AllSettings.gyroscopeSmoothingWindow,
                    min = 2,
                    max = 10,
                    enabled = on && AllSettings.gyroscopeSmoothing.state,
                )
                ExtraCheck(
                    text = "Invert X-axis",
                    unit = AllSettings.gyroscopeInvertX,
                    summary = "When enabled, turning the device left or right will control the viewing angle in reverse.",
                    enabled = on,
                )
                ExtraCheck(
                    text = "Invert Y axis",
                    unit = AllSettings.gyroscopeInvertY,
                    summary = "When enabled, turning the device up or down will control the viewing angle in reverse.",
                    enabled = on,
                )
            }
        }

        Spacer(Modifier.height(PclDimens.cardGap))
        PclBottomSpacer()
    }
}

/** 一枚虚拟鼠标指针：预览图 + 更换图片 / 设置热点 / 重置。 */
@Composable
private fun PclPointerRow(title: String, shape: CursorShape) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val file = getMouseFile(shape)

    var refresh by remember { mutableIntStateOf(0) }
    var exists by remember { mutableStateOf(false) }
    LaunchedEffect(file, refresh) {
        exists = withContext(Dispatchers.IO) { file.exists() && file.length() > 0L }
    }

    var op by remember { mutableStateOf<PointerOp>(PointerOp.None) }

    val picker = rememberLauncherForActivityResult(
        contract = MediaPickerContract(allowImages = true, allowVideos = false),
    ) { result ->
        val uri = result?.firstOrNull() ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { context.copyLocalFile(uri, file) }
                if (!withContext(Dispatchers.IO) { file.isImageFile() }) {
                    error("The selected file is not a recognized image.")
                }
            }.onSuccess {
                refresh++
            }.onFailure { th ->
                withContext(Dispatchers.IO) { file.delete() }
                PclHintHost.push("Failed to replace pointer image: ${th.message ?:"未知错误"}", PclHintTheme.Red)
            }
        }
    }

    PclListItem(
        title = title,
        modifier = Modifier.fillMaxWidth(),
        info = if (exists) "Custom image used" else "Launcher comes with pictures",
        logoContent = {
            Box(modifier = Modifier.align(Alignment.Center)) {
                MousePointer(
                    mouseSize = 22.dp,
                    cursorShape = shape,
                    mouseFile = file,
                    centerIcon = true,
                    triggerRefresh = refresh,
                )
            }
        },
        type = PclListItemType.Clickable,
        height = 48.dp,
        buttons = buildList {
            add(
                PclListItemButton(
                    logo = PclIcons.replaceImage,
                    tooltip = "Change picture",
                    onClick = { picker.launch(Unit) },
                )
            )
            add(
                PclListItemButton(
                    logo = PclIcons.hotspot,
                    tooltip = "Set up hotspot",
                    onClick = { op = PointerOp.Hotspot },
                )
            )
            if (exists) {
                add(
                    PclListItemButton(
                        logo = PclIcons.restartAlt,
                        tooltip = "Reset to own image",
                        onClick = { op = PointerOp.PreReset },
                    )
                )
            }
        },
        onClick = { picker.launch(Unit) },
    )

    when (op) {
        PointerOp.None -> {}

        PointerOp.PreReset -> PclConfirmDialog(
            title = "reset",
            content = "Are you sure you want to reset the virtual mouse pointer image?",
            // ⚠️ 不能照抄原生的写法。
            //
            // 原生 `MousePointerOperation` 是「确定 → 切成中间态 Reset → 由 Reset 分支
            // 的 `LaunchedEffect` 干活」；它用的是 `SimpleAlertDialog`，那个的 onConfirm
            // 之后不会自动再调 onDismiss。
            //
            // 本移植的 `PclConfirmDialog` 点「确定」时会 **先 onConfirm、紧接着再 onDismiss**
            // （见 `PclDialogs.kt` 里 `PclMsgButton` 的回调）。于是 `op = Reset` 和
            // `op = None` 落在同一帧里，组合永远看不到 `Reset`，那个分支一次都不会执行 ——
            // 表现就是用户报的「替换图标后按下重置没有反应」。
            //
            // 直接在 onConfirm 里把活干完，不再经过中间态。
            onConfirm = {
                scope.launch {
                    withContext(Dispatchers.IO) { file.delete() }
                    refresh++
                }
            },
            onDismiss = { op = PointerOp.None },
        )

        PointerOp.Hotspot -> MouseHotspotEditorDialog(
            hotspot = hotspotUnitOf(shape),
            cursorShape = shape,
            onClose = { op = PointerOp.None },
        )
    }
}

/** 与原生 `getMouseHotspot` 同一张表：形状 → 热点设置项。 */
@Composable
private fun hotspotUnitOf(shape: CursorShape) = when (shape) {
    CursorShape.Arrow -> AllSettings.arrowMouseHotspot
    CursorShape.IBeam -> AllSettings.iBeamMouseHotspot
    CursorShape.Hand -> AllSettings.linkMouseHotspot
    CursorShape.CrossHair -> AllSettings.crossHairMouseHotspot
    CursorShape.ResizeNS -> AllSettings.resizeNSMouseHotspot
    CursorShape.ResizeEW -> AllSettings.resizeEWMouseHotspot
    CursorShape.ResizeAll -> AllSettings.resizeAllMouseHotspot
    CursorShape.NotAllowed -> AllSettings.notAllowedMouseHotspot
}

/** Android 键值 → 人类可读的按键名。 */
private fun keyCodeName(code: Int): String {
    val raw = android.view.KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")
    return when (raw) {
        "VOLUME_UP" -> "Volume keys (upper)"
        "VOLUME_DOWN" -> "Volume keys (lower)"
        "MENU" -> "Menu key"
        "BACK" -> "return key"
        "SPACE" -> "space"
        "ENTER" -> "Enter"
        "TAB" -> "Tab"
        else -> raw.replace('_', ' ')
    }
}

/* ============================================================================
 *  手柄设置 —— 原生 GamepadSettingsScreen
 * ==========================================================================*/

@Composable
fun PclSetupGamepadPage() {
    val context = LocalContext.current
    val gamepadViewModel: GamepadViewModel = viewModel()

    var configToken by remember { mutableIntStateOf(0) }
    var newConfigDialog by remember { mutableStateOf(false) }
    var deleteConfigTarget by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    // 逐键绑定。`GamepadViewModel.currentMapping` 与 `GamepadMappingList.findByMap()` 读的都是
    // **普通字段、不是 Compose 状态**（映射表内部是一个普通 MutableMap），改完之后框架不会知道，
    // 所以每次保存/重置都要自己递增一个 token 触发重算 —— 原生也是这么干的（`refreshed = refreshed.not()`）。
    var bindToken by remember { mutableIntStateOf(0) }
    // 映射分「游戏内」与「菜单内」两套（`GamepadMapping.targetsInGame / targetsInMenu`），原生用两枚 CheckChip 切换。
    var editKeyInGame by remember { mutableStateOf(true) }
    // 非空时弹出逐键绑定键盘（对应原生的 `BindKeyOperation.OnBind`）。
    var bindingTarget by remember { mutableStateOf<GamepadMap?>(null) }

    // `getAllConfigKeys()` 读的是 GamepadViewModel 内部那份 MMKV 缓存，
    // 必须先 `reloadAllMappings()` 才是最新的；而它**不是 Compose 状态**，
    // 所以「先落进一个 state，再渲染」—— 不能写成 `remember(token){ getAllConfigKeys() }`，
    // 那样首帧拿到的是空列表、后面 reload 完了也不会重算。
    var configs by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(configToken) {
        gamepadViewModel.reloadAllMappings()
        configs = gamepadViewModel.getAllConfigKeys()
        // 换配置后 `currentMapping` 指向了另一份映射表，逐键列表必须重算 ——
        // 而 `reloadAllMappings()` 不是状态写入，框架不会自己感知，得由这个 token 顶上。
        bindToken++
    }
    val current = AllSettings.gamepadMappingConfig.state

    // 原生语义：手柄被关闭、或改成 SDL 直通时，映射相关的项一律不可用。
    val remapEnabled = AllSettings.gamepadControl.state &&
            AllSettings.gamepadInputMode.state == GamepadInputMode.Mapped

    PclContent {
        // ---- 手柄控制
        PclCard(modifier = Modifier.fillMaxWidth(), title = "joystick control") {
            Column {
                ExtraCheck(
                    text = "Enable gamepad controls",
                    unit = AllSettings.gamepadControl,
                    summary = "If your other peripherals are mistakenly recognized as controllers, please turn off this option.",
                )
            }
        }

        Spacer(Modifier.height(PclDimens.cardGap))

        // ---- 输入模式
        PclCard(modifier = Modifier.fillMaxWidth(), title = "input mode") {
            Column {
                ExtraCombo(
                    label = "Gamepad input mode",
                    items = GamepadInputMode.entries.toList(),
                    selected = AllSettings.gamepadInputMode.state,
                    labelOf = { context.getString(it.titleRes) },
                    onSelect = { AllSettings.gamepadInputMode.save(it) },
                )
                ExtraSummary(context.getString(AllSettings.gamepadInputMode.state.summaryRes))
                PclHint(
                    text = "After switching the input mode, you need to restart the game for it to take effect." +
                            "Mapping mode is suitable for games that do not support controllers; SDL passthrough delivers raw controller input directly to the game" +
                            "(Minecraft 26.3+ native controller), the key mapping will no longer take effect at this time.",
                    theme = PclHintTheme.Blue,
                )
            }
        }

        Spacer(Modifier.height(PclDimens.cardGap))

        // ---- 摇杆
        PclCard(modifier = Modifier.fillMaxWidth(), title = "joystick") {
            Column {
                ExtraCombo(
                    label = "Joystick operation mode",
                    items = JoystickMode.entries.toList(),
                    selected = AllSettings.joystickControlMode.state,
                    labelOf = { context.getString(it.titleRes) },
                    onSelect = { AllSettings.joystickControlMode.save(it) },
                )
                ExtraSummary(context.getString(AllSettings.joystickControlMode.state.summaryRes))
                ExtraSlider(
                    label = "Controller dead zone scaling",
                    unit = AllSettings.gamepadDeadZoneScale,
                    min = 50,
                    max = 200,
                    suffix = "%",
                )
                ExtraSlider(
                    label = "Joystick pointer sensitivity",
                    unit = AllSettings.gamepadCursorSensitivity,
                    min = 25,
                    max = 300,
                    suffix = "%",
                )
                ExtraSlider(
                    label = "Joystick viewing angle sensitivity",
                    unit = AllSettings.gamepadCameraSensitivity,
                    min = 25,
                    max = 300,
                    suffix = "%",
                )
            }
        }

        Spacer(Modifier.height(PclDimens.cardGap))

        // ---- 按键映射
        PclCard(modifier = Modifier.fillMaxWidth(), title = "Key mapping") {
            Column {
                ExtraCombo(
                    label = "Controller mapping configuration",
                    items = configs,
                    selected = current.takeIf { it in configs },
                    labelOf = { it },
                    enabled = remapEnabled,
                    onSelect = {
                        AllSettings.gamepadMappingConfig.save(it)
                        gamepadViewModel.reloadAllMappings()
                        configToken++
                    },
                )
                if (configs.isEmpty()) {
                    ExtraSummary("No mapping configuration has been created yet, please create a new one first.")
                }
                ExtraRow("Management configuration") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PclButton(
                            text = "New configuration",
                            enabled = remapEnabled,
                            onClick = { newConfigDialog = true },
                        )
                        PclButton(
                            text = "Delete current configuration",
                            enabled = remapEnabled && current in configs,
                            colorState = PclColorState.Red,
                            onClick = { deleteConfigTarget = current },
                        )
                    }
                }
                ExtraRow("Key mapping") {
                    PclButton(
                        text = "Reset controller key mapping",
                        enabled = remapEnabled,
                        colorState = PclColorState.Red,
                        onClick = { confirmReset = true },
                    )
                }
                Spacer(Modifier.height(6.dp))
                PclHint(
                    text = "Button mapping can help better adapt to your controller: the launcher will ask you to press the key buttons of the controller in sequence." +
                            "Align the key values ​​of different brands of controllers into the same set. " +
                            "You can reset and restart the game and bind again.",
                    theme = PclHintTheme.Blue,
                )
            }
        }

        Spacer(Modifier.height(PclDimens.cardGap))

        // ---- 按键绑定
        // 对应原生 `GamepadSettingsScreen` 里 `BindKeyOperation` 那一段：先选「游戏内 / 菜单内」，
        // 然后一个手柄按键一行，点开 `GamepadBindingKeyboard` 挑要触发的键盘/鼠标操作。
        // 手柄按键 → 键盘按键的映射表存在 `GamepadMappingList` 里（`targetsInGame` / `targetsInMenu`，
        // 落盘在 keyMappingListMMKV），和原生读写的完全是同一份数据。
        PclCard(modifier = Modifier.fillMaxWidth(), title = "Keybindings") {
            Column {
                ExtraRow("Scope") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PclPillButton(
                            text = "In game",
                            checked = editKeyInGame,
                            // `highlight = true` 不能省：`PclPillButton` 默认那套配色
                            // （白字 + 透明底）对应 `MyRadioButton.ColorType=White`，是给顶部**蓝色标题栏**
                            // 上的胶囊用的；照搬到白卡片里就是白字白底 —— 「菜单内」会整块看不见。
                            // PCL 里所有「浅色内容区里多选一」的胶囊都写 ColorType=Highlight
                            // （登录方式选择、实例列表筛选、资源详情页），这里同例。
                            highlight = true,
                            enabled = remapEnabled,
                            onClick = { editKeyInGame = true },
                        )
                        PclPillButton(
                            text = "within menu",
                            checked = editKeyInGame.not(),
                            highlight = true,
                            enabled = remapEnabled,
                            onClick = { editKeyInGame = false },
                        )
                    }
                }
                ExtraSummary(
                    "Click a row to specify which keyboard/mouse operations this controller button should trigger;" +
                            "The reset button at the end of the line returns this item to its default keystrokes."
                )
                Spacer(Modifier.height(4.dp))

                GamepadMap.entries.forEach { item ->
                    // `findByMap()` 的结果不会自己刷新（映射表内部是普通 MutableMap），
                    // 因此必须拿 `bindToken` / `editKeyInGame` 当 key 重算。
                    val codes = remember(bindToken, editKeyInGame, item) {
                        gamepadViewModel.currentMapping
                            ?.findByMap(item, inGame = editKeyInGame)
                            ?.toList()
                            ?: emptyList()
                    }
                    // `getNameByGamepadEvent` 是 @Composable（要用 stringResource），
                    // 不能塞进 `joinToString` 这类非 inline lambda，只能显式循环。
                    val names = StringBuilder()
                    for (code in codes) {
                        if (names.isNotEmpty()) names.append("、")
                        names.append(getNameByGamepadEvent(code))
                    }

                    PclListItem(
                        title = item.pclKeyDisplayName,
                        info = if (names.isEmpty()) "No key value is bound" else "Bound: $names",
                        logoContent = {
                            Image(
                                modifier = Modifier.fillMaxSize(),
                                painter = painterResource(item.getIconRes()),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                            )
                        },
                        type = PclListItemType.Clickable,
                        height = 42.dp,
                        buttons = listOf(
                            PclListItemButton(
                                logo = PclIcons.reset,
                                tooltip = "reset",
                                // 与左栏「初始化本页设置」的刷新按钮同一个 LogoScale（PCL 源码里 0.9）。
                                logoScale = 0.9f,
                                onClick = {
                                    gamepadViewModel.currentMapping?.resetMapping(item, editKeyInGame)
                                    bindToken++
                                },
                            ),
                        ),
                        onClick = if (remapEnabled) ({ bindingTarget = item }) else null,
                    )
                }
            }
        }

        Spacer(Modifier.height(PclDimens.cardGap))
        PclBottomSpacer()
    }

    if (newConfigDialog) {
        PclInputDialog(
            title = "Create a controller mapping configuration",
            label = "Configuration name",
            onConfirm = { name ->
                gamepadViewModel.createNewConfig(
                    name = name,
                    onContainsConfig = { PclHintHost.push("A configuration with the same name already exists.", PclHintTheme.Red) },
                    onFinished = { configToken++ },
                )
            },
            onDismiss = { newConfigDialog = false },
        )
    }

    deleteConfigTarget?.let { name ->
        PclConfirmDialog(
            title = "Delete configuration",
            content = "Are you sure you want to delete the configuration \"$name\"?",
            danger = true,
            onConfirm = {
                gamepadViewModel.deleteConfig(name) { configToken++ }
                deleteConfigTarget = null
            },
            onDismiss = { deleteConfigTarget = null },
        )
    }

    if (confirmReset) {
        PclConfirmDialog(
            title = "Reset controller key mapping",
            content = "All bound controller key mappings will be cleared when you re-enter the game." +
                    "The launcher will guide you to bind again. ",
            danger = true,
            onConfirm = {
                remapperMMKV().clearAll()
                PclHintHost.push("All controller key mappings have been reset.", PclHintTheme.Blue)
                confirmReset = false
            },
            onDismiss = { confirmReset = false },
        )
    }

    bindingTarget?.let { item ->
        // 与原生 `BindKeyOperation.OnBind` 完全同构：
        // 打开时把当前已绑定的键拷成一份本地可变清单，之后每次增删都**立刻**落盘
        // （`saveMapping()` 内部会 `save()` 到 MMKV），关掉键盘不需要再确认。
        val mapping = gamepadViewModel.currentMapping
        val selectedKeys = remember(item, editKeyInGame) {
            (
                    mapping?.findByMap(item, inGame = editKeyInGame)?.toList()
                            ?: emptyList()
                    ).toMutableList()
        }

        GamepadBindingKeyboard(
            selectedKeys = selectedKeys,
            onKeyAdd = { key ->
                selectedKeys.add(key)
                mapping?.saveMapping(item, selectedKeys.toSet(), editKeyInGame)
                bindToken++
            },
            onKeyRemove = { key ->
                selectedKeys.remove(key)
                mapping?.saveMapping(item, selectedKeys.toSet(), editKeyInGame)
                bindToken++
            },
            onDismissRequest = { bindingTarget = null },
        )
    }
}

/**
 * 手柄按键的中文名。
 *
 * 原生界面只用图标表示手柄按键（`GamepadMap.getIconRes()`），没有文字；
 * 而 PCL 的列表项是「文字为主、图标只是前缀」，所以这里补一份名字。
 */
private val GamepadMap.pclKeyDisplayName: String
    get() = when (this) {
        GamepadMap.ButtonA -> "A key"
        GamepadMap.ButtonB -> "B key"
        GamepadMap.ButtonX -> "X key"
        GamepadMap.ButtonY -> "Y key"
        GamepadMap.ButtonStart -> "Start (menu) key"
        GamepadMap.ButtonSelect -> "Select (view) key"
        GamepadMap.ButtonLB -> "LB left shoulder button"
        GamepadMap.ButtonRB -> "RB right shoulder button"
        GamepadMap.ButtonLT -> "LT left trigger"
        GamepadMap.ButtonRT -> "RT right trigger"
        GamepadMap.DPadUp -> "Arrow keys up"
        GamepadMap.DPadLeft -> "Arrow key left"
        GamepadMap.DPadDown -> "Arrow keys down"
        GamepadMap.DPadRight -> "Arrow key right"
        GamepadMap.StickLeftClick -> "Left joystick press"
        GamepadMap.StickRightClick -> "Right joystick press"
    }

/* ============================================================================
 *  Java 管理 —— 原生 JavaManageScreen（列表版式取自 PCL2 的 Java 选择列表）
 * ==========================================================================*/

@Composable
fun PclSetupJavaPage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val eventViewModel: EventViewModel = viewModel()

    var runtimes by remember { mutableStateOf<List<Runtime>>(emptyList()) }
    var token by remember { mutableIntStateOf(0) }
    var deleteTarget by remember { mutableStateOf<Runtime?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(token) {
        loading = true
        runtimes = withContext(Dispatchers.IO) {
            runCatching { RuntimesManager.getRuntimes(forceLoad = true) }.getOrDefault(emptyList())
        }
        loading = false
    }

    // 导入 .xz 运行库：与原生 `progressRuntimeUri` 同一条链路
    // （checkExtensionOrThrow → installRuntime → 失败提示 → 完成后刷新）。
    val importPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = context.getFileName(uri)
        if (name.isNullOrBlank()) {
            PclHintHost.push("The filename of the selected file cannot be read.", PclHintTheme.Red)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            runCatching { name.checkExtensionOrThrow(listOf("xz")) }.onFailure {
                PclHintHost.push("Please select the Java runtime in .xz format.", PclHintTheme.Red)
                return@launch
            }
            withContext(Dispatchers.IO) {
                runCatching {
                    val stream = context.contentResolver.openInputStream(uri)
                        ?: error("The selected file cannot be read.")
                    RuntimesManager.installRuntime(
                        nativeLibDir = PathManager.DIR_NATIVE_LIB,
                        inputStream = stream,
                        name = name,
                    )
                }.onSuccess {
                    withContext(Dispatchers.Main) {
                        token++
                        PclHintHost.push("The import of runtime library \"$name\" is completed.", PclHintTheme.Blue)
                    }
                }.onFailure { th ->
                    withContext(Dispatchers.Main) {
                        PclHintHost.push("Import failed: ${th.message ?:"未知错误"}", PclHintTheme.Red)
                    }
                }
            }
        }
    }

    val selected = AllSettings.javaRuntime.state
    // 勾上「自动选择合适的 Java 环境」之后，下面那张运行库列表就只是给用户看现状的：
    // 既然要自动挑，还让人手动选一个就是自相矛盾（最后到底用了哪个谁也说不清）。
    // 所以自动选择开启时把整张列表灰掉并禁止点选 —— 取消勾选即恢复可点。
    val autoPick = AllSettings.autoPickJavaRuntime.state

    PclContent {
        // ---- Java 选择策略
        PclCard(modifier = Modifier.fillMaxWidth(), title = "Java selection") {
            Column {
                ExtraCheck(
                    text = "Automatically select the appropriate Java environment",
                    unit = AllSettings.autoPickJavaRuntime,
                    summary = "When you start the game after turning it on, the Java version required by the game will be automatically selected;" +
                            "After closing, use the runtime selected in the list below.",
                )
                Spacer(Modifier.height(6.dp))
                PclHint(
                    text = if (autoPick) {
                        "Automatic selection has been turned on, and the runtime library list below is for viewing only; you can manually specify it after unchecking it."
                    } else {
                        "Currently selected:" +
                                (runtimes.firstOrNull { it.name == selected }?.name
                                        ?: "Not specified (processed by automatic selection)")
                    },
                    theme = PclHintTheme.Blue,
                )
            }
        }

        Spacer(Modifier.height(PclDimens.cardGap))

        // ---- 运行库列表
        PclCard(modifier = Modifier.fillMaxWidth(), title = "Java runtime") {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PclButton(
                        text = "refresh",
                        logo = PclIcons.refresh,
                        onClick = { token++ },
                    )
                    PclButton(
                        text = "Import runtime library (.xz)",
                        logo = PclIcons.addBox,
                        // 用 `*/*`：.xz 的 MIME 各机型给得不一样（application/x-xz /
                        // application/octet-stream 都有），限死类型会直接看不到文件。
                        onClick = { importPicker.launch("*/*") },
                    )
                }
                Spacer(Modifier.height(8.dp))

                when {
                    loading -> PclText(text = "Scanning runtime library...", color = summaryColor, fontSize = 12)

                    runtimes.isEmpty() -> PclText(
                        text = "No Java runtime found. " +
                                "Or install a game version first and let the launcher download it automatically.",
                        color = summaryColor,
                        fontSize = 12,
                    )

                    else -> runtimes.forEach { runtime ->
                        PclJavaRow(
                            runtime = runtime,
                            checked = runtime.name == selected,
                            enabled = !autoPick,
                            onSelect = { AllSettings.javaRuntime.save(runtime.name) },
                            onOpenFolder = {
                                eventViewModel.sendEvent(
                                    EventViewModel.Event.OpenFileManager(
                                        rootPath = PathManager.DIR_MULTIRT.absolutePath,
                                        currentPath = File(PathManager.DIR_MULTIRT, runtime.name).absolutePath,
                                    )
                                )
                            },
                            onDelete = { deleteTarget = runtime },
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                PclHint(
                    text = "The runtime library \"comes with the launcher\" is automatically downloaded by Zalith. After deletion, it will be downloaded again the next time you start the game;" +
                            "The runtime library you imported can be safely deleted.",
                    theme = PclHintTheme.Blue,
                )
            }
        }

        Spacer(Modifier.height(PclDimens.cardGap))
        PclBottomSpacer()
    }

    deleteTarget?.let { runtime ->
        PclConfirmDialog(
            title = "Delete runtime library",
            content = "Are you sure you want to delete \"${runtime.name}\"?",
            danger = true,
            onConfirm = {
                val name = runtime.name
                deleteTarget = null
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) { RuntimesManager.removeRuntime(name) } }
                        .onSuccess { token++ }
                        .onFailure { th ->
                            PclHintHost.push("Deletion failed: ${th.message ?:"未知错误"}", PclHintTheme.Red)
                        }
                }
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

/** 一行 Java 运行库：版本信息 + 打开目录 / 删除。 */
@Composable
private fun PclJavaRow(
    runtime: Runtime,
    checked: Boolean,
    /** 开启「自动选择」时整行灰掉、点不中（单选、行内按钮一并失效）。 */
    enabled: Boolean,
    onSelect: () -> Unit,
    onOpenFolder: () -> Unit,
    onDelete: () -> Unit,
) {
    PclListItem(
        title = runtime.name,
        modifier = Modifier.fillMaxWidth(),
        info = runtimeInfo(runtime),
        logo = PclIcons.setupJava,
        logoScale = 0.8f,
        type = PclListItemType.RadioBox,
        checked = checked,
        height = 50.dp,
        enabled = enabled,
        buttons = listOf(
            PclListItemButton(
                logo = PclIcons.openFolder,
                tooltip = "Open the directory",
                onClick = onOpenFolder,
            ),
            PclListItemButton(
                logo = PclIcons.delete,
                tooltip = "Delete this runtime library",
                onClick = onDelete,
            ),
        ),
        onClick = onSelect,
    )
}

/** 运行库的一行描述，对应原生 `JavaRuntimeItem` 里的那几个标签。 */
private fun runtimeInfo(runtime: Runtime): String = buildString {
    if (runtime.versionString == null) {
        append("The version information is corrupted and will not be used at startup")
    } else {
        append(runtime.versionString)
        runtime.arch?.let { append(" · ").append(it) }
        if (runtime.javaVersion != 0) append(" · Java ").append(runtime.javaVersion)
        if (!runtime.isCompatible()) append("· Does not match native architecture")
    }
    if (runtime.isProvidedByLauncher) append("· Comes with launcher")
}

/* ============================================================================
 *  控制管理 —— 原生 ControlManageScreen
 * ==========================================================================*/

@Composable
fun PclSetupControlManagePage() {
    val context = LocalContext.current
    val dataList by ControlManager.dataList.collectAsStateWithLifecycle()
    val isRefreshing by ControlManager.isRefreshing.collectAsStateWithLifecycle()

    var createDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ControlData?>(null) }
    /**
     * 导入失败的具体原因。
     *
     * 【别只推左下角 hint】「缺 editorVersion」「JSON 损坏」「版本过新」这些是用户唯一
     * 能拿去排查的线索，hint 三秒就没了，观感上跟「点了没反应」一模一样 ——
     * 第 213 轮就是栽在这儿。改成必须点掉的提示框。
     */
    var importError by remember { mutableStateOf<String?>(null) }
    // 最近一次导入的结果：放在文件级单例里，切走再回来也还在
    val lastImportResult by PclControlImportLog.text

    // 原生界面进入这一页时会先确保至少有一份默认布局（`checkDefaultAndRefresh`），
    // 否则全新安装的用户会看到一份空列表、没得选。
    LaunchedEffect(Unit) { ControlManager.checkDefaultAndRefresh(context) }

    val selectedName = AllSettings.controlLayout.state

    // 【不能走 `Event.ImportControls`】那是 Activity 级事件通道，只有 `MainActivity` 在
    // `lifecycleScope` 里收 —— PCL 界面跑在 `Pcl2Activity` 里，事件发出去没人接，
    // 表现就是「选完 json 文件，列表里什么都没多出来」。
    // 这里直接调 `ControlManager.importControl`（和 `PclExternalImport` 的外部导入同一条路），
    // 导完自己 refresh 一次列表。
    val importScope = rememberCoroutineScope()
    val importPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isEmpty()) {
            // 【别再静默 return】选择器没给回 uri（用户点了取消 / 某些文件管理器返回空）
            // 和「导入失败」在界面上长得一模一样，必须能区分开
            PclControlImportLog.set("Not getting file: The file selector did not return a readable address.")
            return@rememberLauncherForActivityResult
        }
        PclControlImportLog.set("${uris.size} files selected, importing...")
        importScope.launch {
            var imported = 0
            val failures = mutableListOf<String>()
            withContext(Dispatchers.IO) {
                uris.forEach { uri ->
                    // 提示里带上文件名：一次选好几个文件时，不然根本不知道是哪个出的问题
                    val displayName = context.getFileName(uri) ?: "selected file"
                    runCatching {
                        val stream = openDocumentStream(context, uri)
                            ?: error("If you cannot read this file, it may be that the file manager does not give you read permission.")
                        var parseError: String? = null
                        stream.use {
                            ControlManager.importControl(
                                inputStream = it,
                                // `ControlManager` 的消息里已经写明「文件已保存，但读不出来：…」，
                                // 直接透传，别再套一层
                                onSerializationError = { e ->
                                    parseError = e.message ?: "The file has been saved but cannot be read."
                                },
                                catchedError = { e ->
                                    parseError = "Failed to write disk: ${e.message}"
                                },
                            )
                        }
                        parseError?.let { message -> error(message) }
                        imported++
                    }.onFailure {
                        val reason = it.message ?: it.javaClass.simpleName
                        failures += "「$displayName」$reason"
                        Logger.warning(LOG_TAG, "Control layout import failed: $displayName/$uri", it)
                    }
                }
                // 列表要重新扫一遍：`dataList` 是 ControlManager 里的 StateFlow，
                // 不 refresh 的话导进来的那份永远不出现在这一页
                ControlManager.refresh()
            }
            // `refresh()` 内部是异步的，等它把表算完再报「当前列表里到底有几份」
            delay(150)
            val names = ControlManager.dataList.value.map { it.file.name }
            val listInfo =
                if (names.isEmpty()) "List is still empty"
                else "The list currently has ${names.size} copies: ${names.joinToString("、")}"
            if (imported > 0) {
                PclControlImportLog.set("$imported imported - $listInfo")
                PclHintHost.push(
                    if (imported == 1) "Control layout has been imported." else "$imported control layouts have been imported.",
                    PclHintTheme.Green,
                )
            } else {
                val reason = failures.firstOrNull() ?: "The selected file cannot be read."
                // 一个都没能正常用上 —— 原因必须让人看见，不能三秒后自己消失
                PclControlImportLog.set("$reason —— $listInfo")
                importError = reason
            }
        }
    }

    PclContent {
        PclCard(modifier = Modifier.fillMaxWidth(), title = "Control layout") {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PclButton(
                        text = "refresh",
                        logo = PclIcons.refresh,
                        onClick = { ControlManager.refresh() },
                    )
                    PclButton(
                        text = "Import layout (.json)",
                        logo = PclIcons.addBox,
                        onClick = {
                            // 【只给一个 `*/*`】以前传的是
                            // `arrayOf("application/json", "*/*")`，部分系统的文件选择器会拿
                            // EXTRA_MIME_TYPES 去过滤，结果选完之后一个 uri 都不返回 ——
                            // 回调里 uris 为空，直接 return，表现就是「点了完全没反应」。
                            // 给通配就一定选得到任何文件，后缀对不对交给导入流程自己判断。
                            runCatching { importPicker.launch(arrayOf("*/*")) }
                                .onFailure {
                                    val why = "Unable to open file selector: ${it.message}"
                                    PclControlImportLog.set(why)
                                    PclHintHost.push(why, PclHintTheme.Red)
                                }
                        },
                    )
                    PclButton(
                        text = "Create a new blank layout",
                        logo = PclIcons.selectCreateFolder,
                        onClick = { createDialog = true },
                    )
                }
                Spacer(Modifier.height(8.dp))

                when {
                    isRefreshing && dataList.isEmpty() ->
                        PclText(text = "Reading control layout...", color = summaryColor, fontSize = 12)

                    dataList.isEmpty() ->
                        PclText(text = "No control layout available.", color = summaryColor, fontSize = 12)

                    else -> dataList.forEach { data ->
                        PclControlRow(
                            data = data,
                            checked = data.file.name == selectedName,
                            onSelect = { ControlManager.selectControl(data) },
                            onEdit = { startEditorActivity(context, data.file) },
                            onDuplicate = {
                                ControlManager.duplicateControl(data) { th ->
                                    PclHintHost.push("Copy failed: ${th.message ?:"未知错误"}", PclHintTheme.Red)
                                }
                            },
                            onDelete = { deleteTarget = data },
                        )
                    }
                }

                lastImportResult?.let { message ->
                    Spacer(Modifier.height(6.dp))
                    PclHint(
                        text = message,
                        // 失败留下的是黄条，成功是绿条 —— 扫一眼就知道上一次到底怎么了
                        theme = if (message.startsWith("Imported")) PclHintTheme.Green
                        else PclHintTheme.Yellow,
                    )
                }

                Spacer(Modifier.height(8.dp))
                PclHint(
                    text = "The layout file is saved in: ${PathManager.DIR_CONTROL_LAYOUTS.absolutePath}\n" +
                            "Click a row to set it as the current layout; \"Edit Layout\" will open the visual editor." +
                            "Adjust the position and size of buttons.",
                    theme = PclHintTheme.Blue,
                )
            }
        }

        Spacer(Modifier.height(PclDimens.cardGap))
        PclBottomSpacer()
    }

    if (createDialog) {
        PclInputDialog(
            title = "New control layout",
            label = "layout name",
            onConfirm = { name ->
                ControlManager.createEmptyControl(name) { th ->
                    PclHintHost.push("New failed: ${th.message ?:"未知错误"}", PclHintTheme.Red)
                }
            },
            onDismiss = { createDialog = false },
        )
    }

    deleteTarget?.let { data ->
        val title = if (data.isSupport) data.controlLayout.info.name.default else data.file.name
        PclConfirmDialog(
            title = "Delete control layout",
            content = "Are you sure you want to delete \"$title\"?",
            danger = true,
            onConfirm = {
                ControlManager.deleteControl(data)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }

    importError?.let { message ->
        PclNoticeDialog(
            title = "Control layout import not completed",
            content = message,
            onDismiss = { importError = null },
        )
    }
}

/** 一行控制布局：名称 / 作者信息 + 编辑 / 复制 / 删除。 */
@Composable
private fun PclControlRow(
    data: ControlData,
    checked: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    val title = if (data.isSupport) data.controlLayout.info.name.default else data.file.name
    val info = buildString {
        if (!data.isSupport) {
            // 这类行有两种来源：布局版本比本启动器新，或者文件本身损坏。
            // 以前损坏的文件会被 refresh 直接丢掉（用户只看到「导入完了但列表没变化」），
            // 现在保留成一行，至少能删。
            append("Not supported or cannot be read, can only be deleted")
        } else {
            val author = data.controlLayout.info.author.default
            if (author.isNotEmpty()) append("author:").append(author)
            if (data.controlLayout.info.versionName.isNotEmpty()) {
                if (isNotEmpty()) append(" · ")
                append("Version:").append(data.controlLayout.info.versionName)
            }
            if (isEmpty()) append(data.file.name)
        }
    }

    PclListItem(
        title = title,
        modifier = Modifier.fillMaxWidth(),
        info = info,
        logo = PclIcons.setupControlManage,
        logoScale = 0.85f,
        type = PclListItemType.RadioBox,
        checked = checked,
        height = 50.dp,
        buttons = buildList {
            if (data.isSupport) {
                add(
                    PclListItemButton(
                        logo = PclIcons.loginMsEdit,
                        tooltip = "Edit layout",
                        onClick = onEdit,
                    )
                )
                add(
                    PclListItemButton(
                        logo = PclIcons.fileCopy,
                        tooltip = "Make a copy",
                        onClick = onDuplicate,
                    )
                )
            }
            add(
                PclListItemButton(
                    logo = PclIcons.delete,
                    tooltip = "Delete this layout",
                    onClick = onDelete,
                )
            )
        },
        onClick = { if (data.isSupport) onSelect() },
    )
}
