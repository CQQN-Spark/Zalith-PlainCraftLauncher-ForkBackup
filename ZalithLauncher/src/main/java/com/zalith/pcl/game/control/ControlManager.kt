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

package com.zalith.pcl.game.control

import android.content.Context
import com.movtery.layer_controller.data.lang.createTranslatable
import com.movtery.layer_controller.layout.ControlLayout
import com.movtery.layer_controller.layout.EmptyControlLayout
import com.movtery.layer_controller.layout.EmptyLayoutInfo
import com.movtery.layer_controller.layout.loadLayoutFromFile
import com.movtery.layer_controller.layout.loadLayoutFromFileUncheck
import com.movtery.layer_controller.layout.loadLayoutFromString
import com.movtery.layer_controller.observable.ObservableControlLayout
import com.movtery.layer_controller.utils.newRandomFileName
import com.movtery.layer_controller.utils.saveToFile
import com.zalith.pcl.context.copyAssetFile
import com.zalith.pcl.path.PathManager
import com.zalith.pcl.setting.AllSettings
import com.zalith.pcl.utils.file.readString
import com.zalith.pcl.utils.logging.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.InputStream

private const val TAG = "ControlManager"

/**
 * 控制布局管理者
 */
object ControlManager {
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _dataList = MutableStateFlow<List<ControlData>>(emptyList())
    val dataList = _dataList.asStateFlow()

    private var currentJob: Job? = null

    private val _selectedLayout = MutableStateFlow<ControlData?>(null)
    /** 当前选择的控制布局 */
    val selectedLayout = _selectedLayout.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    /** 是否正在刷新控制布局 */
    val isRefreshing = _isRefreshing.asStateFlow()

    /**
     * 获取一个新的布局文件文件，名称随机
     */
    private fun getNewRandomFile() = File(PathManager.DIR_CONTROL_LAYOUTS, "${newRandomFileName()}.json")

    /**
     * 检查当前是否不存在控制布局，不存在则解压一份默认控制布局
     * @param context 访问assets的上下文
     */
    fun checkDefaultAndRefresh(context: Context) {
        scope.launch(Dispatchers.IO) {
            val files = (PathManager.DIR_CONTROL_LAYOUTS.listFiles() ?: emptyArray())
                .filter { file ->
                    file.isFile && file.exists() && file.extension.equals("json", true)
                }
            if (files.isEmpty()) {
                unpackDefaultControl(context)
            }
            refresh()
        }
    }

    fun refresh() {
        currentJob?.cancel()
        currentJob = scope.launch(Dispatchers.IO) {
            _isRefreshing.update { true }

            _dataList.update { emptyList() }
            PathManager.DIR_CONTROL_LAYOUTS.listFiles()?.mapNotNull { file ->
                if (!(file.isFile && file.exists() && file.extension.equals("json", true))) return@mapNotNull null

                var isSupport = true
                val layout: ControlLayout = try {
                    loadLayoutFromFile(file)
                } catch (_: IllegalArgumentException) {
                    isSupport = false
                    runCatching {
                        loadLayoutFromFileUncheck(file)
                    }.onFailure { e ->
                        Logger.warning(TAG, "Failed to load control layout! file = $file", e)
                    }.getOrNull() ?: EmptyControlLayout
                } catch (e: Exception) {
                    // 【别再 return@mapNotNull null】以前这里把解析不了的文件直接丢掉，
                    // 表现就是「导进来了，但这一页什么都不多」—— 用户完全无从判断是没导进去
                    // 还是文件坏了。现在保留成一行「不支持的布局」，至少看得见、能删掉。
                    Logger.warning(TAG, "Failed to load control layout! file = $file", e)
                    isSupport = false
                    EmptyControlLayout
                }

                ControlData(
                    file = file,
                    controlLayout = ObservableControlLayout(layout),
                    isSupport = isSupport
                )
            }?.let { list ->
                _dataList.update {
                    list.sortedBy {
                        if (it.isSupport) it.controlLayout.info.name.default
                        else it.file.name
                    }
                }
            }
            checkSettings()

            _isRefreshing.update { false }
        }
    }

    /**
     * 检查并更新设置
     */
    private fun checkSettings() {
        val setting = AllSettings.controlLayout.getValue()

        val layout = _dataList.value.find { it.file.name == setting && it.isSupport }
            ?: dataList.value.firstOrNull { it.isSupport }
                ?.also { AllSettings.controlLayout.save(it.file.name) }

        if (layout == null) {
            AllSettings.controlLayout.reset()
        }

        _selectedLayout.update { layout }
    }

    /**
     * 解压默认控制布局
     */
    private suspend fun unpackDefaultControl(
        context: Context
    ) = withContext(Dispatchers.IO) {
        try {
            val file = getNewRandomFile()
            context.copyAssetFile(fileName = "default_layout.json", output = file, overwrite = false)
        } catch (e: Exception) {
            Logger.warning(TAG, "Failed to unpack default control layout", e)
        }
    }

    /**
     * 选择控制布局
     */
    fun selectControl(data: ControlData) {
        if (!data.file.exists() || !data.isSupport) return
        AllSettings.controlLayout.save(data.file.name)
        _selectedLayout.update { data }
    }

    /**
     * 在协程内删除控制布局
     */
    fun deleteControl(data: ControlData) {
        scope.launch(Dispatchers.IO) {
            if (!data.file.exists()) return@launch
            FileUtils.deleteQuietly(data.file)
            refresh()
        }
    }

    /**
     * 在协程内保存控制布局的数据
     */
    fun saveControl(
        data: ControlData,
        submitError: (Exception) -> Unit
    ) {
        scope.launch(Dispatchers.IO) {
            if (!data.file.exists()) {
                refresh()
                return@launch
            }
            val layout = data.controlLayout.pack()
            try {
                layout.saveToFile(data.file)
            } catch (e: Exception) {
                submitError(e)
//                FileUtils.deleteQuietly(data.file)
            }
            refresh()
        }
    }

    /**
     * 新建一份空白控制布局。
     *
     * 对应原生 `ControlManageScreen` 的「新建」：`EmptyControlLayout` 换掉 info 的
     * 名称/作者/版本号之后直接存盘（`ControlViewModel.createNew`）。
     */
    fun createEmptyControl(name: String, submitError: (Exception) -> Unit) {
        scope.launch(Dispatchers.IO) {
            val file = getNewRandomFile()
            try {
                EmptyControlLayout.copy(
                    info = EmptyLayoutInfo.copy(
                        name = createTranslatable(default = name),
                        author = createTranslatable(default = ""),
                        versionName = ""
                    )
                ).saveToFile(file)
            } catch (e: Exception) {
                submitError(e)
                FileUtils.deleteQuietly(file)
            }
            refresh()
        }
    }

    /**
     * 复制一份已有的控制布局。
     *
     * 对应原生 `ControlViewModel.copyNew`：`ObservableControlLayout.pack()` 出来原样另存。
     */
    fun duplicateControl(data: ControlData, submitError: (Exception) -> Unit) {
        scope.launch(Dispatchers.IO) {
            val file = getNewRandomFile()
            try {
                data.controlLayout.pack().saveToFile(file)
            } catch (e: Exception) {
                submitError(e)
                FileUtils.deleteQuietly(file)
            }
            refresh()
        }
    }

    /**
     * 尝试导入控制布局
     *
     * 解析失败时**不要**把文件留在目录里（否则列表里会多一份「不支持的布局」，
     * 看着像导入成功了却打不开），所以两条错误分支都会先删文件再回调。
     */
    suspend fun importControl(
        inputStream: InputStream,
        onSerializationError: (Exception) -> Unit,
        catchedError: (Exception) -> Unit,
        onFinished: () -> Unit = {},
    ) = withContext(Dispatchers.IO) {
        val file = getNewRandomFile()
        try {
            inputStream.use { stream ->
                val jsonString = stream.readString().stripJsonNoise()
                val layout = loadLayoutFromString(jsonString)
                layout.saveToFile(file)
            }
            Logger.info(TAG, "控制布局导入成功：${file.name}")
            onFinished()
        } catch (e: SerializationException) {
            FileUtils.deleteQuietly(file)
            Logger.warning(TAG, "控制布局不是可识别的 JSON 格式", e)
            onSerializationError(e)
        } catch (e: IllegalArgumentException) {
            // `loadLayoutFromString` 在文件的 editorVersion 比本启动器新时抛这个
            // （`Control layout versions are not supported!`）。原样抛给用户是一句英文，
            // 这里换成能看懂的话 —— 注意 SerializationException 也继承自
            // IllegalArgumentException，所以必须排在它后面。
            FileUtils.deleteQuietly(file)
            Logger.warning(TAG, "控制布局版本过新", e)
            catchedError(IllegalArgumentException("这个布局来自更新版本的启动器，请先升级启动器再导入。", e))
        } catch (e: Exception) {
            FileUtils.deleteQuietly(file)
            Logger.warning(TAG, "控制布局导入失败", e)
            catchedError(e)
        }
    }
}

/**
 * 去掉 JSON 文本里的「隐形垃圾」。
 *
 * 最常见的两种：
 *   · **UTF-8 BOM**（`EF BB BF`）—— 用 Windows 记事本 / 某些下载站另存过的文件都带，
 *     `IOUtils.toString(UTF_8)` 会把它保留成 `\uFEFF`，kotlinx.serialization 直接抛
 *     「Unexpected JSON token at offset 0」，表现就是「这份布局导不进来」，而且报错
 *     里一个中文字符都没有，用户根本看不出是 BOM 的问题。
 *   · **零宽空格 / 首尾空白** —— 复制粘贴拼出来的文件常见。
 */
private fun String.stripJsonNoise(): String =
    trimStart('\uFEFF', '\u200B').trim()

/**
 * 打开 SAF 文档 Uri 的输入流。
 *
 * `openInputStream` 对少数第三方文件管理器（以及没有给 MIME 提示的 provider）
 * 会返回 null 甚至抛异常，这时退回 `openFileDescriptor` 拿原始 fd —— 两条都失败
 * 才真的读不到。
 */
fun openDocumentStream(context: Context, uri: android.net.Uri): InputStream? {
    runCatching { context.contentResolver.openInputStream(uri) }
        .getOrNull()
        ?.let { return it }
    return runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.let {
            java.io.FileInputStream(it.fileDescriptor)
        }
    }.getOrNull()
}