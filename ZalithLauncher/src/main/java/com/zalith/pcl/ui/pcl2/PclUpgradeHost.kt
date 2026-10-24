/*
 * Zalith Launcher 2 — PCL2 UI port
 *
 * 启动器更新的全局弹窗宿主。
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.zalith.pcl.ui.pcl2

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zalith.pcl.BuildConfig
import com.zalith.pcl.setting.AllSettings
import com.zalith.pcl.ui.pcl2.components.PclSelectDialog
import com.zalith.pcl.ui.pcl2.components.PclUpgradeDialog
import com.zalith.pcl.ui.pcl2.components.PclUpgradeFilesDialog
import com.zalith.pcl.ui.pcl2.components.pclMarkdownToPlain
import com.zalith.pcl.upgrade.RemoteData
import com.zalith.pcl.upgrade.findCurrentBody
import com.zalith.pcl.upgrade.getCurrentCouldDrive
import com.zalith.pcl.viewmodel.LauncherUpgradeOperation
import com.zalith.pcl.viewmodel.LauncherUpgradeViewModel
import java.util.Locale

/**
 * 启动器更新弹窗（PCL 界面）。
 *
 * ---- 为什么必须单独挂一个宿主
 *
 * 上游只有 `MainActivity` 会渲染 [LauncherUpgradeOperation]，而 PCL 界面跑在
 * `Pcl2Activity` —— 于是「关于」页点「检查更新」时，`LauncherUpgradeViewModel` 确实
 * 抓到了新版本、也确实把 `operation` 设成了 `Upgrade`，但**没有任何地方去画它**，
 * 表现就是「点了完全没反应，连个弹窗都没有」。
 *
 * 这里挂在 `PclMainContent` 上（与 `PclNamePromptHost` 同一层），所以：
 *   · 「关于」页手动检查 → 本宿主弹出 PCL 风格的更新框；
 *   · 启动时自动检查（[LauncherUpgradeViewModel.checkOnAppStart]）→ 同样是这个框。
 *
 * 用的是 **Activity 作用域的同一个 ViewModel 实例**（`viewModel()` 默认取
 * `LocalViewModelStoreOwner`，与「关于」页里拿到的是同一个），不需要任何额外转发。
 */
@Composable
fun PclUpgradeHost() {
    val context = LocalContext.current
    val upgradeViewModel: LauncherUpgradeViewModel = viewModel()

    // 启动时自动检查一次（内部有 1 小时限频，不会每次都打网络）。
    LaunchedEffect(Unit) {
        upgradeViewModel.checkOnAppStart()
    }

    when (val operation = upgradeViewModel.operation) {
        LauncherUpgradeOperation.None -> {}

        is LauncherUpgradeOperation.Upgrade -> {
            val data = operation.data
            val body = remember(data) {
                data.findCurrentBody(Locale.getDefault()) ?: data.defaultBody
            }
            // 按钮回调里已经把 operation 推到下一步（或清空）了，退出动画结束时的
            // `onLater` 就不能再写一次 —— 否则会把刚设置的 SelectApk 覆盖成 None，
            // 「立即更新」点了什么都不发生（与 PclConfirmDialog 那条注释同一个坑）。
            var navigated by remember(data) { mutableStateOf(false) }

            PclUpgradeDialog(
                versionText = "当前版本 ${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）" +
                    "  →  最新版本 ${data.version}（${data.code}）",
                bodyText = pclMarkdownToPlain(body.markdown),
                onDownload = {
                    navigated = true
                    val files = data.files
                    when {
                        // 资产没取到（GitHub API 不通 / 还没上传）→ 退回网盘
                        files.isEmpty() -> {
                            val drive = data.currentCloudDrive()
                            upgradeViewModel.operation = if (drive == null) {
                                LauncherUpgradeOperation.None
                            } else {
                                LauncherUpgradeOperation.OpenCloudDrive(drive)
                            }
                        }
                        files.size == 1 -> {
                            openLink(context, files.first().uri)
                            upgradeViewModel.operation = LauncherUpgradeOperation.None
                        }
                        else -> upgradeViewModel.operation = LauncherUpgradeOperation.SelectApk(data)
                    }
                },
                onIgnore = {
                    navigated = true
                    AllSettings.lastIgnoredVersion.save(data.code)
                    upgradeViewModel.operation = LauncherUpgradeOperation.None
                },
                onLater = {
                    if (!navigated) upgradeViewModel.operation = LauncherUpgradeOperation.None
                },
            )
        }

        is LauncherUpgradeOperation.SelectApk -> {
            PclUpgradeFilesDialog(
                files = operation.data.files.map { it.fileName to it.uri },
                onSelect = { uri ->
                    openLink(context, uri)
                    upgradeViewModel.operation = LauncherUpgradeOperation.None
                },
                onDismiss = { upgradeViewModel.operation = LauncherUpgradeOperation.None },
            )
        }

        is LauncherUpgradeOperation.OpenCloudDrive -> {
            val drive = operation.cloudDrive
            val options = if (drive.links.isEmpty()) {
                listOf("默认链接" to drive.link)
            } else {
                drive.links.map { it.name to it.link }
            }
            PclSelectDialog(
                title = "选择网盘下载",
                options = options,
                current = null,
                onSelect = { link ->
                    openLink(context, link)
                    upgradeViewModel.operation = LauncherUpgradeOperation.None
                },
                onDismiss = { upgradeViewModel.operation = LauncherUpgradeOperation.None },
            )
        }
    }
}

/** 当前语言对应的网盘；没配多网盘时退回 [RemoteData.defaultCloudDrive]。 */
private fun RemoteData.currentCloudDrive(): RemoteData.CloudDrive? {
    return getCurrentCouldDrive(Locale.getDefault()) ?: defaultCloudDrive
}
