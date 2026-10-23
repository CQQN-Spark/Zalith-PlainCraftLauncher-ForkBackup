/*
 * Zalith Launcher 2 — PCL2 UI port
 *
 * 「你知道吗 / 回声洞」的**联网文案源**。
 *
 * 官网（pcl-homepage-site）提供 `GET /api/random?type=hint|cave`，只返回审核通过的文案。
 * 这里优先联网抓取，抓不到（未配置 / 断网 / 超时 / 404）就回退到本地内置文案，
 * 保证任何情况下主页都有内容可显示。
 *
 * 站点根地址优先取 [DEFAULT_BASE_URL]（编译期，站点定下来后填这一行即可），
 * 也可以在运行期用 MMKV key `pcl_home_remote_url` 覆盖（后续加设置项用）。
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.movtery.zalithlauncher.ui.pcl2.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.movtery.zalithlauncher.setting.launcherMMKV
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object PclRemoteContent {

    /** MMKV key：运行期覆盖站点根地址用。 */
    private const val KEY_BASE_URL = "pcl_home_remote_url"

    /**
     * 站点根地址（编译期默认值）。站点域名确定后填这里，例如 `https://xxx.example.com`。
     * 留空表示还没配置 —— 这时只看 MMKV，两者都空则完全不联网、只用本地文案。
     */
    private const val DEFAULT_BASE_URL = "http://103.85.86.151:25997"

    private const val TIMEOUT_MS = 5000

    /** 站点根地址（去尾斜杠）。空 = 不联网。 */
    fun baseUrl(): String {
        val mmkv = runCatching { launcherMMKV().getString(KEY_BASE_URL, "").orEmpty() }.getOrDefault("")
        return DEFAULT_BASE_URL.ifBlank { mmkv }.trimEnd('/')
    }

    /**
     * 阻塞式联网随机取一条已审核文案；未配置 / 失败 / 无内容都返回 null。
     * **必须在 IO 线程调用。**
     */
    fun randomBlocking(type: String): String? {
        val base = baseUrl()
        if (base.isEmpty()) return null
        var conn: HttpURLConnection? = null
        return try {
            conn = URL("$base/api/random?type=$type").openConnection() as HttpURLConnection
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("User-Agent", "ZalithLauncher/2")
            conn.setRequestProperty("Accept", "application/json")
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            JSONObject(body).optString("text").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    /** 协程版：切到 IO 线程联网。 */
    suspend fun random(type: String): String? = withContext(Dispatchers.IO) { randomBlocking(type) }
}

/**
 * 随机文案（本地兜底 + 联网替换）。
 *
 * 先立刻返回一条本地随机文案（首帧就有内容），再在后台联网；
 * 拉到审核通过的新文案就替换掉本地那条。[reloadToken] 变化会重新随机并重新拉取。
 */
@Composable
fun rememberRemoteText(type: String, reloadToken: Int = 0): String {
    var text by remember(reloadToken, type) {
        mutableStateOf(PclHomePresets.randomLocal(type))
    }
    LaunchedEffect(type, reloadToken) {
        PclRemoteContent.random(type)?.let { text = it }
    }
    // 回声洞的默认文案已删、又没拉到联网文案时给一句占位，避免留白。
    return text.ifBlank {
        if (type == "cave") "回声洞暂时没有留言，去官网投稿吧～" else text
    }
}
