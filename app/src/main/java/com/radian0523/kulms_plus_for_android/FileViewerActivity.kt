package com.radian0523.kulms_plus_for_android

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.radian0523.kulms_plus_for_android.ui.theme.KULMSTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

class FileViewerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_URL = "url"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val url = intent.getStringExtra(EXTRA_URL) ?: run { finish(); return }

        setContent {
            KULMSTheme {
                FileViewerScreen(url = url, onClose = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun FileViewerScreen(url: String, onClose: () -> Unit) {
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "閉じる")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val wv = webViewRef.value ?: return@IconButton
                        val currentUrl = wv.url ?: return@IconButton
                        val cookie = CookieManager.getInstance().getCookie(currentUrl) ?: ""
                        val cacheDir = wv.context.cacheDir
                        scope.launch(Dispatchers.IO) {
                            val file = downloadFile(cacheDir, currentUrl, cookie)
                            if (file != null) {
                                withContext(Dispatchers.Main) { openChooser(wv.context, file) }
                            }
                        }
                    }) {
                        Icon(Icons.Default.Download, contentDescription = "保存")
                    }
                }
            )
        }
    ) { padding ->
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.setSupportZoom(true)
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    webViewClient = object : WebViewClient() {
                        /**
                         * メインフレームのリクエストを検査する。
                         * - HTML/XHTML → null を返して WebView に通常処理させる
                         * - PDF 等のファイル → ダウンロードして外部アプリで開き、Activity を閉じる
                         *
                         * HEAD リクエストで Content-Type を確認するので WebView の GET と合わせ
                         * 計2リクエストになるが、Android WebView が PDF を表示できない問題の回避策として採用。
                         */
                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest
                        ): WebResourceResponse? {
                            if (!request.isForMainFrame) return null
                            if (request.method != "GET") return null

                            val reqUrl = request.url.toString()
                            val cookie = CookieManager.getInstance().getCookie(reqUrl) ?: ""

                            // HEAD リクエストで Content-Type を確認
                            val contentType = try {
                                val conn = (URL(reqUrl).openConnection() as HttpURLConnection).apply {
                                    requestMethod = "HEAD"
                                    if (cookie.isNotEmpty()) setRequestProperty("Cookie", cookie)
                                    setRequestProperty("User-Agent", "Mozilla/5.0 (Android)")
                                    connectTimeout = 10_000
                                    instanceFollowRedirects = true
                                }
                                val ct = conn.contentType ?: ""
                                conn.disconnect()
                                ct
                            } catch (e: Exception) {
                                Log.w("FileViewer", "HEAD failed: ${e.message}")
                                return null
                            }

                            val mime = contentType.substringBefore(";").trim().lowercase()

                            // HTML ページは WebView に任せる
                            if (mime.isEmpty() || mime == "text/html" || mime == "application/xhtml+xml") {
                                return null
                            }

                            // ファイル系：ダウンロードして外部アプリで開く
                            CoroutineScope(Dispatchers.IO).launch {
                                val file = downloadFile(ctx.cacheDir, reqUrl, cookie)
                                if (file != null) {
                                    Handler(Looper.getMainLooper()).post {
                                        openChooser(ctx, file)
                                        onClose()
                                    }
                                }
                            }

                            // WebView にはローディングページを返す
                            val loadingHtml = """
                                <html><body style="
                                    display:flex;justify-content:center;align-items:center;
                                    height:100vh;margin:0;font-family:sans-serif;
                                    background:#fafafa;color:#555;font-size:16px;">
                                  <p>読み込み中...</p>
                                </body></html>
                            """.trimIndent()
                            return WebResourceResponse(
                                "text/html", "UTF-8",
                                ByteArrayInputStream(loadingHtml.toByteArray(Charsets.UTF_8))
                            )
                        }
                    }
                    // Content-Disposition: attachment のファイルもダウンロードして chooser を表示
                    setDownloadListener { dlUrl, _, contentDisposition, mimetype, _ ->
                        val dlCookie = CookieManager.getInstance().getCookie(dlUrl) ?: ""
                        CoroutineScope(Dispatchers.IO).launch {
                            val file = downloadFile(ctx.cacheDir, dlUrl, dlCookie, contentDisposition, mimetype)
                            if (file != null) {
                                withContext(Dispatchers.Main) { openChooser(ctx, file) }
                            }
                        }
                    }
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    loadUrl(url)
                    webViewRef.value = this
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        )
    }
}

private fun downloadFile(
    cacheDir: File,
    url: String,
    cookie: String,
    contentDisposition: String = "",
    mimetype: String = ""
): File? {
    return try {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            if (cookie.isNotEmpty()) setRequestProperty("Cookie", cookie)
            setRequestProperty("User-Agent", "Mozilla/5.0 (Android)")
            connectTimeout = 30_000
            readTimeout = 60_000
            instanceFollowRedirects = true
        }
        if (conn.responseCode !in 200..299) { conn.disconnect(); return null }
        val disposition = contentDisposition.ifEmpty { conn.getHeaderField("Content-Disposition") ?: "" }
        val mime = mimetype.ifEmpty { conn.contentType ?: "" }
        val filename = extractFilename(disposition, url, mime)
        val dest = File(cacheDir, filename).also { it.delete() }
        conn.inputStream.use { i -> dest.outputStream().use { i.copyTo(it) } }
        conn.disconnect()
        dest
    } catch (e: Exception) {
        Log.e("FileViewer", "Download failed: ${e.message}")
        null
    }
}

private fun extractFilename(contentDisposition: String, url: String, mimetype: String): String {
    Regex("""filename\*=UTF-8''([^;\s]+)""", RegexOption.IGNORE_CASE)
        .find(contentDisposition)?.groupValues?.get(1)
        ?.let { return try { URLDecoder.decode(it, "UTF-8") } catch (e: Exception) { it } }
    Regex("""filename="?([^";]+)"?""", RegexOption.IGNORE_CASE)
        .find(contentDisposition)?.groupValues?.get(1)
        ?.let { return it.trim() }
    val last = url.substringAfterLast('/').substringBefore('?')
    if (last.isNotEmpty()) return last
    val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimetype) ?: "bin"
    return "download_${System.currentTimeMillis()}.$ext"
}

private fun openChooser(context: Context, file: File) {
    val mime = MimeTypeMap.getSingleton()
        .getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream"
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "ファイルを開く"))
}
