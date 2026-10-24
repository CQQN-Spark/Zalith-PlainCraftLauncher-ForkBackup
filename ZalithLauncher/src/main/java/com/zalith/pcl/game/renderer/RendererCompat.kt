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

package com.zalith.pcl.game.renderer

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import com.zalith.pcl.game.plugin.driver.DriverPluginManager
import com.zalith.pcl.game.renderer.renderers.GL4ESRenderer
import com.zalith.pcl.game.renderer.renderers.KopperZinkRenderer
import com.zalith.pcl.game.renderer.renderers.NGGL4ESRenderer
import com.zalith.pcl.game.version.installed.Version
import com.zalith.pcl.game.version.installed.utils.isBiggerVer
import com.zalith.pcl.game.version.installed.utils.isLowerVer
import com.zalith.pcl.path.PathManager
import com.zalith.pcl.setting.AllSettings
import com.zalith.pcl.utils.device.VulkanCapabilities
import com.zalith.pcl.utils.device.VulkanChecker
import com.zalith.pcl.utils.device.normalizeMcVersion
import com.zalith.pcl.utils.device.supportFor
import com.zalith.pcl.utils.logging.Logger
import java.io.File
import java.util.zip.ZipFile

private const val TAG = "RendererCompat"

/**
 * 「渲染器：自动选择」的实现。
 *
 * 背景：Sodium / Embeddium / Rubidium / Iris / Oculus 这一代区块渲染模组，
 * 依赖桌面 OpenGL 3.3+ 的核心特性（base-vertex 多重绘制、持久映射缓冲、间接绘制等）。
 * GL4ES 系的翻译层（`libgl4es_*.so`）最高只暴露 GL 3.1，这类模组在上面
 * 会在贴图图集加载完后彻底静默（游戏日志里一堆 `*.png-atlas` 之后没有下文，看起来像卡死）。
 *
 * 但**「换成 Kopper Zink」并不是万能解**：zink 是 Vulkan → 桌面 GL 的翻译层，
 * 它的能力完全取决于设备的 Vulkan 驱动。只报 Vulkan 1.0 / 1.1 的厂商驱动
 * （典型是 Kirin / Mali Bifrost 这类老机型）连 GL 上下文都建不出来，
 * 换过去等于把「能跑的 GLES 包装层」换成「一条路都不剩」。
 *
 * 所以自动选择必须**按设备能力**来定：先看设备真实的 Vulkan 能力（原生枚举扩展与特性），
 * 再看 GLES 版本，最后再看模组需求和渲染器自身支持的 MC 版本范围。
 * 具体规则见 [applyAutoCompat]。
 */
object RendererCompat {
    /**
     * 需要桌面 OpenGL 3.3+ 的区块渲染模组：关键字 → 展示名。
     * 关键字按「文件名分词」匹配，所以 `sodium-fabric-0.5.13+mc1.20.1.jar`、
     * `reeses-sodium-options-...jar` 都能命中。
     */
    private val DESKTOP_GL_MODS: Map<String, String> = linkedMapOf(
        "sodium" to "Sodium",
        "embeddium" to "Embeddium",
        "rubidium" to "Rubidium",
        "iris" to "Iris",
        "oculus" to "Oculus"
    )

    /** 展示顺序固定按 [DESKTOP_GL_MODS] 的声明顺序，避免依赖文件系统枚举顺序。 */
    private val DISPLAY_ORDER: List<String> = DESKTOP_GL_MODS.values.toList()

    private val TOKEN_SPLIT = Regex("[^a-z0-9]+")
    private val MOD_ID_PATTERN = Regex("\"id\"\\s*:\\s*\"([^\"]+)\"")

    /**
     * 当前渲染器是否属于 GL4ES 翻译层家族。
     *
     * 用渲染器库名判断而不是渲染器类型 —— 用户装的渲染器插件同样可能基于
     * GL4ES（FCL 插件就有 gl4es 版），按库名判断能一并覆盖：
     * 内置的 [com.zalith.pcl.game.renderer.renderers.GL4ESRenderer] 是
     * `libgl4es_114.so`，[com.zalith.pcl.game.renderer.renderers.NGGL4ESRenderer]
     * 是 `libng_gl4es.so`，都含 `gl4es`。
     */
    fun isGL4ESFamily(renderer: RendererInterface): Boolean =
        renderer.getRendererLibrary().contains("gl4es", ignoreCase = true)

    /**
     * 扫描实例的 mods 目录，返回其中需要桌面 OpenGL 3.3+ 的模组展示名。
     * `xxx.jar.disabled` 这类被禁用的模组不会命中（判据是 `.jar` 结尾）。
     */
    fun scanDesktopGlMods(gameDir: File): List<String> {
        val modsDir = File(gameDir, "mods")
        if (!modsDir.isDirectory) return emptyList()

        val found = linkedSetOf<String>()
        modsDir.listFiles()?.forEach { file ->
            if (!file.isFile) return@forEach
            val name = file.name.lowercase()
            if (!name.endsWith(".jar")) return@forEach

            val hit = matchByFileName(name) ?: matchByModId(file)
            if (hit != null) found += hit
        }
        // listFiles() 的枚举顺序在 Android / Windows 上都不保证，这里按固定顺序产出
        return found.sortedBy { DISPLAY_ORDER.indexOf(it) }
    }

    /** 按文件名分词匹配，覆盖绝大多数正常命名的整合包。 */
    private fun matchByFileName(fileName: String): String? {
        val tokens = TOKEN_SPLIT.split(fileName)
        return DESKTOP_GL_MODS.entries
            .firstOrNull { (key, _) -> tokens.contains(key) }
            ?.value
    }

    /** 文件名看不出来时（用户改过名），读 jar 内的 fabric / quilt 元数据补充判断。 */
    private fun matchByModId(file: File): String? {
        return try {
            ZipFile(file).use { zip ->
                var hit: String? = null
                for (meta in listOf("fabric.mod.json", "quilt.mod.json")) {
                    val entry = zip.getEntry(meta) ?: continue
                    val text = zip.getInputStream(entry).bufferedReader().use { it.readText() }.lowercase()
                    // 有的元数据把 id 写在别的字段前面，取第一个 "id" 就够了
                    val id = MOD_ID_PATTERN.find(text)?.groupValues?.get(1) ?: continue
                    hit = DESKTOP_GL_MODS.entries
                        .firstOrNull { (key, _) -> id == key || id.startsWith("$key-") }
                        ?.value
                    if (hit != null) break
                }
                hit
            }
        } catch (_: Throwable) {
            // 单个 jar 读不了不影响整体判断
            null
        }
    }

    /**
     * 本次「自动选择」落在哪个渲染器、以及为什么。由 [applyAutoCompat] 写入，
     * 启动日志会把它打成 `Renderer Auto: ...` 一行，排查时不用再猜。
     */
    var lastAutoDecision: String? = null
        private set

    /**
     * 启动前按**设备能力**决定渲染器，对应设置里的「渲染器：自动选择」。
     *
     * 判定顺序（不再「看到 Sodium 就切 Kopper Zink」）：
     * 1. 用户在设置里显式选过渲染器 → 一律不动，只在明显不搭时写日志；
     * 2. 实例里有 Sodium / Iris 这类**需要桌面 OpenGL 3.3+** 的模组，且本机 Vulkan 真能满足
     *    zink 的要求（Vulkan ≥ 1.2、且该 MC 版本需要的 Vulkan 依赖齐全）→ Kopper Zink；
     * 3. 设备支持 OpenGL ES 3.x → Krypton Wrapper（Pojav 的 `opengles3`，Android 上最通用的包装层）；
     * 4. 只支持 GLES 2.x → GL4ES；
     * 5. 候选渲染器都不支持当前 MC 版本 → 保持现状。
     *
     * 为什么第 2 条要卡死：Mesa 的 zink 文档写明它从 Vulkan 1.0 起步，
     * 但要提供 OpenGL 3.3（MC 1.17+ 与 Sodium 的门槛）需要 `VK_EXT_vertex_attribute_divisor` +
     * `occlusionQueryPrecise`，且要有 `scalarBlockLayout`（或 Vulkan 1.2 核心特性）才稳。
     * 只报 Vulkan 1.0 / 1.1 的厂商驱动（典型是 Kirin / Mali Bifrost）表面上有 Vulkan，
     * 实际 `eglCreateContext` 直接失败，游戏打印 `GLFW: Failed to create window context!`，
     * 之后 OpenGL / Vulkan 两个后端全灭 —— 也就是「启动一会儿自己退回启动器主页」。
     * 这类机器上真正能用的是 GLES 包装层，所以**绝不能**把它换成 zink。
     */
    fun applyAutoCompat(version: Version, activity: Activity? = null): RendererInterface? {
        if (Renderers.getRenderers().isEmpty()) return null
        // 用户关掉了「自动适配渲染器」：只保留默认（列表里第一个）渲染器，不替他挑
        if (!AllSettings.autoRendererCompat.getValue()) {
            lastAutoDecision = "已关闭自动适配 → 使用默认渲染器"
            return null
        }
        val current = runCatching { Renderers.getCurrentRenderer() }.getOrNull()

        // 1. 用户显式指定过渲染器：尊重他的选择，只在能力明显不匹配时留下日志
        if (version.getRenderer().isNotEmpty()) {
            diagnoseExplicitChoice(version, current)
            return null
        }

        if (activity == null) return null
        val mcVersion = version.getVersionInfo()?.minecraftVersion ?: return null

        val gles = deviceGlesVersion(activity)
        val caps = probeVulkanCapabilities(version)
        val vulkanText = caps?.versionString ?: "无"
        val mods = scanDesktopGlMods(version.getGameDir())
        val modList = mods.joinToString(" / ").ifEmpty { "无" }

        // zink 能不能撑住这台设备的 Vulkan：版本 ≥ 1.2，且该 MC 版本要求的 Vulkan 依赖齐全
        val zinkViable = caps != null &&
                caps.isVersionSupported &&
                caps.supportFor(normalizeMcVersion(mcVersion)).isSupported

        // 2. 候选优先级（后面还要过「渲染器自己是否支持这个 MC 版本」这一关）
        val candidates = buildList {
            if (mods.isNotEmpty() && zinkViable) add(KopperZinkRenderer)
            if (gles.first >= 3) add(NGGL4ESRenderer)
            add(GL4ESRenderer)
        }

        val reason = buildString {
            append("GLES ${gles.first}.${gles.second}，Vulkan $vulkanText，")
            append("桌面GL模组 [$modList]，zink 可用=$zinkViable")
        }

        val picked = candidates.firstOrNull { supportsMcVersion(it, mcVersion) }
        if (picked == null) {
            lastAutoDecision = "$reason → 没有可用的内置渲染器，保持 ${current?.getRendererName() ?: "默认"}"
            Logger.warning(TAG, "自动选择渲染器：$lastAutoDecision")
            return null
        }

        // 需要桌面 GL 3.3+ 的模组却用不上 zink：把后果写清楚，别让人以为自动选择没生效
        if (mods.isNotEmpty() && !zinkViable) {
            Logger.warning(
                TAG,
                "检测到需要桌面 OpenGL 3.3+ 的模组（$modList），但本机 Vulkan（$vulkanText）不满足 zink 的要求" +
                        "（需要 Vulkan 1.2 及以上与完整扩展），已使用 ${picked.getRendererName()}；" +
                        "这类模组在该渲染器上可能无法工作或卡在加载界面。"
            )
        }

        lastAutoDecision = "$reason → ${picked.getRendererName()}"
        Logger.info(TAG, "自动选择渲染器：$lastAutoDecision")

        if (current != null && current.getUniqueIdentifier() == picked.getUniqueIdentifier()) return null

        Renderers.setCurrentRenderer(picked.getUniqueIdentifier(), retryToFirstOnFailure = false)
        return picked
    }

    /**
     * 用户显式选过渲染器时的诊断：只在「明显不搭」时写一条日志，绝不改动选择。
     */
    private fun diagnoseExplicitChoice(version: Version, current: RendererInterface?) {
        if (current == null) return
        val mods = scanDesktopGlMods(version.getGameDir())
        if (mods.isEmpty()) return

        val modList = mods.joinToString(" / ")
        if (isGL4ESFamily(current) && current !== NGGL4ESRenderer) {
            Logger.warning(
                TAG,
                "检测到需要桌面 OpenGL 3.3+ 的模组（$modList），当前渲染器 ${current.getRendererName()} " +
                        "属于 GL4ES 翻译层、无法支撑，游戏很可能卡在加载界面。请在「设置 → 启动 → 渲染器」中改用 Krypton Wrapper。"
            )
        }
    }

    /** 渲染器自己声明的 MC 版本支持范围 */
    private fun supportsMcVersion(renderer: RendererInterface, mcVersion: String): Boolean {
        val min = renderer.getMinMCVersion()
        val max = renderer.getMaxMCVersion()
        if (min != null && mcVersion.isLowerVer(min)) return false
        if (max != null && mcVersion.isBiggerVer(max)) return false
        return true
    }

    /** 设备支持的 OpenGL ES 版本（major, minor），取系统声明的最高值 */
    private fun deviceGlesVersion(context: Context): Pair<Int, Int> {
        return runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val raw = am.deviceConfigurationInfo.reqGlEsVersion
            (raw shr 16) to (raw and 0xFFFF)
        }.getOrDefault(0 to 0)
    }

    /** 按当前驱动枚举 Vulkan 能力；同一进程内按驱动缓存，避免每次启动都重新建实例 */
    private val vulkanCapabilitiesCache: MutableMap<String, VulkanCapabilities?> = mutableMapOf()

    private fun probeVulkanCapabilities(version: Version): VulkanCapabilities? {
        val driver = runCatching { DriverPluginManager.getDriver(version.getDriver()) }.getOrNull()
        val useDriverPlugin = driver != null && !driver.isLauncher
        val cacheKey = if (useDriverPlugin) driver.path else "system"

        vulkanCapabilitiesCache[cacheKey]?.let { return it }
        val caps = runCatching {
            if (useDriverPlugin) {
                VulkanChecker.checkCapabilities(
                    driverPath = null,
                    nativeDir = driver.path,
                    cacheDir = File(PathManager.DIR_CACHE, "vulkan_temp").absolutePath
                )
            } else {
                VulkanChecker.checkCapabilities(driverPath = null, nativeDir = null, cacheDir = null)
            }
        }.onFailure {
            Logger.warning(TAG, "枚举 Vulkan 能力失败，按「不可用」处理", it)
        }.getOrNull()

        vulkanCapabilitiesCache[cacheKey] = caps
        return caps
    }
}
