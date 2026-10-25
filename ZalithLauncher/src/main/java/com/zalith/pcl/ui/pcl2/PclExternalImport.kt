/*
 * Zalith Launcher 2 — PCL2 UI port
 *
 * 外部导入（「分享到启动器」/「用启动器打开」）在 PCL2 界面里的落点。
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.zalith.pcl.ui.pcl2

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.zalith.pcl.game.control.ControlManager
import com.zalith.pcl.game.download.modpack.install.ModpackImporter
import com.zalith.pcl.ui.activities.IMPORT_TYPE_AUTO
import com.zalith.pcl.ui.activities.IMPORT_TYPE_CONTROLS
import com.zalith.pcl.ui.activities.IMPORT_TYPE_MODPACK
import com.zalith.pcl.ui.pcl2.components.PclHintTheme
import com.zalith.pcl.ui.pcl2.data.PclImportBridge
import com.zalith.pcl.ui.pcl2.data.PclImportRequest
import com.zalith.pcl.ui.pcl2.data.PclRuntime
import com.zalith.pcl.ui.pcl2.pages.pclDiscardIncompleteVersion
// 注意：`PclTitledTaskDialog` / `modpackImportErrorText` 在 `PclSectionPages.kt` 里，
// 那个文件的 package 是 `...ui.pcl2`（不是 `.pages`），与本文件同包，不需要 import。
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 处理「分享到启动器」送进来的整合包 / 键位文件。
 *
 * 之前这两类导入只认 `MainActivity`：`SplashActivity.handleImportIntent()` 不管
 * 「使用 PCL2 风格界面」这个开关，一律把导入转发给原生 Material 界面 —— 于是用户在文件管理器里
 * 长按整合包或键位 →「分享」→ 选启动器，界面会突然退回传统 ZL 界面。
 *
 * 现在 SplashActivity 按开关分流，请求先落到 [PclImportBridge]，由这里接手：
 * · 整合包 → `ModpackImporter`（和下载页、版本页那条导入链是同一套实现）；
 * · 键位 → `ControlManager.importControl`，导完刷新列表，版本设置的「控制布局」立刻能选到。
 */
@Composable
fun PclExternalImportHost(runtime: PclRuntime) {
    val context = LocalContext.current
    // 正在跑的整合包导入器（非空时显示进度弹窗）
    var importer by remember { mutableStateOf<ModpackImporter?>(null) }
    val request = PclImportBridge.pending

    LaunchedEffect(request) {
        val req = PclImportBridge.take() ?: return@LaunchedEffect
        when (val type = resolveImportKind(context, req)) {
            IMPORT_TYPE_MODPACK -> importer = startModpackImport(context, runtime, req.uri)
            IMPORT_TYPE_CONTROLS -> importControlLayout(context, req.uri)
            else -> {
                // `auto` 入口拿到的是 `*/*`，认不出来就明确告诉用户，别再「跳个界面就没动静」
                val name = displayNameOf(context, req.uri) ?: "这个文件"
                PclHintHost.push(
                    "「$name」不是启动器能识别的类型（支持 .json 键位布局与 .zip/.mrpack 整合包）。",
                    PclHintTheme.Yellow
                )
                // 类型常量留个引用，避免 when 分支被 lint 当成死代码
                if (type == IMPORT_TYPE_AUTO) return@LaunchedEffect
            }
        }
    }

    importer?.let { imp ->
        PclTitledTaskDialog(
            title = "正在导入整合包",
            tasks = imp.taskFlow,
            // 与资源页那张弹窗同规矩：取消必须真的停掉任务流，而不是只把弹窗藏起来。
            onCancel = {
                runtime.modpackImportIdOf(imp)?.let { runtime.cancelModpackImport(it) }
                    ?: runCatching { imp.cancel() }
                importer = null
            },
            onDismiss = { importer = null },
        )
    }
}

/**
 * 把一次导入请求解析成具体的处理类型。
 *
 * `auto` 入口（manifest 里声明通配类型的那个）拿到的类型信息全是通配，
 * 只能靠文件名后缀判断：`.json` → 键位布局，`.zip/.mrpack/.modpack` → 整合包。
 * 其它情况原样返回 `auto`，由调用方提示不支持。
 */
private fun resolveImportKind(context: Context, req: PclImportRequest): String {
    if (req.type != IMPORT_TYPE_AUTO) return req.type
    val name = displayNameOf(context, req.uri)?.lowercase().orEmpty()
    return when {
        name.endsWith(".json") -> IMPORT_TYPE_CONTROLS
        name.endsWith(".zip") ||
        name.endsWith(".mrpack") ||
        name.endsWith(".modpack") ||
        name.endsWith(".mcbbs") ||
        name.endsWith(".rar") ||
        name.endsWith(".7z") -> IMPORT_TYPE_MODPACK
        else -> IMPORT_TYPE_AUTO
    }
}

/** 取内容 URI 对应的文件名（没有就返回 null）。 */
private fun displayNameOf(context: Context, uri: Uri): String? = runCatching {
    if (uri.scheme == "file") return@runCatching uri.lastPathSegment
    context.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
    }
}.getOrNull()

/** 起一条本地整合包导入。
 *
 * 逻辑照搬版本页「安装整合包 → 从本地文件」那条链（排队、撞名规避、失败清理半成品），
 * 只把「页面里的 holder」换成这里的 `importer` 状态。
 */
private fun startModpackImport(
    context: Context,
    runtime: PclRuntime,
    uri: Uri,
): ModpackImporter? {
    val newImporter = runCatching {
        ModpackImporter(
            context = context,
            uri = uri,
            // 挂在 Activity 的 scope 上：切版块 / 离开页面都不会把导入掐掉
            scope = runtime.backgroundScope,
            // 分享进来的整合包同样先问版本名（默认 = 整合包自己的名字），
            // 与版本页那条链共用 `pclAskVersionName` 的默认值/避让规则。
            waitForVersionName = { packName ->
                pclAskVersionName(packName, title = "设置版本名称")
            },
            waitForConfirmMobileData = { true },
        )
    }.onFailure {
        runtime.submitErrorText("整合包导入失败", modpackImportErrorText(it))
    }.getOrNull() ?: return null

    val importId = runtime.trackModpackImport("从分享导入整合包", importer = newImporter)
    PclHintHost.push("开始导入分享过来的整合包。", PclHintTheme.Blue)

    runtime.backgroundScope.launch {
        // `ModpackImporter` 共用写死的 `cache/temp_modpack`，两个整合包同时装必然互相踩，
        // 所以在安装锁上排队（和下载页那条链一致）。
        runtime.modpackInstallLock.withLock {
            // 前一条可能是刚被取消的，`cancel()` 之后它还要一个调度点才真停
            delay(400)
            if (!runtime.isModpackImportTracked(importId)) return@withLock
            newImporter.startImport(
                isRunning = {
                    PclHintHost.push("已经有一个整合包在导入了，请等它完成。", PclHintTheme.Yellow)
                },
                onFinished = { name ->
                    PclHintHost.push("整合包「$name」安装完成。", PclHintTheme.Green)
                    runtime.clearModpackImport(importId)
                    runtime.refreshVersions()
                },
                onCancelled = {
                    runtime.clearModpackImport(importId)
                    if (pclDiscardIncompleteVersion(newImporter.resolvedVersionNameOrNull())) {
                        runtime.refreshVersions()
                    }
                },
                onError = { th ->
                    runtime.clearModpackImport(importId)
                    // 主动取消会以 CancellationException 落到这里，那不是故障
                    if (th !is CancellationException) {
                        // 任务流因异常结束时 `GameInstaller` 不清目标目录，留下的半成品会一直占着版本名
                        if (pclDiscardIncompleteVersion(newImporter.resolvedVersionNameOrNull())) {
                            runtime.refreshVersions()
                        }
                        runtime.submitErrorText("整合包导入失败", modpackImportErrorText(th))
                    }
                },
            )
            // 结束信号看任务表（所有出口都会清），不等导入器的回调
            runtime.modpackTaskIds.first { importId !in it }
        }
    }
    return newImporter
}

/** 导入一个键位（控制布局）JSON。 */
private suspend fun importControlLayout(context: Context, uri: Uri) {
    // 先给个即时反馈：导入到落盘之间有一小段耗时，什么都不显示会被当成「点了没反应」
    PclHintHost.push("正在导入键位布局…", PclHintTheme.Blue)
    val error = withContext(Dispatchers.IO) {
        runCatching {
            val stream = context.contentResolver.openInputStream(uri)
                ?: return@runCatching "无法读取这个键位文件（没有读取权限）。"
            var failure: String? = null
            stream.use {
                ControlManager.importControl(
                    inputStream = it,
                    onSerializationError = { e -> failure = "这个文件不是启动器能识别的键位布局：${e.message}" },
                    catchedError = { e -> failure = "键位导入失败：${e.message}" },
                )
            }
            // 列表要重新扫一遍，版本设置里的「控制布局」下拉才会出现新导入的那份
            ControlManager.refresh()
            failure
        }.getOrElse { "键位导入失败：${it.message}" }
    }
    if (error == null) {
        PclHintHost.push("键位布局已导入，在「控制布局」里就能选到它。", PclHintTheme.Green)
    } else {
        PclHintHost.push(error, PclHintTheme.Red)
    }
}
