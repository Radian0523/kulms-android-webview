package com.radian0523.kulms_plus_for_android.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.MimeTypeMap
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.radian0523.kulms_plus_for_android.FileViewerActivity
import androidx.core.content.FileProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import com.radian0523.kulms_plus_for_android.notification.NotificationHelper
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

/** セッション切れを示す例外。 */
class SessionExpiredException : Exception("Session expired")

/** 認証情報ログインの結果。 */
sealed class LoginResult {
    object Success : LoginResult()
    object OtpRequired : LoginResult()
    data class Failed(val message: String) : LoginResult()
}

/**
 * WebView管理 + chrome.storage ブリッジ + 拡張機能スクリプト注入。
 *
 * WebViewFetcher をベースに、fetch() / ensureOnLMS() を除去し、
 * ContentScriptInjector と KulmsStorageBridge を追加。
 */
@SuppressLint("SetJavaScriptEnabled", "StaticFieldLeak")
object WebViewManager {
    private const val TAG = "WebViewManager"
    const val BASE_URL = "https://lms.gakusei.kyoto-u.ac.jp"
    const val LOGIN_PORTAL_URL = "$BASE_URL/portal/login"
    private const val IIMC_HOST = "auth.iimc.kyoto-u.ac.jp"

    lateinit var webView: WebView
        private set

    private lateinit var appContext: Context
    private var initialized = false
    private val mainHandler = Handler(Looper.getMainLooper())

    /** WebView のローディング状態。 */
    private var isLoading = false

    /** ログイン状態。 */
    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn

    /** ログイン進行中の URL 監視用リスナー。 */
    private val loginNavigationListeners = mutableListOf<(String) -> Unit>()

    /** セッション切れコールバック。 */
    var onSessionExpired: (() -> Unit)? = null

    /** ポータル到達コールバック（WebView ログイン用自動遷移）。 */
    var onPortalReached: (() -> Unit)? = null

    fun init(context: Context) {
        if (initialized) return
        appContext = context.applicationContext
        ContentScriptInjector.init(appContext)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(
                0 != appContext.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE
            )
        }
        webView = WebView(appContext).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.setSupportMultipleWindows(true)
            settings.javaScriptCanOpenWindowsAutomatically = true
            addJavascriptInterface(KulmsStorageBridge, "Android")
            webViewClient = KulmsWebViewClient()
            webChromeClient = KulmsWebChromeClient()
            setDownloadListener { url, _, contentDisposition, mimetype, _ ->
                val cookie = CookieManager.getInstance().getCookie(url) ?: ""
                CoroutineScope(Dispatchers.IO).launch {
                    val file = downloadFileWithCookie(url, cookie, contentDisposition, mimetype)
                    if (file != null) {
                        withContext(Dispatchers.Main) {
                            openFileWithViewer(file, mimetype)
                        }
                    }
                }
            }
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        initialized = true
    }

    fun setLoggedIn(value: Boolean) {
        _isLoggedIn.value = value
    }

    /** LMS ポータルを表示する。 */
    fun loadPortal() {
        mainHandler.post {
            webView.loadUrl("$BASE_URL/portal")
        }
    }

    // ---- Login ----

    suspend fun loginWithCredentials(username: String, password: String): LoginResult {
        val result = withContext(Dispatchers.Main) {
            val deferred = CompletableDeferred<LoginResult>()
            var credentialsInjected = false

            val listener: (String) -> Unit = listener@{ url ->
                if (deferred.isCompleted) return@listener
                Log.d(TAG, "loginWithCredentials: navigated to $url")

                val isPortalPage = url.startsWith("$BASE_URL/portal")
                    && !url.contains("/login")
                    && !url.contains("/relogin")
                    && !url.contains("/logout")
                if (isPortalPage) {
                    Log.d(TAG, "loginWithCredentials: portal reached")
                    deferred.complete(LoginResult.Success)
                    return@listener
                }

                val twoFactorPaths = listOf(
                    "/authselect.php", "/u2flogin.cgi",
                    "/otplogin.cgi", "/motplogin.cgi"
                )
                if (url.contains(IIMC_HOST) && twoFactorPaths.any { url.contains(it) }) {
                    Log.d(TAG, "loginWithCredentials: 2FA required → WebView fallback")
                    deferred.complete(LoginResult.OtpRequired)
                    return@listener
                }

                if (url.contains(IIMC_HOST) && url.contains("/login.cgi")) {
                    if (!credentialsInjected) {
                        credentialsInjected = true
                        injectCredentials(username, password)
                    } else {
                        checkLoginCgiState { state ->
                            if (deferred.isCompleted) return@checkLoginCgiState
                            when (state) {
                                is CgiState.Otp -> {
                                    Log.d(TAG, "loginWithCredentials: OTP required")
                                    deferred.complete(LoginResult.OtpRequired)
                                }
                                is CgiState.Error -> {
                                    Log.d(TAG, "loginWithCredentials: failed - ${state.message}")
                                    deferred.complete(LoginResult.Failed(state.message))
                                }
                                is CgiState.Unknown -> {
                                    Log.d(TAG, "loginWithCredentials: login.cgi unknown state, waiting")
                                }
                            }
                        }
                    }
                }
            }

            loginNavigationListeners.add(listener)
            try {
                webView.loadUrl(LOGIN_PORTAL_URL)
                val r = withTimeoutOrNull(30_000L) { deferred.await() }
                    ?: LoginResult.Failed("ログイン処理がタイムアウトしました。ネットワーク状況を確認してください。")
                r
            } finally {
                loginNavigationListeners.remove(listener)
            }
        }

        if (result == LoginResult.Success) {
            waitForStableNavigation()
            Log.d(TAG, "loginWithCredentials: SUCCESS (stable)")
        }

        return result
    }

    private fun injectCredentials(username: String, password: String) {
        val u = username.replace("\\", "\\\\").replace("'", "\\'")
        val p = password.replace("\\", "\\\\").replace("'", "\\'")
        val js = """
            (function() {
                try {
                    var u = document.getElementById('username_input');
                    var p = document.getElementById('password_input');
                    var f = document.getElementById('login');
                    if (u && p && f) {
                        u.value = '$u';
                        p.value = '$p';
                        u.dispatchEvent(new Event('input', {bubbles: true}));
                        p.dispatchEvent(new Event('input', {bubbles: true}));
                        f.submit();
                    }
                } catch (e) {}
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private sealed class CgiState {
        object Unknown : CgiState()
        data class Error(val message: String) : CgiState()
        object Otp : CgiState()
    }

    private fun checkLoginCgiState(callback: (CgiState) -> Unit) {
        val js = """
            (function() {
                try {
                    var otpSend = document.getElementById('otp_send_button');
                    var dusername = document.getElementById('dusername_area');
                    var commentEl = document.getElementById('comment');
                    var otpVisible = false;
                    if (otpSend && otpSend.style.display !== 'none') otpVisible = true;
                    if (dusername && dusername.children.length > 0) otpVisible = true;
                    if (otpVisible) return JSON.stringify({type: 'otp'});
                    var msg = '';
                    if (commentEl) {
                        var t = (commentEl.innerText || commentEl.textContent || '').trim();
                        if (t && t.length > 1) msg = t;
                    }
                    if (msg) return JSON.stringify({type: 'error', message: msg});
                    return JSON.stringify({type: 'unknown'});
                } catch (e) {
                    return JSON.stringify({type: 'unknown'});
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(js) { raw ->
            val state = try {
                val unquoted = raw?.trim()?.removeSurrounding("\"")?.replace("\\\"", "\"") ?: ""
                when {
                    unquoted.contains("\"type\":\"otp\"") -> CgiState.Otp
                    unquoted.contains("\"type\":\"error\"") -> {
                        val msgRegex = "\"message\":\"([^\"]*)\"".toRegex()
                        val m = msgRegex.find(unquoted)?.groupValues?.getOrNull(1) ?: "ログインに失敗しました"
                        CgiState.Error(m)
                    }
                    else -> CgiState.Unknown
                }
            } catch (e: Exception) {
                CgiState.Unknown
            }
            callback(state)
        }
    }

    fun loadLoginPortal() {
        mainHandler.post {
            webView.loadUrl(LOGIN_PORTAL_URL)
        }
    }

    fun clearData() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        if (initialized) {
            mainHandler.post {
                webView.clearCache(true)
                webView.clearHistory()
            }
        }
        // 一時ダウンロードファイルをクリア
        appContext.cacheDir.listFiles()?.forEach { it.delete() }
    }

    // MARK: - File Download & Preview

    private fun downloadFileWithCookie(
        url: String,
        cookie: String,
        contentDisposition: String,
        mimetype: String
    ): File? {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.apply {
                requestMethod = "GET"
                if (cookie.isNotEmpty()) setRequestProperty("Cookie", cookie)
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android)")
                connectTimeout = 30_000
                readTimeout = 60_000
                instanceFollowRedirects = true
            }
            if (conn.responseCode !in 200..299) {
                conn.disconnect()
                return null
            }
            val filename = extractFilename(contentDisposition, url, mimetype)
            val tempFile = File(appContext.cacheDir, filename)
            conn.inputStream.use { input -> tempFile.outputStream().use { input.copyTo(it) } }
            conn.disconnect()
            tempFile
        } catch (e: Exception) {
            Log.e(TAG, "Download failed: ${e.message}")
            null
        }
    }

    private fun extractFilename(contentDisposition: String, url: String, mimetype: String): String {
        // RFC 5987: filename*=UTF-8''encoded-name
        Regex("""filename\*=UTF-8''([^;\s]+)""", RegexOption.IGNORE_CASE)
            .find(contentDisposition)?.groupValues?.get(1)?.let {
                return try { URLDecoder.decode(it, "UTF-8") } catch (e: Exception) { it }
            }
        // 通常形式: filename="name.ext"
        Regex("""filename="?([^";]+)"?""", RegexOption.IGNORE_CASE)
            .find(contentDisposition)?.groupValues?.get(1)?.let { return it.trim() }
        // URLから取得
        val urlFilename = url.substringAfterLast('/').substringBefore('?')
        if (urlFilename.isNotEmpty()) return urlFilename
        // MIMEから拡張子を推測
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimetype) ?: "bin"
        return "download_${System.currentTimeMillis()}.$ext"
    }

    private fun openFileWithViewer(file: File, mimetype: String) {
        val resolvedMime = mimetype.ifEmpty {
            val ext = file.extension.lowercase()
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        }
        val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, resolvedMime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "ファイルを開く").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(chooser)
    }

    private suspend fun waitForStableNavigation(maxSeconds: Double = 10.0) = withContext(Dispatchers.Main) {
        val stepMs = 100L
        val quietRequired = 5
        val maxSteps = (maxSeconds * 10).toInt()

        var quietCount = 0
        for (step in 0 until maxSteps) {
            delay(stepMs)
            if (isLoading) {
                quietCount = 0
            } else {
                quietCount++
                if (quietCount >= quietRequired) return@withContext
            }
        }
    }

    // ======== KulmsStorageBridge ========

    /**
     * @JavascriptInterface でストレージブリッジを実装。
     * JS から Android.kulmsStorageRequest(json) で呼び出される。
     * SharedPreferences に永続化し、evaluateJavascript でコールバック返却。
     */
    object KulmsStorageBridge {
        private const val PREFS_NAME = "kulms_extension_storage"
        private const val STORE_KEY = "kulms-extension-storage"

        @JavascriptInterface
        fun kulmsStorageRequest(json: String) {
            try {
                val msg = JSONObject(json)
                val action = msg.getString("action")
                val callbackId = msg.getString("callbackId")

                when (action) {
                    "get" -> handleGet(msg, callbackId)
                    "set" -> handleSet(msg, callbackId)
                    "remove" -> handleRemove(msg, callbackId)
                    "clear" -> handleClear(callbackId)
                    "nativeFetch" -> handleNativeFetch(msg, callbackId)
                    else -> sendCallback(callbackId, JSONObject())
                }
            } catch (e: Exception) {
                Log.e(TAG, "KulmsStorageBridge error: ${e.message}")
            }
        }

        private fun handleGet(msg: JSONObject, callbackId: String) {
            val store = loadStore()
            val result = JSONObject()

            if (msg.isNull("keys")) {
                // return all
                sendCallback(callbackId, store)
            } else {
                val keys = msg.getJSONArray("keys")
                for (i in 0 until keys.length()) {
                    val key = keys.getString(i)
                    if (store.has(key)) {
                        result.put(key, store.get(key))
                    }
                }
                sendCallback(callbackId, result)
            }
        }

        private fun handleSet(msg: JSONObject, callbackId: String) {
            val store = loadStore()
            val items = msg.getJSONObject("items")
            val iter = items.keys()
            while (iter.hasNext()) {
                val key = iter.next()
                store.put(key, items.get(key))
            }
            saveStore(store)
            sendCallback(callbackId, JSONObject())

            // 課題データ更新時に通知をスケジュール
            if (items.has("kulms-assignments") || items.has("kulms-checked-assignments")) {
                try {
                    val assignmentsData = store.optJSONObject("kulms-assignments")
                    if (assignmentsData != null) {
                        val assignments = assignmentsData.optJSONArray("assignments")
                        if (assignments != null) {
                            val checked = store.optJSONObject("kulms-checked-assignments") ?: JSONObject()
                            NotificationHelper.scheduleFromExtensionData(appContext, assignments, checked)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to schedule notifications: ${e.message}")
                }
            }
        }

        private fun handleRemove(msg: JSONObject, callbackId: String) {
            val store = loadStore()
            val keys = msg.getJSONArray("keys")
            for (i in 0 until keys.length()) {
                store.remove(keys.getString(i))
            }
            saveStore(store)
            sendCallback(callbackId, JSONObject())
        }

        private fun handleClear(callbackId: String) {
            saveStore(JSONObject())
            sendCallback(callbackId, JSONObject())
        }

        private fun handleNativeFetch(msg: JSONObject, callbackId: String) {
            val url = msg.optString("url", "")
            if (url.isEmpty()) {
                sendCallback(callbackId, JSONObject().put("error", "Invalid URL"))
                return
            }

            Thread {
                try {
                    val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 15000
                    connection.readTimeout = 15000

                    val statusCode = connection.responseCode
                    val bytes = connection.inputStream.readBytes()

                    // Detect charset from Content-Type header
                    val contentType = (connection.contentType ?: "").lowercase()
                    val charset = when {
                        contentType.contains("utf-8") -> Charsets.UTF_8
                        contentType.contains("euc-jp") || contentType.contains("euc_jp") ->
                            java.nio.charset.Charset.forName("EUC-JP")
                        else -> java.nio.charset.Charset.forName("Shift_JIS")
                    }

                    val text = try {
                        String(bytes, charset)
                    } catch (e: Exception) {
                        String(bytes, Charsets.UTF_8)
                    }

                    connection.disconnect()

                    val result = JSONObject()
                        .put("text", text)
                        .put("status", statusCode)
                    sendCallback(callbackId, result)
                } catch (e: Exception) {
                    sendCallback(callbackId, JSONObject().put("error", e.message ?: "Fetch failed"))
                }
            }.start()
        }

        private fun loadStore(): JSONObject {
            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = prefs.getString(STORE_KEY, null) ?: return JSONObject()
            return try {
                JSONObject(raw)
            } catch (e: Exception) {
                JSONObject()
            }
        }

        private fun saveStore(store: JSONObject) {
            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(STORE_KEY, store.toString()).apply()
        }

        private fun sendCallback(callbackId: String, data: JSONObject) {
            val escaped = data.toString()
                .replace("\\", "\\\\")
                .replace("'", "\\'")
            val js = "window.__kulmsStorageCallback('$callbackId', JSON.parse('$escaped'))"
            mainHandler.post {
                webView.evaluateJavascript(js, null)
            }
        }
    }

    // ======== ContentScriptInjector ========

    /**
     * 拡張機能の CSS / JS を assets から読み込み、onPageFinished() で
     * evaluateJavascript() により注入する。
     */
    object ContentScriptInjector {
        private val scriptCache = mutableMapOf<String, String>()
        private var shimJS = ""
        private var cssInjectionJS = ""
        private var appVersion = "1.0.0"

        // ロケール JSON はそのまま保持（JS オブジェクトリテラルとして直接埋め込む）
        private var jaMessagesJson = ""
        private var enMessagesJson = ""

        private val scriptNames = listOf(
            "src/settings.js", "src/assignments.js", "src/submit-detect.js",
            "src/tree-view.js", "src/course-name.js", "src/course-click.js",
            "src/tool-visibility.js",
            "kulms-textbook-handler.js", "src/textbooks.js",
            "src/sidebar-resize.js", "src/top-favbar.js"
        )

        fun init(context: Context) {
            appVersion = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
            } catch (e: Exception) {
                "1.0.0"
            }

            // Pre-load shim
            shimJS = loadAsset(context, "kulms-shim.js")

            // Pre-load CSS → wrap in <style> injection JS
            val css = loadAsset(context, "styles.css")
            if (css.isNotEmpty()) {
                val escapedCSS = css
                    .replace("\\", "\\\\")
                    .replace("`", "\\`")
                    .replace("\${", "\\\${")
                cssInjectionJS = "(function(){" +
                    "var s=document.createElement('style');" +
                    "s.textContent=`" + escapedCSS + "`;" +
                    "(document.head||document.documentElement).appendChild(s);" +
                    "})();"
            }

            // Pre-load locale JSON (raw — JSON is valid JS, no escaping needed)
            jaMessagesJson = loadAsset(context, "_locales/ja/messages.json")
            enMessagesJson = loadAsset(context, "_locales/en/messages.json")

            // Pre-load extension scripts
            for (name in scriptNames) {
                val content = loadAsset(context, name)
                if (content.isNotEmpty()) {
                    scriptCache[name] = content
                }
            }

            Log.d(TAG, "ContentScriptInjector: loaded ${scriptCache.size} scripts, " +
                "ja=${jaMessagesJson.length}chars, en=${enMessagesJson.length}chars")
        }

        /**
         * onPageFinished() で呼び出す。URL に応じてスクリプトを注入する。
         *
         * ロケールデータは JSON を JS オブジェクトリテラルとして直接埋め込む。
         * JSON は valid JavaScript なのでエスケープ不要。
         * - __kulmsResourceData: JSON.stringify() で文字列として格納（fetch override 用）
         * - __kulmsSetShimMessages(): パース済みオブジェクトを直接セット
         */
        fun injectAll(webView: WebView, url: String?) {
            if (url == null) return

            // シムは全ページで注入（SSO 等でもストレージブリッジが安全に動作）
            evaluateJS(webView, shimJS)

            // アプリバージョン・プラットフォーム埋め込み
            evaluateJS(webView, "window.__kulmsAppVersion = '" + appVersion + "'; window.__kulmsPlatform = 'android';")

            // LMS ホストのみ拡張スクリプトを注入
            if (url.startsWith(BASE_URL)) {
                // CSS
                if (cssInjectionJS.isNotEmpty()) {
                    evaluateJS(webView, cssInjectionJS)
                }

                // ロケールデータ + シム内部メッセージストア事前セット
                // JSON を直接 JS オブジェクトとして埋め込み、エスケープ問題を回避
                val ja = jaMessagesJson.ifEmpty { "{}" }
                val en = enMessagesJson.ifEmpty { "{}" }
                val localeJS = "(function(){" +
                    "var ja=" + ja + ";" +
                    "var en=" + en + ";" +
                    "window.__kulmsResourceData={};" +
                    "window.__kulmsResourceData['_locales/ja/messages.json']=JSON.stringify(ja);" +
                    "window.__kulmsResourceData['_locales/en/messages.json']=JSON.stringify(en);" +
                    "var l=(navigator.language||'ja').toLowerCase().indexOf('ja')===0?'ja':'en';" +
                    "window.__kulmsSetShimMessages(l==='ja'?ja:en);" +
                    "})();"
                evaluateJS(webView, localeJS)

                // 拡張スクリプト（manifest.json 順）
                for (name in scriptNames) {
                    scriptCache[name]?.let { evaluateJS(webView, it) }
                }

                Log.d(TAG, "ContentScriptInjector: injected all scripts for $url")
            }
        }

        private fun evaluateJS(webView: WebView, js: String) {
            if (js.isEmpty()) return
            webView.evaluateJavascript(js, null)
        }

        private fun loadAsset(context: Context, path: String): String {
            return try {
                context.assets.open(path).bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                Log.w(TAG, "ContentScriptInjector: asset not found: $path")
                ""
            }
        }
    }

    // ======== WebViewClient ========

    private class KulmsWebViewClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            if (url.startsWith("$BASE_URL/access/")) {
                val intent = Intent(appContext, FileViewerActivity::class.java).apply {
                    putExtra(FileViewerActivity.EXTRA_URL, url)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(intent)
                return true
            }
            return false
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            isLoading = true
        }

        @Deprecated("Deprecated in Java")
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
            if (url.startsWith("$BASE_URL/access/")) {
                val intent = Intent(appContext, FileViewerActivity::class.java).apply {
                    putExtra(FileViewerActivity.EXTRA_URL, url)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(intent)
                return true
            }
            return false
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            isLoading = false
            super.onPageFinished(view, url)

            // 拡張スクリプト注入
            if (view != null && url != null) {
                ContentScriptInjector.injectAll(view, url)
            }

            // ログイン中の URL 通知
            if (url != null) {
                val listeners = ArrayList(loginNavigationListeners)
                for (l in listeners) l(url)
            }

            // ポータル到達自動検知（credential login 中以外）
            if (loginNavigationListeners.isEmpty() && url != null) {
                val isPortalPage = url.startsWith("$BASE_URL/portal")
                    && !url.contains("/login")
                    && !url.contains("/relogin")
                    && !url.contains("/logout")
                if (isPortalPage) {
                    onPortalReached?.invoke()
                }
            }

            // IIMC リダイレクト検知（ログイン済みの場合のみ）
            if (_isLoggedIn.value && url != null && url.contains(IIMC_HOST)) {
                Log.d(TAG, "Session expired: redirected to IIMC ($url)")
                _isLoggedIn.value = false
                onSessionExpired?.invoke()
            }
        }
    }

    // ======== WebChromeClient ========

    /**
     * target="_blank" リンクを FileViewerActivity で開く。
     */
    private class KulmsWebChromeClient : WebChromeClient() {
        override fun onCreateWindow(
            view: WebView,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message?
        ): Boolean {
            // 一時 WebView で URL を捕捉し、FileViewerActivity に渡す
            val tempWebView = WebView(view.context)
            tempWebView.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val url = request.url.toString()
                    val intent = Intent(appContext, FileViewerActivity::class.java).apply {
                        putExtra(FileViewerActivity.EXTRA_URL, url)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    appContext.startActivity(intent)
                    tempWebView.destroy()
                    return true
                }

                @Deprecated("Deprecated in Java")
                override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                    val intent = Intent(appContext, FileViewerActivity::class.java).apply {
                        putExtra(FileViewerActivity.EXTRA_URL, url)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    appContext.startActivity(intent)
                    tempWebView.destroy()
                    return true
                }
            }
            val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
            transport.webView = tempWebView
            resultMsg.sendToTarget()
            return true
        }
    }
}
