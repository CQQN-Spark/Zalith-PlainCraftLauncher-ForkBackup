/*
 * Zalith Launcher 2 — PCL2 UI port
 *
 * 第三方登录（authlib-injector / 统一通行证）的全局询问框。
 *
 * 版本设置里选了「第三方登录」之后，启动该版本时如果本地还没有对应服务器的账户，
 * 就直接在这里问账号密码、当场登录并切成当前账户 —— 与 PCL 在登录页里放一块
 * 「第三方登录」面板是同一个意思，只是把它挪到了真正需要它的那一刻。
 *
 * 为什么做成全局挂起询问（而不是挂在版本设置页里）：启动可以从首页、下载页、
 * 版本列表、服务器列表四个地方发起，而账户可能在启动前一刻才需要 —— 挂进页面
 * 会漏掉大部分链路（这个坑在「整合包装之前先问版本名」那轮已经踩过一次）。
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.zalith.pcl.ui.pcl2

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.zalith.pcl.game.account.Account
import com.zalith.pcl.game.account.AccountsManager
import com.zalith.pcl.game.account.auth_server.AuthServerHelper
import com.zalith.pcl.game.account.auth_server.data.AuthServer
import com.zalith.pcl.ui.androidText
import com.zalith.pcl.ui.pcl2.components.PclDualInputDialog
import com.zalith.pcl.ui.pcl2.components.PclNoticeDialog
import com.zalith.pcl.ui.pcl2.data.PclAuthLoginSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 一条待完成的第三方登录。
 *
 * [answer] 是给启动链路等的：登录成功给出账户，取消 / 失败给出 null。
 */
class PclThirdPartyLoginRequest(
    val serverName: String,
    val baseUrl: String,
    val registerUrl: String,
) {
    internal val answer = CompletableDeferred<Account?>()

    /** 正在跟服务器要凭据（此时输入框已经收起，改成一条「正在登录」提示）。 */
    var busy by mutableStateOf(false)

    /** 上一次尝试的失败原因（非空时先显示错误，关掉再回到输入框）。 */
    var error by mutableStateOf<String?>(null)

    /** 用户已经放弃这一条：后续回调不要再改动界面。 */
    internal var cancelled = false
}

/** 一次登录的结果：成功给账户，失败给原因（两者必有一个）。 */
private class LoginOutcome(val account: Account?, val error: String?)

object PclThirdPartyLogin {
    /** 当前没被处理完的那一条（null = 没有）。 */
    var pending by mutableStateOf<PclThirdPartyLoginRequest?>(null)
        private set

    /**
     * 挂起询问一次第三方登录。
     * @return 登录成功返回账户（`suspendSaveAccount` 已经把它设成当前账户）；取消返回 null
     */
    suspend fun ask(
        context: Context,
        serverName: String,
        baseUrl: String,
        registerUrl: String,
    ): Account? {
        // 1）主页面板里已经填过邮箱密码（本次输入或勾了「记住密码」）：
        //    直接拿去换凭据，别再弹一遍框 —— 用户刚在上面填完就要他再填一次是最恼人的。
        val saved = PclAuthLoginSettings.current(context, baseUrl)
        if (saved.usable) {
            val quiet = performLogin(context, serverName, baseUrl, registerUrl, saved.email, saved.password)
            if (quiet.account != null) return quiet.account
            // 静默登录失败（密码改了 / 服务器抽风）：落到输入框，并把报错原因带上
        }

        // 2）上一条还没答完（极端情况）：放它过去，别把协程挂死
        pending?.let { old ->
            pending = null
            old.cancelled = true
            old.answer.complete(null)
        }
        val request = PclThirdPartyLoginRequest(serverName, baseUrl, registerUrl)
        pending = request
        return request.answer.await()
    }

    /**
     * 真正干活的登录：调认证服务器 → 服务器与账户入库 → 设为当前账户。
     *
     * 不碰界面（没有 busy / error 状态），输入框那条路和静默登录那条路共用它。
     */
    private suspend fun performLogin(
        context: Context,
        serverName: String,
        baseUrl: String,
        registerUrl: String,
        email: String,
        password: String,
    ): LoginOutcome {
        val outcome = CompletableDeferred<LoginOutcome>()
        val helper = AuthServerHelper(
            baseUrl = baseUrl,
            serverName = serverName,
            email = email,
            password = password,
            onSuccess = { account, _ ->
                // 认证服务器先入库：账号管理页要靠它显示「服务器名称 / 注册链接」，
                // 少了这一行，账户在原生界面里会变成一条查不到服务器的孤魂。
                runCatching {
                    AccountsManager.saveAuthServer(
                        AuthServer(
                            baseUrl = baseUrl,
                            serverName = serverName,
                            register = registerUrl.ifBlank { null },
                        )
                    )
                }
                account.downloadYggdrasil()
                // 保存的同时会把当前账户切到它
                AccountsManager.suspendSaveAccount(account)
                if (!outcome.isCompleted) outcome.complete(LoginOutcome(account, null))
            },
            onFailed = { th ->
                if (!outcome.isCompleted) {
                    outcome.complete(LoginOutcome(null, th.message ?: th.javaClass.simpleName))
                }
            },
        )
        // 一个账号可能有多个角色：这里直接取第一个。PCL 的原生选择框在手机上一屏放不下，
        // 而且绝大多数皮肤站只给一个角色 —— 真要换角色，去「更多 → 账号」里换。
        helper.createNewAccount(context) { profiles, select ->
            select(profiles.first())
        }
        return outcome.await()
    }

    /** 提交账号密码，开始登录（输入框那条路）。 */
    fun submit(
        scope: CoroutineScope,
        context: Context,
        email: String,
        password: String,
    ) {
        val request = pending ?: return
        if (email.isBlank() || password.isBlank()) {
            request.error = "请输入邮箱 / 用户名与密码。"
            return
        }
        request.error = null
        request.busy = true

        scope.launch {
            val outcome = performLogin(
                context = context,
                serverName = request.serverName,
                baseUrl = request.baseUrl,
                registerUrl = request.registerUrl,
                email = email,
                password = password,
            )
            // 用户中途点过取消：结果回来也不再落库 / 切账户
            if (request.cancelled) return@launch
            request.busy = false
            if (outcome.account != null) {
                pending = null
                request.answer.complete(outcome.account)
            } else {
                request.error = outcome.error ?: "登录失败"
            }
        }
    }

    /**
     * 关掉输入框（用户点了取消 / 点了外面）。
     *
     * **正在登录时不能取消** —— `PclDualInputDialog` 的「登录」按钮点完也会走一次
     * `onDismiss`，如果这里无条件取消，刚提交的登录就会被自己打断。
     */
    fun cancel() {
        val request = pending ?: return
        // 正在登录、或者刚报过错（错误提示还挂着）时都不算「用户放弃」：
        // 前者不能自己打断自己，后者要在关掉错误后回到输入框重试。
        if (request.busy || request.error != null) return
        request.cancelled = true
        pending = null
        request.answer.complete(null)
    }

    /** 「正在登录」提示上的「取消」：这次是真的放弃（结果回来也不再用它）。 */
    fun abort() {
        val request = pending ?: return
        request.cancelled = true
        pending = null
        request.answer.complete(null)
    }

    /** 关掉错误提示，回到输入框重试。 */
    fun clearError() {
        pending?.error = null
    }
}

/**
 * 挂在窗体最外层的第三方登录询问框（见 [PclThirdPartyLogin]）。
 *
 * 三种状态互斥：错误 → 正在登录 → 输入框，不会叠两层弹窗。
 */
@Composable
fun PclThirdPartyLoginHost() {
    val request = PclThirdPartyLogin.pending ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    when {
        request.error != null -> PclNoticeDialog(
            title = "第三方登录失败",
            content = request.error.orEmpty(),
            onDismiss = { PclThirdPartyLogin.clearError() },
        )

        request.busy -> PclNoticeDialog(
            title = "第三方登录",
            content = "正在登录「${request.serverName}」……\n\n" +
                    "服务器：${request.baseUrl}",
            confirmText = "取消",
            onDismiss = { PclThirdPartyLogin.abort() },
        )

        else -> PclDualInputDialog(
            title = "第三方登录",
            label1 = "「${request.serverName}」的邮箱 / 用户名",
            label2 = "密码",
            hint2 = "密码只用于向该认证服务器换取凭据",
            confirmText = "登录",
            onConfirm = { email, password ->
                PclThirdPartyLogin.submit(scope, context, email, password)
            },
            onDismiss = { PclThirdPartyLogin.cancel() },
        )
    }
}
