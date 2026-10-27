/*
 * Zalith Launcher 2 — PCL2 UI port
 *
 * 「更多」版块的右侧页面，对应 PCL2 的：
 *   PageOtherHelp.xaml       帮助（搜索框 + 帮助列表 + 加载卡片）
 *   PageOtherAbout.xaml      关于与鸣谢（关于 / 特别鸣谢 / 法律信息 / 许可与版权声明）
 *   PageOtherTest.xaml       百宝箱（居中单行提示）
 *
 * 卡片结构与文案取自原 XAML 与 zh-CN.xaml。
 * 「关于」与「反馈」两页已经换成 Zalith 的真实信息与真实链接
 * （版本号来自 BuildConfig，链接来自 UrlManager，点击即跳转浏览器），
 * PCL 专有的「新功能投票」在 Zalith 没有后端，改为真实的社区与反馈入口。
 *
 * 「关于」/「特别鸣谢」两卡按 `PageOtherAbout.xaml` 的原始版式重写为
 * 「圆形头像 + 标题/副标题 + 右侧按钮」的列表行；「关于」里的作者依次是
 * **本移植版作者 zuanxu**（`img_avatar_zuanxu`）、**龙腾猫跃**（`img_avatar_ltcat`，
 * PCL2 作者，按钮「打开主页」→ github.com/LTCatt）与 **墨北Mov**
 * （`img_avatar_movtery`，Zalith Launcher 2 作者，按钮「打开主页」→ github.com/MovTery）；
 * 鸣谢对象按 Zalith 实际依赖重排。
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.zalith.pcl.ui.pcl2

import android.app.Activity
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zalith.pcl.BuildConfig
import com.zalith.pcl.R
import com.zalith.pcl.path.URL_BANGBANG93_SPONSOR
import com.zalith.pcl.path.URL_COMMUNITY
import com.zalith.pcl.path.URL_EASYTIER_GITHUB
import com.zalith.pcl.path.URL_FCL
import com.zalith.pcl.path.URL_GITHUB_DRIVER_PLUGINS
import com.zalith.pcl.path.URL_GITHUB_NATIVE_LIB_PLUGINS
import com.zalith.pcl.path.URL_GITHUB_RENDERER_PLUGINS
import com.zalith.pcl.path.URL_HMCL
import com.zalith.pcl.path.URL_LTCATT
import com.zalith.pcl.path.URL_MCIM
import com.zalith.pcl.path.URL_MCMOD
import com.zalith.pcl.path.URL_MOVTERY
import com.zalith.pcl.path.URL_PCL2
import com.zalith.pcl.path.URL_POJAV
import com.zalith.pcl.path.URL_PROJECT
import com.zalith.pcl.path.URL_SUPPORT
import com.zalith.pcl.coroutine.Task
import com.zalith.pcl.coroutine.TaskSystem
import com.zalith.pcl.filemanager.FileManagerLauncher
import com.zalith.pcl.game.download.engine.DownloadEngine
import com.zalith.pcl.game.download.engine.DownloadRequest
import com.zalith.pcl.game.path.GamePathManager
import com.zalith.pcl.path.URL_WEBLATE
import com.zalith.pcl.upgrade.TooFrequentOperationException
import com.zalith.pcl.viewmodel.LauncherUpgradeViewModel
import com.zalith.pcl.path.PathManager
import com.zalith.pcl.ui.androidText
import com.zalith.pcl.ui.pcl2.components.PclBottomSpacer
import com.zalith.pcl.ui.pcl2.components.PclButton
import com.zalith.pcl.ui.pcl2.components.PclCard
import com.zalith.pcl.ui.pcl2.components.PclColorState
import com.zalith.pcl.ui.pcl2.components.PclConfirmDialog
import com.zalith.pcl.ui.pcl2.components.PclPrank
import com.zalith.pcl.ui.pcl2.components.PclThreeOkDialog
import com.zalith.pcl.ui.pcl2.components.PclEmptyState
import com.zalith.pcl.ui.pcl2.components.PclHint
import com.zalith.pcl.ui.pcl2.components.PclHintTheme
import com.zalith.pcl.ui.pcl2.components.PclIcons
import com.zalith.pcl.ui.pcl2.components.PclListItem
import com.zalith.pcl.ui.pcl2.components.PclNoticeDialog
import com.zalith.pcl.ui.pcl2.components.PclSearchBox
import com.zalith.pcl.ui.pcl2.components.PclSplitLine
import com.zalith.pcl.ui.pcl2.components.PclTextBox
import com.zalith.pcl.ui.pcl2.components.PclText
import com.zalith.pcl.ui.pcl2.data.rememberRemoteText
import com.zalith.pcl.ui.pcl2.theme.PclDimens
import com.zalith.pcl.ui.pcl2.theme.PclTextStyle
import com.zalith.pcl.ui.pcl2.theme.pclPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.File
import com.zalith.pcl.utils.network.openLinkInternal

/** 在浏览器里打开一个真实链接。对同包的更新宿主（PclUpgradeHost）开放。 */
internal fun openLink(context: android.content.Context?, url: String) {
    val activity = context as? Activity ?: return
    runCatching {
        activity.openLinkInternal(url)
    }.onFailure {
        PclHintHost.push("Unable to open link: $url", PclHintTheme.Red)
    }
}

/* ============================================================================
 *  帮助
 * ==========================================================================*/

/** `PageOtherHelp` —— 顶部搜索框（Margin 0,0,0,15）+ 帮助条目卡片列表。 */
@Composable
fun PclOtherHelpPage() {
    var keyword by remember { mutableStateOf("") }
    val entries = listOf(
        "How to install Minecraft?" to "Select the game version and loader in \"Download → Original Game\" and click \"Start Download\".",
        "How to install Forge/Fabric?" to "Expand the corresponding loader card on the installation page of \"Download → Original Game\", select a version and then start downloading.",
        "How to modify game memory?" to "\"Settings → Startup → Memory Allocation\" can choose automatic or drag the slider to customize; what is written here is the actual setting of Zalith.",
        "How to change renderer?" to "Select the renderer plug-in installed on the device in \"Settings → Startup → Renderer\".",
        "How to install mods?" to "\"Download → Mod\" search and download, or \"Version Management → Mod Management\" to view and start and stop installed Mods.",
        "How to use the online function?" to "Create a room on the \"Connectivity\" page and copy the invitation code, and friends can join with the invitation code; you can also customize the node in \"Settings → Connection\".",
        "What should I do if the startup fails?" to "The real-time startup log will be displayed on the right side of the homepage; you can also turn off the integrity check in \"Version Management → Settings\" and try again.",
    ).filter { keyword.isEmpty() || it.first.contains(keyword) || it.second.contains(keyword) }

    PclContent {
        PclSearchBox(
            value = keyword,
            modifier = Modifier.fillMaxWidth().padding(bottom = 15.dp),
            hint = PclStrings.LangPageOtherHelpSearch,
            onValueChange = { keyword = it },
        )
        PclHint(text = PclStrings.LangPageOtherHelpSimpleChineseOnly)
        Spacer(Modifier.height(15.dp))
        entries.forEach { (title, body) ->
            PclCard( modifier = Modifier.fillMaxWidth(),title = title) {
                PclText(text = body, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(15.dp))
        }
        if (entries.isEmpty()) {
            PclEmptyState(
                title = PclStrings.LangPageOtherHelpSearch,
                content = "No help content related to \"$keyword\" was found.",
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
        PclBottomSpacer()
    }
}

/* ============================================================================
 *  关于
 * ==========================================================================*/

/**
 * 「关于 / 特别鸣谢」里的一行：圆形头像 + 标题 / 副标题 + 右侧按钮。
 *
 * 版式取自 PCL2 `PageOtherAbout.xaml` 的 `Grid`：
 *   `ColumnDefinition Width="40"`——Avatar column (picture 34dp, left and right `Margin="3"` 后正好 40）
 *   `ColumnDefinition`（星号）      —— `MyListItem`：Title 13 号、Info 12 号灰字
 *   `ColumnDefinition Width="150"` —— 右侧 35dp 高的 `MyButton`
 * 每行 `RowDefinition Height="45"`。
 *
 * 头像在 PCL2 里用 `EllipseGeometry RadiusX/Y="17"` 裁成圆，
 * 这里等价成 34dp 的 `CircleShape` 裁剪；位图走 `ContentScale.Crop`，
 * 保证非正方形头像也不会被压扁。
 *
 * 行高：PCL2 桌面端一律 45，但手机竖屏的文字列比桌面窄得多，
 * 12 号的副标题很容易折成两行。所以这里写的是 `heightIn(min = 45.dp)` 而不是硬高度：
 * 上下各留 5dp 内边距后内容区下限正好 35dp（= 按钮高），一般行就是 45dp；
 * 副标题真的折行时这一行才长高，避免中文下半截被 45dp 的硬高度裁掉。
 */
@Composable
private fun PclAboutRow(
    icon: Painter,
    title: String,
    info: String,
    buttonText: String,
    highlighted: Boolean = false,
    onClick: () -> Unit,
) {
    val p = pclPalette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 45.dp)
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(40.dp), contentAlignment = Alignment.Center) {
            Image(
                painter = icon,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape),
            )
        }
        Column(modifier = Modifier.weight(1f).padding(end = 15.dp)) {
            PclText(text = title, fontSize = 13)
            PclText(text = info, fontSize = 12, color = p.gray2)
        }
        PclButton(
            text = buttonText,
            colorState = if (highlighted) PclColorState.Highlight else PclColorState.Normal,
            onClick = onClick,
        )
    }
}

/**
 * `PageOtherAbout` —— 关于与鸣谢。
 *
 * 与 PCL2 原版的两点差异（都是有意的）：
 *   · 作者一栏换成**本移植版的作者 zuanxu** 与其头像；
 *   · 其后依次列出**两位上游作者**：龙腾猫跃（PCL2）与墨北Mov（Zalith Launcher 2），
 *     两行的按钮都是「打开主页」，分别跳转各自的 GitHub 个人主页
 *     （`URL_LTCATT` / `URL_MOVTERY`）。
 * 鸣谢对象按 Zalith 实际依赖的上下游重排，按钮全部指向真实可访问的页面
 * （版本号来自 BuildConfig，链接来自 UrlManager）。
 */
@Composable
fun PclOtherAboutPage() {
    val context = LocalContext.current
    // Activity 作用域的更新 ViewModel：与外壳上的 `PclUpgradeHost` 是**同一个实例**，
    // 所以这里检查到新版本时，那个宿主会直接弹出 PCL 风格的更新框
    // （上游只有 MainActivity 渲染更新弹窗，本界面在 Pcl2Activity，之前点完毫无反应）。
    val scope = rememberCoroutineScope()
    val upgradeViewModel: LauncherUpgradeViewModel = viewModel()
    // 手动检查的状态：按钮文字改成「检查中…」，结果用弹窗给出（不再依赖左下角提示条）。
    var checking by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    PclContent {
        // ---- 关于
        PclCard(modifier = Modifier.fillMaxWidth(), title = PclStrings.LangPageOtherAboutAbout) {
            Column(modifier = Modifier.fillMaxWidth()) {
                PclAboutRow(
                    icon = painterResource(R.drawable.img_avatar_zuanxu),
                    title = "zuanxu",
                    info = "Author of Zalith-Plain Craft Launcher",
                    buttonText = "Open home page",
                    highlighted = true,
                ) { openLink(context, URL_PROJECT) }

                // PCL2 原版关于页的作者栏就是这一行（LTCat.jpg + 「赞助 PCL」），
                // 这里按用户要求改成「打开主页」→ 龙腾猫跃本人的 GitHub。
                PclAboutRow(
                    icon = painterResource(R.drawable.img_avatar_ltcat),
                    title = "Dragon Soaring Cat Jumping",
                    info = "From the author of Plain Craft Launcher 2!",
                    buttonText = "Open home page",
                ) { openLink(context, URL_LTCATT) }

                PclAboutRow(
                    icon = painterResource(R.drawable.img_avatar_movtery),
                    title = "MobeiMov",
                    info = "Author of Zalith Launcher 2!",
                    buttonText = "Open home page",
                ) { openLink(context, URL_MOVTERY) }

                PclAboutRow(
                    icon = painterResource(R.drawable.img_launcher),
                    title = "Zalith Launcher 2",
                    info = "Running kernel of Zalith-Plain Craft Launcher · Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · ${BuildConfig.BUILD_TYPE}",
                    buttonText = "Open source code",
                ) { openLink(context, "https://github.com/ZalithLauncher/ZalithLauncher2") }

                PclAboutRow(
                    icon = painterResource(R.drawable.ic_launcher_logo),
                    title = "Zalith-Plain Craft Launcher",
                    info = "This launcher: Minecraft Java version launcher based on Zalith Launcher 2 and PCL2 interface",
                    buttonText = if (checking) "Checking…" else "Check for updates",
                ) {
                    // 检查更新必须**当场给出可见结果**：以前只往左下角推一条提示条，
                    // 而「已是最新」「网络失败」这类结果一闪就没了，用户点完的感受就是
                    // 「点了没反应，连个弹窗都没有」。现在统一弹 PCL 提示框。
                    if (checking) return@PclAboutRow
                    scope.launch {
                        checking = true
                        try {
                            val finished = withTimeoutOrNull(90_000) {
                                upgradeViewModel.checkManually(
                                    onChecked = { data ->
                                        notice = when {
                                            data == null ->
                                                "Failed to obtain updates, please check the network and try again. " +
                                                    "(If you are in mainland China and the GitHub interface is often unable to connect, it is usually fine to try again at another time.)"
                                            BuildConfig.VERSION_CODE >= data.code ->
                                                "It is currently the latest version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})."
                                            // 有新版本：operation 已由 ViewModel 设好，交给 PclUpgradeHost 弹框
                                            else -> null
                                        }
                                    },
                                )
                            }
                            if (finished == null) {
                                notice = "Checking for updates has timed out, please check the network and try again."
                            }
                        } catch (_: TooFrequentOperationException) {
                            notice = "Checking too frequently, please wait a few seconds and try again."
                        } catch (_: Exception) {
                            notice = "Failed to obtain updates, please check the network and try again."
                        } finally {
                            checking = false
                        }
                    }
                }

                PclAboutRow(
                    icon = painterResource(R.drawable.img_launcher_pcl2),
                    title = "Plain Craft Launcher 2",
                    info = "Ported prototype of Zalith-Plain Craft Launcher interface: layout, color matching and copywriting are all taken from PCL2",
                    buttonText = "Open source code",
                ) { openLink(context, URL_PCL2) }
            }
        }

        Spacer(Modifier.height(PclDimens.cardGap))

        // ---- 特别鸣谢
        PclCard(modifier = Modifier.fillMaxWidth(), title = PclStrings.LangPageOtherAboutThanks) {
            Column(modifier = Modifier.fillMaxWidth()) {
                PclAboutRow(
                    icon = painterResource(R.drawable.img_avatar_bangbang93),
                    title = "bangbang93",
                    info = "Provide BMCLAPI image source and Forge installation tool",
                    buttonText = "Sponsored Mirror Source",
                ) { openLink(context, URL_BANGBANG93_SPONSOR) }

                PclAboutRow(
                    icon = painterResource(R.drawable.img_platform_mcmod),
                    title = "MC Encyclopedia",
                    info = "Chinese translation of Mod name and more related information provided!",
                    buttonText = "Open encyclopedia",
                ) { openLink(context, URL_MCMOD) }

                PclAboutRow(
                    icon = painterResource(R.drawable.img_avatar_mcim),
                    title = "z0z0r4",
                    info = "MCIM community resource mirror source and help library provided!",
                    buttonText = "MCIM Home Page",
                ) { openLink(context, URL_MCIM) }

                PclAboutRow(
                    icon = painterResource(R.drawable.ic_public),
                    title = "EasyTier",
                    info = "Online module provided!",
                    buttonText = "GitHub page",
                ) { openLink(context, URL_EASYTIER_GITHUB) }

                PclAboutRow(
                    icon = painterResource(R.drawable.img_launcher_fcl),
                    title = "Fold Craft Launcher",
                    info = "An important reference for Android interface and interaction framework",
                    buttonText = "GitHub page",
                ) { openLink(context, URL_FCL) }

                PclAboutRow(
                    icon = painterResource(R.drawable.img_launcher_hmcl),
                    title = "Hello Minecraft! Launcher",
                    info = "Important reference for startup process and version management",
                    buttonText = "GitHub page",
                ) { openLink(context, URL_HMCL) }

                PclAboutRow(
                    icon = painterResource(R.drawable.img_launcher_pojav),
                    title = "PojavLauncher",
                    info = "Technology pioneers running Minecraft Java Edition on Android",
                    buttonText = "GitHub page",
                ) { openLink(context, URL_POJAV) }

                PclAboutRow(
                    icon = painterResource(R.drawable.img_platform_weblate),
                    title = "Weblate",
                    info = "Provide online multi-language translation collaboration for this launcher",
                    buttonText = "Participate in translation",
                ) { openLink(context, URL_WEBLATE) }
            }
        }

        Spacer(Modifier.height(PclDimens.cardGap))

        // ---- 许可与版权声明
        PclCard(modifier = Modifier.fillMaxWidth(),
            title = PclStrings.LangPageOtherAboutLicenseCopyright,
            collapsible = true,
            initiallyCollapsed = true,
        ) {
            Column {
                PclText(
                    text = "Plain Craft Launcher 2 is released under the GNU General Public License v3.0," +
                        "Zalith Launcher 2 is also released under GPL-3.0, and this interface port also follows this license.",
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                    PclButton(text = "License text", onClick = { openLink(context, "$URL_PROJECT/blob/main/LICENSE") })
                    PclButton(text = "Project warehouse", onClick = { openLink(context, URL_PROJECT) })
                }
            }
        }

        PclBottomSpacer()
    }

    // 手动检查更新的结果（已最新 / 失败 / 太频繁 / 超时）。
    // 放在 `PclContent` 之后：Dialog 是独立窗口，不参与页面滚动布局。
    notice?.let { text ->
        PclNoticeDialog(
            title = "Check for updates",
            content = text,
            onDismiss = { notice = null },
        )
    }
}

/* ============================================================================
 *  百宝箱
 *
 *  ⚠️ PCL2 的**开源版把这一页整个掏空了**：`PageOtherTest.xaml` 只剩一句居中提示，
 *  `.xaml.vb` 里每个函数都只 `Hint(GetLang("LangPageOtherTestNoUtility"))`
 *  （「为便于维护，开源内容中不包含百宝箱功能……」）。所以下面的实现**不是**照抄源码，
 *  而是按 PCL2 实际界面复刻版式，行为在 Android 上重新落地：
 *
 *    · 今日人品 / 千万别点 —— 纯本地彩蛋，随机池写在本文件下方；
 *    · 内存优化 —— Android 上对应「触发 GC 并回收堆」，并把**实际**释放量报出来；
 *    · 清理游戏垃圾 —— 只清 .minecraft 里公认的垃圾（崩溃报告 / 轮转日志 / hs_err 转储），
 *      删除前先把条数与体积列出来让用户确认，`latest.log` 永远保留；
 *    · 回声洞 —— PCL 是向自己的服务端取随机留言，Zalith 没有这个后端，改为内置留言池；
 *    · 下载自定义文件 —— 走 Zalith 自己的多线程下载引擎（`DownloadEngine`），
 *      提交后同样会出现在「下载管理」里。
 * ==========================================================================*/

@Composable
fun PclOtherTestPage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 通用小提示框（标题 to 正文）
    var notice by remember { mutableStateOf<Pair<String, String>?>(null) }
    // 待确认的垃圾清理结果
    var rubbish by remember { mutableStateOf<RubbishScan?>(null) }
    // 待确认的存储清理结果
    var storage by remember { mutableStateOf<StorageScan?>(null) }
    // 「千万别点」：0 无 / 1 三按钮弹窗 / 2 恶搞画面
    var prankStage by remember { mutableIntStateOf(0) }
    // 回声洞：进页面就联网抓一条（旧实现只给了占位文案，没有首帧请求，
    // 必须点一下才去抓，表现成「每次进来都是没有留言」）。点击整块换一条。
    var echoReload by remember { mutableIntStateOf(0) }
    val echoText = rememberRemoteText("cave", echoReload)

    // ---- 下载自定义文件的表单
    var url by remember { mutableStateOf("") }
    var folder by remember { mutableStateOf(defaultDownloadFolder().absolutePath) }
    var fileName by remember { mutableStateOf("") }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        when (val path = treeUriToPath(uri)) {
            null -> PclHintHost.push("The path to the selected folder cannot be recognized, please try again in another location.", PclHintTheme.Red)
            else -> folder = path
        }
    }

    PclContent {
        // ---------------------------------------------------------------- 百宝箱
        PclCard(modifier = Modifier.fillMaxWidth(), title = "treasure chest") {
            Column {
                // 横排一行；窄屏时允许横向滑动，避免挤成一团
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PclButton(text = "Today's character", onClick = { notice = todayFortune() })
                    PclButton(text = "Clean up storage", onClick = {
                        scope.launch {
                            val scan = withContext(Dispatchers.IO) { scanStorage(context) }
                            if (scan.totalBytes <= 0L) {
                                notice = "Clean up storage" to "The launcher's cache directory is already empty and has no content to clean."
                            } else {
                                storage = scan
                            }
                        }
                    })
                    PclButton(text = "Clean up game junk", onClick = {
                        scope.launch {
                            val scan = withContext(Dispatchers.IO) {
                                scanRubbish(File(GamePathManager.currentPath.value))
                            }
                            if (scan.files.isEmpty()) {
                                notice = "Clean up game junk" to "This game directory is already very clean, with no junk files to clean."
                            } else {
                                rubbish = scan
                            }
                        }
                    })
                    PclButton(
                        text = "Don't order it",
                        colorState = PclColorState.Red,
                        onClick = { prankStage = 1 },
                    )
                }
            }
        }
        Spacer(Modifier.height(PclDimens.cardGap))

        // ---------------------------------------------------------------- 回声洞
        PclCard(modifier = Modifier.fillMaxWidth(), title = "echo cave") {
            // 整块可点：点一次从官网联网换一条（拿不到就保持现状）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { echoReload++ },
            ) {
                Text(
                    text = echoText,
                    style = PclTextStyle.base(pclPalette),
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }
        Spacer(Modifier.height(PclDimens.cardGap))

        // ---------------------------------------------------------------- 下载自定义文件
        PclCard(modifier = Modifier.fillMaxWidth(), title = "Download custom files") {
            Column {
                PclHint(
                    text = "Download arbitrary files using PCL's high-speed multi-threaded download engine. " +
                            "(For example, Baidu Netdisk) may report an error (403) Forbidden and cannot download normally.",
                    theme = PclHintTheme.Blue,
                )
                Spacer(Modifier.height(12.dp))

                PclFormRow("Download address") {
                    PclTextBox(
                        value = url,
                        modifier = Modifier.fillMaxWidth(),
                        hint = "https://example.com/file.zip",
                        onValueChange = { url = it },
                    )
                }
                PclFormRow("save to") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PclTextBox(
                            value = folder,
                            modifier = Modifier.weight(1f),
                            onValueChange = { folder = it },
                        )
                        Spacer(Modifier.width(8.dp))
                        PclButton(text = "choose", onClick = { folderPicker.launch(null) })
                    }
                }
                PclFormRow("file name") {
                    PclTextBox(
                        value = fileName,
                        modifier = Modifier.fillMaxWidth(),
                        hint = "If left blank, the download address will be automatically inferred.",
                        onValueChange = { fileName = it },
                    )
                }

                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    PclButton(
                        text = "Start downloading",
                        colorState = PclColorState.Highlight,
                        onClick = {
                            val request = buildCustomDownload(url = url, folder = folder, fileName = fileName)
                            if (request == null) {
                                PclHintHost.push("Please fill in the download address and file name first.", PclHintTheme.Red)
                            } else {
                                startCustomDownload(request)
                            }
                        },
                    )
                    Spacer(Modifier.width(10.dp))
                    PclButton(
                        text = "open folder",
                        onClick = {
                            runCatching {
                                File(folder).takeIf { !it.exists() }?.mkdirs()
                                FileManagerLauncher.launch(context, rootPath = folder)
                            }.onFailure {
                                PclHintHost.push("Unable to open folder: ${it.message ?: it.javaClass.simpleName}", PclHintTheme.Red)
                            }
                        },
                    )
                }
            }
        }
        PclBottomSpacer()
    }

    notice?.let { (title, body) ->
        PclNoticeDialog(title = title, content = body, onDismiss = { notice = null })
    }

    rubbish?.let { scan ->
        PclConfirmDialog(
            title = "Clean up game junk",
            content = buildString {
                append("Will be deleted in \"${GamePathManager.currentPath.value}\"")
                append("${scan.files.size} files, totaling ${formatBytes(scan.totalBytes)}. ")
                append("Will be cleared: crash report, rotation log (keep latest.log), hs_err / replay crash dump. ")
                append("Mods, archives, resource packs, etc. are **not affected**.")
            },
            danger = true,
            onConfirm = {
                // 注意：`PclConfirmDialog` 在 onConfirm 之后还会紧接着调一次 onDismiss，
                // 所以这里不能再切「中间态」等别处去干活（那样会被同帧覆盖掉），
                // 该做的事直接做完。
                scope.launch {
                    val (count, bytes) = withContext(Dispatchers.IO) { deleteRubbish(scan.files) }
                    PclHintHost.push("Cleaned $count files, freeing ${formatBytes(bytes)}.", PclHintTheme.Green)
                }
            },
            onDismiss = { rubbish = null },
        )
    }

    storage?.let { scan ->
        PclConfirmDialog(
            title = "Clean up storage",
            content = buildString {
                append("The cache directory of the launcher will be cleared, totaling ${formatBytes(scan.totalBytes)} (${scan.targets.size} items). ")
                append("What will be cleared are: temporary directories left behind by downloads and installations, image and icon caches, and external cache directories. ")
                append("Games, saves, mods, accounts and settings are **not affected**.")
            },
            danger = true,
            onConfirm = {
                // 同 `rubbish`：不能在这里切中间态，直接干活。
                scope.launch {
                    val (count, bytes) = withContext(Dispatchers.IO) { deleteStorage(scan.targets) }
                    PclHintHost.push("Cleaned $count items, freeing ${formatBytes(bytes)}.", PclHintTheme.Green)
                }
            },
            onDismiss = { storage = null },
        )
    }

    if (prankStage == 1) {
        // PCL2 那个彩蛋的已知结构：弹出的对话框里并排三枚全部写着「确定」的按钮，
        // 随便按哪一枚都会**整扇窗口**开始整活（四种效果随机抽一个，见 `PclPrank`）。
        PclThreeOkDialog(
            title = "Don't order it",
            content = "I told you not to order it, so why did you order it anyway? " +
                    "The three buttons below are exactly the same, just pick any one and press it.",
            onChosen = {
                // 先把弹窗关掉，再放彩蛋 —— 窗口飘起来的时候不该还挂着一个对话框
                prankStage = 0
                PclPrank.triggerRandom()
            },
            // 按下返回键关窗会走 onDismiss；加个判定，别把已经关掉的状态又拉回来。
            onDismiss = { if (prankStage == 1) prankStage = 0 },
        )
    }
}

/** 表单行：左标签 + 右控件，对齐 PCL 设置页的 `Margin="0,0,0,10"` 布局。 */
@Composable
private fun PclFormRow(label: String, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = PclTextStyle.base(pclPalette),
            maxLines = 1,
            modifier = Modifier.width(72.dp),
        )
        Box(modifier = Modifier.weight(1f)) { content() }
    }
}

/* ------------------------------------------------------------------ 百宝箱的数据与工具 */

/** 默认下载目录：启动器自己的外部目录（无需任何存储权限即可写）。 */
private fun defaultDownloadFolder(): File = File(PathManager.DIR_FILES_EXTERNAL, "MyDownload")

/** 待清理的垃圾文件集合。 */
private data class RubbishScan(val files: List<File>, val totalBytes: Long)

/**
 * 扫描 .minecraft 里公认的垃圾。
 *
 * 只认这几类，且**只删文件、不递归删目录**：
 *   · `crash-reports` 目录下的全部文件
 *   · `logs` 目录下除 `latest.log` 之外的轮转日志
 *   · 根目录的 `hs_err_pid` / `replay_pid` 开头的崩溃转储
 */
private fun scanRubbish(gameDir: File): RubbishScan {
    if (!gameDir.isDirectory) return RubbishScan(emptyList(), 0L)
    val out = mutableListOf<File>()

    File(gameDir, "crash-reports").listFiles()
        ?.filterTo(out) { it.isFile }

    File(gameDir, "logs").listFiles()
        ?.filterTo(out) { it.isFile && it.name != "latest.log" }

    gameDir.listFiles()
        ?.filterTo(out) {
            it.isFile && (it.name.startsWith("hs_err_pid") || it.name.startsWith("replay_pid"))
        }

    return RubbishScan(out, out.sumOf { it.length() })
}

/** 删除扫描结果，返回「实际删掉的条数 to 实际释放的字节」。 */
private fun deleteRubbish(files: List<File>): Pair<Int, Long> {
    var count = 0
    var bytes = 0L
    files.forEach { file ->
        val size = file.length()
        if (runCatching { file.delete() }.getOrDefault(false)) {
            count++
            bytes += size
        }
    }
    return count to bytes
}

/** 待清理的存储缓存。 */
private data class StorageScan(val targets: List<File>, val totalBytes: Long)

/**
 * 扫描启动器的缓存占用。
 *
 * 覆盖两处：应用私有缓存目录（`context.cacheDir`，里面装着下载/安装的临时目录、
 * 图片缓存 `images`、图标缓存 `app_icons`、联网主页缓存 `remote_homepage`）
 * 与外部缓存目录。
 *
 * ⚠️ **正在跑任务时跳过 `temp_` 开头的目录**：`temp_game` / `temp_modpack` 等是下载与
 * 安装引擎的工作区，边下边删会把任务弄炸。任务列表来自 `TaskSystem.tasksFlow`。
 */
private fun scanStorage(context: Context): StorageScan {
    val busy = TaskSystem.tasksFlow.value.isNotEmpty()
    val targets = mutableListOf<File>()

    context.cacheDir.listFiles()?.forEach { child ->
        if (busy && child.name.startsWith("temp_")) return@forEach
        targets += child
    }
    context.externalCacheDir?.listFiles()?.forEach { targets += it }

    return StorageScan(targets, targets.sumOf { sizeOf(it) })
}

/** 递归求体积（目录则累加子项）。 */
private fun sizeOf(file: File): Long =
    if (file.isDirectory) (file.listFiles()?.sumOf { sizeOf(it) } ?: 0L) else file.length()

/** 删除扫描结果，返回「实际删掉的项数 to 实际释放的字节」。 */
private fun deleteStorage(targets: List<File>): Pair<Int, Long> {
    // 删除前**再确认一次**任务状态：从扫描到用户点确定之间可能刚好有下载起来了
    val busy = TaskSystem.tasksFlow.value.isNotEmpty()
    var count = 0
    var bytes = 0L
    targets.forEach { file ->
        if (busy && file.name.startsWith("temp_")) return@forEach
        val size = sizeOf(file)
        if (runCatching { file.deleteRecursively() }.getOrDefault(false)) {
            count++
            bytes += size
        }
    }
    return count to bytes
}

/** 今日人品：同一天必然得到同一个结果（按日期做种子）。 */
private fun todayFortune(): Pair<String, String> {
    val day = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
    val seed = day.hashCode().toLong()
    val luck = (kotlin.math.abs(seed) % 101L).toInt()
    val comment = when {
        luck >= 95 -> "Good luck! "
        luck >= 80 -> "A pretty good day to go mining or build a big project."
        luck >= 60 -> "The fortune is stable and there will be no big surprises in daily play."
        luck >= 40 -> "On an ordinary day, be careful not to build your house next to magma."
        luck >= 20 -> "Luck is generally on the low side, so remember to back up your save before departure."
        luck >= 5 -> "It’s better not to go to the end today and pack the boxes at home."
        else -> "Suitable for: sleeping. "
    }
    return "Today's character" to "Today’s personality: $luck\n\n$comment"
}

/** 把下载表单整理成一个下载请求；信息不全时返回 null。 */
private fun buildCustomDownload(url: String, folder: String, fileName: String): CustomDownload? {
    val rawUrl = url.trim()
    if (rawUrl.isEmpty()) return null
    val dir = File(folder.trim().ifEmpty { defaultDownloadFolder().absolutePath })
    val name = fileName.trim().ifEmpty {
        rawUrl.substringAfterLast('/').substringBefore('?').substringBefore('#')
    }
    if (name.isEmpty()) return null
    return CustomDownload(url = rawUrl, target = File(dir, name))
}

private data class CustomDownload(val url: String, val target: File)

/**
 * 提交下载任务。走 Zalith 自己的引擎（`DownloadEngine`），
 * 与「下载管理」共用同一条任务流，所以提交后在那里也能看到进度、也能取消。
 */
private fun startCustomDownload(request: CustomDownload) {
    runCatching {
        request.target.parentFile?.takeIf { !it.exists() }?.mkdirs()
        val task = Task.runTask(
            dispatcher = Dispatchers.IO,
            task = { task ->
                task.updateProgress(-1f)
                task.updateMessage(androidText("Download ${request.target.name}"))
                // 这个接口不预先知道大小（`expectedSize` 缺省），进度只能是「不确定」，
                // 但消息里可以如实报出已落盘的累计字节数。
                var downloaded = 0L
                DownloadEngine.download(
                    request = DownloadRequest(
                        urls = listOf(request.url),
                        targetFile = request.target,
                    ),
                ) { delta ->
                    downloaded += delta
                    task.updateMessage(
                        androidText("Download ${request.target.name} (${formatBytes(downloaded)})")
                    )
                }
                task.updateMessage(androidText("Downloaded ${request.target.name} (${formatBytes(downloaded)})"))
                task.updateProgress(1f)
            },
        )
        TaskSystem.submitTask(task, onEnded = {
            PclHintHost.push("Download ends: ${request.target.absolutePath}", PclHintTheme.Green)
        })
        PclHintHost.push("The download task has been submitted. Please see \"Download Management\" for the progress.", PclHintTheme.Blue)
    }.onFailure {
        PclHintHost.push("Unable to start download: ${it.message ?: it.javaClass.simpleName}", PclHintTheme.Red)
    }
}

/** `DocumentsContract` 的树 URI → 真实路径（与版本管理页里那份逻辑一致）。 */
private fun treeUriToPath(uri: android.net.Uri): String? = runCatching {
    val docId = android.provider.DocumentsContract.getTreeDocumentId(uri)
    val parts = docId.split(":", limit = 2)
    val volume = parts[0]
    val relative = parts.getOrNull(1).orEmpty().trim('/')
    val root = when {
        volume.startsWith("raw:") -> return@runCatching "/" + volume.removePrefix("raw:").trim('/')
        volume == "primary" -> "/storage/emulated/0"
        else -> "/storage/$volume"
    }
    if (relative.isEmpty()) root else "$root/$relative"
}.getOrNull()

/** 人类可读的字节数。 */
private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> String.format(Locale.US, "%.2f GB", bytes / 1024.0 / 1024.0 / 1024.0)
    bytes >= 1024L * 1024L -> String.format(Locale.US, "%.2f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}

/* ============================================================================
 *  反馈
 * ==========================================================================*/

/** `PageOtherFeedback` —— 真实的反馈与支持入口，点击即打开对应页面。 */
@Composable
fun PclOtherFeedbackPage() {
    val p = pclPalette
    val context = LocalContext.current
    val channels = listOf(
        Triple("GitHub Issues", "Submit bug reports and feature suggestions (this launcher repository)", "$URL_PROJECT/issues"),
        Triple("renderer plugin", "Get installable renderer plug-ins (FCL plug-in format)", URL_GITHUB_RENDERER_PLUGINS),
        Triple("Driver plug-in", "Get Vulkan driver plug-ins such as Turnip", URL_GITHUB_DRIVER_PLUGINS),
        Triple("Native library plug-in", "Get additional native library plugins", URL_GITHUB_NATIVE_LIB_PLUGINS),
    )
    PclContent {
        PclHint(
            text = "Before giving feedback, it is recommended to check the startup log (on the right side of the homepage, or the log settings in \"Settings → Startup → Advanced Options\").",
            theme = PclHintTheme.Blue,
        )
        Spacer(Modifier.height(15.dp))
        PclCard( modifier = Modifier.fillMaxWidth(),title = "Feedback and resource channels") {
            Column {
                channels.forEach { (name, info, url) ->
                    PclListItem(
                        title = name,
                        info = info,
                        logo = PclIcons.otherFeedback,
                        logoScale = 0.85f,
                        height = 48.dp,
                        infoColor = p.gray2,
                        buttons = listOf(
                            com.zalith.pcl.ui.pcl2.components.PclListItemButton(
                                logo = PclIcons.arrowRight,
                                tooltip = "Open",
                            ) { openLink(context, url) },
                        ),
                        onClick = { openLink(context, url) },
                    )
                }
            }
        }
        PclBottomSpacer()
    }
}

/* ============================================================================
 *  投票 → Zalith 没有投票后端，改为真实的社区入口
 * ==========================================================================*/

/** 原名 `PageOtherVote`；Zalith 无投票功能，这里展示真实的社区入口。 */
@Composable
fun PclOtherVotePage() {
    val p = pclPalette
    val context = LocalContext.current
    val entries = listOf(
        Triple("Project warehouse", "View progress, submit Issues and Pull Requests", URL_PROJECT),
        Triple("Contributor list", "View developers who contributed to this launcher", "$URL_PROJECT/graphs/contributors"),
    )
    PclContent {
        PclHint(
            text = "This launcher does not have a built-in \"new feature voting\" system. Please submit feature suggestions through GitHub Issues.",
            theme = PclHintTheme.Yellow,
        )
        Spacer(Modifier.height(15.dp))
        PclCard( modifier = Modifier.fillMaxWidth(),title = "Community") {
            Column {
                entries.forEach { (name, info, url) ->
                    PclListItem(
                        title = name,
                        info = info,
                        logo = PclIcons.otherVote,
                        logoScale = 0.9f,
                        height = 48.dp,
                        infoColor = p.gray2,
                        buttons = listOf(
                            com.zalith.pcl.ui.pcl2.components.PclListItemButton(
                                logo = PclIcons.arrowRight,
                                tooltip = "Open",
                            ) { openLink(context, url) },
                        ),
                        onClick = { openLink(context, url) },
                    )
                }
            }
        }
        PclBottomSpacer()
    }
}
