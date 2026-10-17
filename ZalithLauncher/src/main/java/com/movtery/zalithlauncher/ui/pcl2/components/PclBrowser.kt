/*
 * Zalith Launcher 2 — PCL2 UI port
 *
 * 启动器内置浏览器。
 *
 * PCL2 登录微软账户时用的是**设备码流程**：弹一个 `MyMsgLogin` 对话框显示验证码，
 * 验证地址则交给系统浏览器（`ModBase.OpenWebsite`）。手机上跳到系统浏览器意味着离开
 * 启动器，登录完了还得手动切回来，所以这里改成在启动器**内部**开一个网页层
 * （等价于 PCL 自己的 `MyWebBrowser` 内嵌窗口），关掉它就算退出登录。
 *
 * 同一组件也用来承载 PCL 的「自定义主页 → 联网更新」（`UiCustomType = 2`）。
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.movtery.zalithlauncher.ui.pcl2.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.movtery.zalithlauncher.ui.pcl2.theme.PclDimens
import com.movtery.zalithlauncher.ui.pcl2.theme.PclTextStyle
import com.movtery.zalithlauncher.ui.pcl2.theme.pclPalette

/** 内置浏览器要打开的一页。 */
data class PclWebPage(
    val url: String,
    /** 顶部显示的名字；为空就显示「内置浏览器」。 */
    val title: String? = null,
)

/**
 * 内置浏览器浮层：铺满整个启动器窗口，上方一条 PCL 风格的工具栏 + 网页本体。
 *
 * 关闭方式：按返回键（网页还能后退时先后退）、点右上角的「关闭」。
 */
@Composable
fun PclWebOverlay(
    page: PclWebPage,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = pclPalette
    var webView by remember(page) { mutableStateOf<WebView?>(null) }
    var currentUrl by remember(page) { mutableStateOf(page.url) }
    var loading by remember(page) { mutableStateOf(true) }

    // 有网页时返回键先交给网页历史，退无可退才关掉这一层 ——
    // 与 Zalith 原生界面「退出网页页 = 退出登录」的语义一致。
    BackHandler(enabled = true) {
        val view = webView
        if (view != null && view.canGoBack()) view.goBack() else onClose()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0x77000000)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.94f)
                .clip(RoundedCornerShape(PclDimens.formCorner))
                .background(p.sidebarBackground)
                .border(1.dp, p.color6, RoundedCornerShape(PclDimens.formCorner)),
        ) {
            // ---- 工具栏（PCL 的 MyMsgLogin 标题行风格：标题 15 号 + 右侧按钮）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(p.color7.copy(alpha = 0.62f))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PclText(
                    text = page.title ?: "内置浏览器",
                    color = p.color1,
                    fontSize = 15,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = currentUrl,
                    style = PclTextStyle.base(p).copy(color = p.gray2, fontSize = 11.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                PclTextButton(text = "刷新") { webView?.reload() }
                Spacer(Modifier.width(14.dp))
                PclTextButton(text = "关闭") { onClose() }
            }

            if (loading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = p.color3,
                    trackColor = p.color7,
                )
            } else {
                Spacer(Modifier.height(3.dp))
            }

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        configureLauncherWebView()
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                url?.let { currentUrl = it }
                                loading = true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                url?.let { currentUrl = it }
                                loading = false
                            }
                        }
                        loadUrl(page.url)
                        webView = this
                    }
                },
            )
        }
    }

    DisposableEffect(page) {
        onDispose {
            webView?.disposeWebView()
            webView = null
        }
    }
}

/**
 * 内嵌网页（无工具栏）：给「自定义主页 → 联网更新」用。
 */
@Composable
fun PclInlineWeb(
    url: String,
    modifier: Modifier = Modifier,
) {
    var webView by remember(url) { mutableStateOf<WebView?>(null) }
    var loading by remember(url) { mutableStateOf(true) }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    configureLauncherWebView()
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url2: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url2, favicon)
                            loading = true
                        }

                        override fun onPageFinished(view: WebView?, url2: String?) {
                            super.onPageFinished(view, url2)
                            loading = false
                        }
                    }
                    loadUrl(url)
                    webView = this
                }
            },
        )
        if (loading) {
            PclText(text = "正在加载自定义主页…", color = pclPalette.gray2)
        }
    }

    DisposableEffect(url) {
        onDispose {
            webView?.disposeWebView()
            webView = null
        }
    }
}

private fun WebView.disposeWebView() {
    runCatching {
        stopLoading()
        loadUrl("about:blank")
        clearHistory()
        removeAllViews()
        destroy()
    }
}

/**
 * 启动器内置浏览器的统一设置。
 *
 * 两个要点：
 * - **去掉 UA 里的 `; wv`**：微软账户的登录页会识别 WebView 并拒绝服务
 *   （「此浏览器或应用可能不安全」），去掉这个标记后按普通 Chrome 处理。
 * - **允许第三方 Cookie**：`login.microsoftonline.com` → `login.live.com` 是跨站跳转，
 *   Android 的 WebView 从 API 30 起默认拦截第三方 Cookie，不开会卡在登录页。
 */
@SuppressLint("SetJavaScriptEnabled")
@Suppress("DEPRECATION")
private fun WebView.configureLauncherWebView() {
    settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        databaseEnabled = true
        loadsImagesAutomatically = true
        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        userAgentString = userAgentString.replace("; wv", "")
        // 「自定义主页 → 读取本地文件」是 file:// 加载，需要放开文件访问，
        // 否则页面里的相对资源（css / 图片）会被拦掉。
        allowFileAccess = true
        allowContentAccess = true
        allowFileAccessFromFileURLs = true
        allowUniversalAccessFromFileURLs = true
    }
    runCatching {
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(this@configureLauncherWebView, true)
        }
    }
}
