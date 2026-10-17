/*
 * Zalith Launcher 2
 * Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package com.movtery.zalithlauncher.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.ui.pcl2.components.PclMenuCheckRow
import com.movtery.zalithlauncher.ui.pcl2.components.PclMenuComboRow
import com.movtery.zalithlauncher.ui.pcl2.components.PclMenuPanel
import com.movtery.zalithlauncher.ui.pcl2.components.PclMenuRow
import com.movtery.zalithlauncher.ui.pcl2.components.PclMenuSliderRow
import com.movtery.zalithlauncher.ui.pcl2.components.PclSlider
import com.movtery.zalithlauncher.ui.pcl2.theme.PclTextStyle
import com.movtery.zalithlauncher.ui.pcl2.theme.pclPalette
import java.text.DecimalFormat

/**
 * 菜单状态
 */
enum class MenuState {
    /**
     * 初始化 (刚加载时)
     */
    NONE {
        override fun next() = SHOW
    },

    /**
     * 展示中
     */
    SHOW {
        override fun next() = HIDE
    },

    /**
     * 隐藏中
     */
    HIDE {
        override fun next() = SHOW
    };

    abstract fun next(): MenuState
}

/* ============================================================================
 *  版式说明
 *
 *  这里原来是 Material 3 的一套（圆角 21 的浮层、48 高灰底居中标题条、
 *  `MaterialTheme.colorScheme` 驱动的行底色、M3 Switch）。游戏内悬浮窗菜单是
 *  玩家会看到的主要界面之一，而 PCL 界面里没有任何一处长这样，所以整层换成
 *  PCL2 的控件观感：
 *    · 浮层  = `MyCard`（圆角 5、影子 3、底色 `#F5FFFFFF`、标题 13 号加粗在 (15,12)）；
 *    · 行    = `MyListItem` 的指向态（`ColorBrush7 @ alpha 190` + 1px `ColorBrush6` 边框）；
 *    · 开关  = `MyCheckBox`；下拉 = `MyComboBox`；滑杆 = `MySlider`。
 *  **所有函数签名保持不变**，调用点（`GameMenuSubscreen`）一行都不用改。
 * ==========================================================================*/

/**
 * 单块（右侧）菜单浮层。
 */
@Composable
fun MenuSubscreen(
    state: MenuState,
    closeScreen: () -> Unit,
    shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(5.dp),
    backgroundColor: Color = Color.Black.copy(alpha = 0.25f),
    content: @Composable ColumnScope.() -> Unit
) {
    val visible = state == MenuState.SHOW
    val scrimInteraction = remember { MutableInteractionSource() }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color = backgroundColor)
                    .clickable(
                        indication = null,
                        interactionSource = scrimInteraction,
                        onClick = closeScreen
                    )
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxWidth(fraction = 1f / 3f)
                .fillMaxHeight()
                .padding(top = 12.dp, end = 12.dp, bottom = 12.dp)
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn() + slideInHorizontally(
                    animationSpec = androidx.compose.animation.core.tween(
                        200, easing = com.movtery.zalithlauncher.ui.pcl2.theme.PclMotion.easeOutFluent
                    )
                ) { 40 },
                exit = fadeOut() + slideOutHorizontally { 40 }
            ) {
                PclMenuPanel(
                    title = null,
                    modifier = Modifier.fillMaxSize(),
                    content = content,
                )
            }
        }
    }
}

/**
 * 左右两块菜单浮层。
 *
 * @param leftTitle  左栏标题。为 null 时左栏不占标题位（例如游戏内菜单的左栏自带图标标签栏）。
 * @param rightTitle 右栏标题。
 *
 * 原先的 `leftMenuTitle` / `rightMenuTitle` 是以 `@Composable` 传进来的（调用处自带
 * Material 字体的 `Text`），配色没法在容器里统一，故新增这两个字符串参数代替：
 * 调用处改传标题文字，字体由本容器按 PCL 的卡片标题统一渲染。两个旧参数保留但不再使用。
 */
@Composable
fun DualMenuSubscreen(
    state: MenuState,
    closeScreen: () -> Unit,
    shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(5.dp),
    backgroundColor: Color = Color.Black.copy(alpha = 0.25f),
    titleHeight: Dp = 48.dp,
    leftMenuTitle: (@Composable BoxScope.() -> Unit)? = null,
    leftMenuContent: @Composable ColumnScope.() -> Unit = {},
    rightMenuTitle: (@Composable BoxScope.() -> Unit)? = null,
    rightMenuContent: @Composable ColumnScope.() -> Unit = {},
    leftTitle: String? = null,
    rightTitle: String? = null,
) {
    com.movtery.zalithlauncher.ui.pcl2.components.PclMenuPanels(
        visible = state == MenuState.SHOW,
        onClose = closeScreen,
        leftTitle = leftTitle,
        leftContent = leftMenuContent,
        rightTitle = rightTitle,
        rightContent = rightMenuContent,
    )
}

/* ============================================================================
 *  行
 * ==========================================================================*/

/**
 * 一行菜单文字 —— 走 PCL 的列表项观感。
 *
 * [appendLayout] 是 PCL 里 `MyListItem.Buttons` 那类行尾附加物，直接摆在文字右侧。
 */
@Composable
fun MenuTextButton(
    modifier: Modifier = Modifier,
    text: String,
    enabled: Boolean = true,
    influencedByBackground: Boolean = false,
    shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    color: Color = Color.Transparent,
    contentColor: Color = Color.Unspecified,
    appendLayout: (@Composable () -> Unit)? = null,
    onClick: () -> Unit = {}
) {
    if (appendLayout == null) {
        PclMenuRow(
            modifier = modifier,
            text = text,
            enabled = enabled,
            height = 42.dp,
            onClick = onClick,
        )
    } else {
        // 行尾有附加内容时不能直接用 PclMenuRow（它只画文字），这里补一个同款底板
        com.movtery.zalithlauncher.ui.pcl2.components.PclMenuSurface(
            modifier = modifier,
            enabled = enabled,
            radius = 6.dp,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 10.dp,
                vertical = 6.dp,
            ),
            onClick = onClick,
        ) {
            Text(
                text = text,
                style = PclTextStyle.listTitle(pclPalette),
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            appendLayout()
        }
    }
}

/**
 * 一行带开关的菜单项。
 *
 * `switch` / `onSwitch` 的语义与原来一致（`onSwitch` 收到的是**切换后**的值）。
 */
@Composable
fun MenuSwitchButton(
    modifier: Modifier = Modifier,
    text: String,
    switch: Boolean,
    onSwitch: (Boolean) -> Unit,
    enabled: Boolean = true,
    influencedByBackground: Boolean = false,
    shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    color: Color = Color.Transparent,
    contentColor: Color = Color.Unspecified,
) {
    PclMenuCheckRow(
        modifier = modifier,
        text = text,
        checked = switch,
        enabled = enabled,
        onCheckedChange = onSwitch,
    )
}

/**
 * 一行「标题 + 下拉」。
 *
 * 原实现是就地展开一列 RadioButton，M3 的圆点选择器在 PCL 里没有对应物 ——
 * 改成 PCL 的下拉框（`MyComboBox`），交互更贴近启动器里的同类设置项。
 */
@Composable
fun <E> MenuListLayout(
    modifier: Modifier = Modifier,
    title: String,
    items: List<E>,
    currentItem: E,
    onItemChange: (E) -> Unit,
    getItemText: @Composable (E) -> String,
    selectedItemLayout: @Composable (ColumnScope.(E) -> Unit) = { item ->
        PclMenuPillLabel(text = getItemText(item))
    },
    enabled: Boolean = true,
    maxListHeight: Dp = 200.dp,
    influencedByBackground: Boolean = false,
    shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    color: Color = Color.Transparent,
    contentColor: Color = Color.Unspecified,
) {
    // `getItemText` 是 @Composable（要用 stringResource），不能塞进普通 lambda，
    // 先在组合里把每个条目的文字算好。
    val labels = items.associateWith { getItemText(it) }
    PclMenuComboRow(
        modifier = modifier,
        label = title,
        items = items,
        selected = currentItem,
        enabled = enabled,
        labelOf = { labels[it] ?: it.toString() },
        onSelect = onItemChange,
    )
}

/**
 * 一行「标题 + 滑杆」（整数）。
 */
@Composable
fun MenuSliderLayout(
    modifier: Modifier = Modifier,
    title: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean = true,
    onValueChangeFinished: (Int) -> Unit = {},
    suffix: String? = null,
    influencedByBackground: Boolean = false,
    colors: SliderColors = SliderDefaults.colors(),
    shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    color: Color = Color.Transparent,
    contentColor: Color = Color.Unspecified,
) {
    var showInputDialog by remember { mutableStateOf(false) }
    // `PclSlider` 的 `pointerInput` 只捕获**首次组合**时的 lambda，
    // 直接在这里用参数 `value` 会拿到拖动前的旧值（松手保存 = 保存旧值）。用这个兜住最新值。
    val latestValue = rememberUpdatedState(value)

    PclMenuSliderRow(
        modifier = modifier,
        title = title,
        valueText = "$value${suffix ?: ""}",
        enabled = enabled,
        onValueClick = { showInputDialog = true },
    ) {
        PclSlider(
            value = pclSliderFraction(value.toFloat(), valueRange),
            enabled = enabled,
            onValueChange = { fraction ->
                onValueChange(pclSliderValue(fraction, valueRange).toInt())
            },
            // 松手时落盘。**不传这个回调就只有输入框那条路会保存**：
            // 拖动时只走 `updateState`（纯内存），切后台 / 重启游戏后 state 从 MMKV 重新读，
            // 于是「灵敏度改了又自己跳回去」。
            onValueChangeFinished = { onValueChangeFinished(latestValue.value) },
        )
    }

    if (showInputDialog) {
        SliderValueEditDialog(
            onDismissRequest = { showInputDialog = false },
            title = title,
            valueRange = valueRange,
            value = value.toFloat(),
            onValueChange = { onValueChangeFinished(it.toInt()) },
            intCheck = true
        )
    }
}

/**
 * 一行「标题 + 滑杆」（小数）。
 */
@Composable
fun MenuSliderLayout(
    modifier: Modifier = Modifier,
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean = true,
    onValueChangeFinished: (Float) -> Unit = {},
    suffix: String? = null,
    decimalFormat: String = "#0.00",
    influencedByBackground: Boolean = false,
    colors: SliderColors = SliderDefaults.colors(),
    shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    color: Color = Color.Transparent,
    contentColor: Color = Color.Unspecified,
) {
    val formatter = DecimalFormat(decimalFormat)
    var showInputDialog by remember { mutableStateOf(false) }
    // 同上：PclSlider 内部只捕获首次组合的 lambda，松手要读最新值
    val latestValue = rememberUpdatedState(value)

    PclMenuSliderRow(
        modifier = modifier,
        title = title,
        valueText = formatter.format(value) + (suffix ?: ""),
        enabled = enabled,
        onValueClick = { showInputDialog = true },
    ) {
        PclSlider(
            value = pclSliderFraction(value, valueRange),
            enabled = enabled,
            onValueChange = { fraction -> onValueChange(pclSliderValue(fraction, valueRange)) },
            onValueChangeFinished = { onValueChangeFinished(latestValue.value) },
        )
    }

    if (showInputDialog) {
        SliderValueEditDialog(
            onDismissRequest = { showInputDialog = false },
            title = title,
            valueRange = valueRange,
            value = value,
            onValueChange = { onValueChangeFinished(it) },
        )
    }
}

/**
 * 通用行底板（保留给需要自定义内容的调用处）。
 *
 * 版式与 [PclMenuRow] 同源 —— PCL `MyListItem` 的指向态。
 */
@Composable
fun MenuButtonLayout(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    influencedByBackground: Boolean = false,
    shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    color: Color = Color.Transparent,
    contentColor: Color = Color.Unspecified,
    blur: Int = 0,
    onClick: () -> Unit = {},
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    content: @Composable RowScope.() -> Unit
) {
    com.movtery.zalithlauncher.ui.pcl2.components.PclMenuSurface(
        modifier = modifier,
        enabled = enabled,
        radius = 6.dp,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 10.dp,
            vertical = 8.dp,
        ),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = verticalAlignment,
        onClick = onClick,
        content = content,
    )
}

/* ============================================================================
 *  内部小件
 * ==========================================================================*/

/** PCL 的小标签（`MyLabel`），替代原来那个用 M3 `tertiary` 色的胶囊。 */
@Composable
private fun PclMenuPillLabel(text: String) {
    com.movtery.zalithlauncher.ui.pcl2.components.PclMenuPill(text = text)
}

/** 把实际值换算成 0..1 的滑杆比例。 */
private fun pclSliderFraction(value: Float, range: ClosedFloatingPointRange<Float>): Float {
    val span = range.endInclusive - range.start
    if (span <= 0f) return 0f
    return ((value - range.start) / span).coerceIn(0f, 1f)
}

/** 把 0..1 的滑杆比例换算回实际值。 */
private fun pclSliderValue(fraction: Float, range: ClosedFloatingPointRange<Float>): Float {
    val span = range.endInclusive - range.start
    return range.start + fraction.coerceIn(0f, 1f) * span
}
