/*
 * Zalith Launcher 2 — PCL2 UI port
 *
 * 版本级「登录方式」的取值口径与地址归一化（对应 PCL 的
 * `Pages/PageInstance/PageInstanceSetup.xaml` → 服务器 → 登录方式）。
 *
 * PCL 在这个下拉里给了五项：
 *   正版登录或离线登录 / 仅正版登录 / 仅离线登录 /
 *   第三方登录：统一通行证 / 第三方登录：Authlib Injector 或 LittleSkin
 * 选择后两个时才展开「认证服务器 / 注册链接 / 服务器名称」这几行。
 *
 * 这里只做数据层的事（归一化、匹配账户、LittleSkin 预设），
 * 界面在 `pages/PclInstancePages.kt`，启动前的账户解析在 `data/PclRuntime.kt`。
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.zalith.pcl.ui.pcl2.data

import com.zalith.pcl.game.account.Account
import com.zalith.pcl.game.account.isAuthServerAccount
import com.zalith.pcl.game.version.installed.VersionConfig
import com.zalith.pcl.game.version.installed.VersionLoginType

/** LittleSkin 的 Yggdrasil 地址（PCL 的「设置为 LittleSkin」写的就是它）。 */
const val LITTLESKIN_AUTH_SERVER = "https://littleskin.cn/api/yggdrasil"

/** LittleSkin 的注册页。 */
const val LITTLESKIN_REGISTER_URL = "https://littleskin.cn/auth/register"

/** LittleSkin 显示的服务器名。 */
const val LITTLESKIN_SERVER_NAME = "LittleSkin"

/** 统一通行证（Nide8）的固定前缀，后面接服主给的服务器 ID。 */
private const val NIDE8_AUTH_PREFIX = "https://auth.mc-user.com:233/"

/** 这个版本要求的登录方式（旧配置缺字段时按「正版登录或离线登录」）。 */
fun VersionConfig.loginTypeValue(): VersionLoginType = VersionLoginType.from(loginType)

/** 是不是第三方登录（两种都要证书服务器地址 / ID）。 */
fun VersionLoginType.isThirdParty(): Boolean =
    this == VersionLoginType.UNIFIED_PASSPORT || this == VersionLoginType.AUTHLIB

/** 地址归一化：补 `https://`、去掉结尾斜杠；空串原样返回。 */
fun normalizeAuthServerUrl(raw: String): String {
    var url = raw.trim()
    if (url.isEmpty()) return ""
    if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
        url = "https://$url"
    }
    return url.trimEnd('/')
}

/**
 * 真正拿去登录 / 启动的认证服务器地址。
 *
 * · 统一通行证：输入框里是**服务器 ID**，这里拼成 `https://auth.mc-user.com:233/<id>`
 *   （与核心 `addOtherServer()` 的处理完全一致，否则 Nide8 账户会被当成普通 authlib 服务器）。
 * · Authlib：输入框里是完整地址，补协议、去尾斜杠即可。
 * · 其它登录方式返回空串（没有第三方服务器）。
 */
fun VersionConfig.loginBaseUrl(): String {
    val raw = loginAuthServer.trim()
    if (raw.isEmpty()) return ""
    return when (loginTypeValue()) {
        VersionLoginType.UNIFIED_PASSPORT -> {
            val id = raw.removePrefix(NIDE8_AUTH_PREFIX).trim().trim('/')
            if (id.isEmpty()) "" else "$NIDE8_AUTH_PREFIX$id"
        }
        VersionLoginType.AUTHLIB -> normalizeAuthServerUrl(raw)
        else -> ""
    }
}

/** 登录页 / 提示条上显示的服务器名。没填就给个通用的。 */
fun VersionConfig.loginServerTitle(): String =
    loginServerName.trim().ifEmpty { "Third party login" }

/** 这个账户是不是属于某个认证服务器。 */
fun Account.belongsToAuthServer(baseUrl: String): Boolean {
    if (!isAuthServerAccount()) return false
    val normalized = normalizeAuthServerUrl(baseUrl)
    if (normalized.isEmpty()) return false
    return normalizeAuthServerUrl(otherBaseUrl.orEmpty()).equals(normalized, ignoreCase = true)
}

/** 下拉里每一项的文案（与 PCL 的中文原文一致）。 */
fun VersionLoginType.displayName(): String = when (this) {
    VersionLoginType.MICROSOFT_OR_OFFLINE -> "Genuine login or offline login"
    VersionLoginType.MICROSOFT_ONLY -> "Only genuine login"
    VersionLoginType.OFFLINE_ONLY -> "Log in offline only"
    VersionLoginType.UNIFIED_PASSPORT -> "Third-party login: unified pass"
    VersionLoginType.AUTHLIB -> "Third-party login: Authlib Injector or LittleSkin"
}

/** 下拉里每一项的一句解释（放在卡片底部的蓝色提示里）。 */
fun VersionLoginType.description(): String = when (this) {
    VersionLoginType.MICROSOFT_OR_OFFLINE ->
        "Start with the currently selected account, both genuine accounts and offline accounts are available."
    VersionLoginType.MICROSOFT_ONLY ->
        "It is mandatory to use a Microsoft (genuine) account at startup; if the current account is not genuine, it will automatically switch to the logged-in genuine account."
    VersionLoginType.OFFLINE_ONLY ->
        "Force the use of an offline account at startup; suitable for versions that do not want to use a genuine account to enter the game."
    VersionLoginType.UNIFIED_PASSPORT ->
        "Unified Pass (Nide8): Fill in the server ID given by the server owner, and use the character on the server when starting."
    VersionLoginType.AUTHLIB ->
        "Universal external login (authlib-injector). " +
                "LittleSkin and various self-built skin sites fall into this category."
}
