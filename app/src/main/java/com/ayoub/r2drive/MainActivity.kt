package com.ayoub.r2drive

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewAssetLoader
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var rootLayout: FrameLayout
    private lateinit var webView: WebView
    private lateinit var fullscreenContainer: FrameLayout

    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    private var pendingApkFile: File? = null

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (fileUploadCallback == null) return@registerForActivityResult

        val uris: Array<Uri>? = when {
            result.resultCode != RESULT_OK || result.data == null -> null
            result.data?.clipData != null -> {
                val clipData = result.data!!.clipData!!
                Array(clipData.itemCount) { i -> clipData.getItemAt(i).uri }
            }
            result.data?.data != null -> arrayOf(result.data!!.data!!)
            else -> null
        }
        fileUploadCallback?.onReceiveValue(uris)
        fileUploadCallback = null
    }

    private val installPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (packageManager.canRequestPackageInstalls()) {
                pendingApkFile?.let { file ->
                    launchInstallIntent(file)
                }
            } else {
                Toast.makeText(this, "Permission to install update was denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Setup dark status bar and navigation bar
        setupWindowDecorations()

        // Create root layout programmatically for speed and zero XML inflate overhead
        rootLayout = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#050508"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        fullscreenContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        webView = WebView(this).apply {
            setBackgroundColor(Color.parseColor("#050508"))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        rootLayout.addView(webView)
        rootLayout.addView(fullscreenContainer)
        setContentView(rootLayout)

        setupWebView()
        setupBackNavigation()
    }

    private fun setupWindowDecorations() {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = Color.parseColor("#050508")
        window.navigationBarColor = Color.parseColor("#050508")

        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false
        insetsController.isAppearanceLightNavigationBars = false
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            // Mobile-first rendering: true 1:1 mobile scale, NO desktop zoom-out
            useWideViewPort = false
            loadWithOverviewMode = false
            displayZoomControls = false
            builtInZoomControls = false
            setSupportZoom(false)
            textZoom = 100
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString = "${userAgentString} R2DriveNative/1.0.7 Mobile"
        }

        // Enable Hardware Acceleration on the WebView layer
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        // Register In-App Updater JavaScript Interface
        webView.addJavascriptInterface(AndroidUpdaterBridge(), "AndroidUpdater")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val uri = request?.url ?: return super.shouldInterceptRequest(view, request)
                val intercepted = assetLoader.shouldInterceptRequest(uri)
                if (intercepted != null) return intercepted

                val host = uri.host ?: ""
                if (host.endsWith("r2.cloudflarestorage.com")) {
                    val method = request.method ?: "GET"

                    // Handle CORS Preflight (OPTIONS)
                    if (method.equals("OPTIONS", ignoreCase = true)) {
                        val corsHeaders = mutableMapOf(
                            "Access-Control-Allow-Origin" to "*",
                            "Access-Control-Allow-Methods" to "GET, POST, PUT, DELETE, HEAD, OPTIONS",
                            "Access-Control-Allow-Headers" to "*",
                            "Access-Control-Max-Age" to "86400"
                        )
                        return WebResourceResponse(
                            "text/plain",
                            "UTF-8",
                            204,
                            "No Content",
                            corsHeaders,
                            ByteArrayInputStream(ByteArray(0))
                        )
                    }

                    // For root requests (like listBuckets: https://<account>.r2.cloudflarestorage.com/)
                    // or if the request has no bucket path, proxy it with HttpURLConnection and inject CORS headers
                    if (uri.path.isNullOrEmpty() || uri.path == "/") {
                        try {
                            val conn = URL(uri.toString()).openConnection() as HttpURLConnection
                            conn.requestMethod = method
                            conn.instanceFollowRedirects = true
                            conn.connectTimeout = 15000
                            conn.readTimeout = 20000

                            request.requestHeaders?.forEach { (k, v) ->
                                if (!k.equals("Host", ignoreCase = true)) {
                                    conn.setRequestProperty(k, v)
                                }
                            }

                            val statusCode = conn.responseCode
                            val message = conn.responseMessage ?: "OK"
                            val respHeaders = mutableMapOf<String, String>()
                            conn.headerFields?.forEach { (k, vList) ->
                                if (k != null && vList.isNotEmpty()) {
                                    respHeaders[k] = vList.joinToString(", ")
                                }
                            }
                            respHeaders["Access-Control-Allow-Origin"] = "*"
                            respHeaders["Access-Control-Allow-Methods"] = "GET, POST, PUT, DELETE, HEAD, OPTIONS"
                            respHeaders["Access-Control-Allow-Headers"] = "*"

                            val stream = if (statusCode in 200..399) conn.inputStream else conn.errorStream
                            val mimeType = conn.contentType?.split(";")?.get(0)?.trim() ?: "application/xml"

                            return WebResourceResponse(mimeType, "UTF-8", statusCode, message, respHeaders, stream)
                        } catch (e: Exception) {
                            android.util.Log.e("R2Proxy", "Failed to proxy R2 request", e)
                        }
                    }
                }

                return super.shouldInterceptRequest(view, request)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                if (url.startsWith("https://appassets.androidplatform.net/") ||
                    url.startsWith("blob:") ||
                    url.startsWith("data:")
                ) {
                    return false
                }
                // Open external links safely
                return try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(intent)
                    true
                } catch (e: Exception) {
                    false
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            // Support input type="file" upload dialogs natively
            override fun onShowFileChooser(
                view: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileUploadCallback?.onReceiveValue(null)
                fileUploadCallback = filePathCallback

                val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }

                if (fileChooserParams?.mode == FileChooserParams.MODE_OPEN_MULTIPLE) {
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }

                try {
                    filePickerLauncher.launch(intent)
                } catch (e: Exception) {
                    fileUploadCallback?.onReceiveValue(null)
                    fileUploadCallback = null
                    return false
                }
                return true
            }

            // Support Fullscreen Video Player (Plyr)
            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (customView != null) {
                    onHideCustomView()
                    return
                }
                customView = view
                customViewCallback = callback
                fullscreenContainer.addView(view)
                fullscreenContainer.visibility = View.VISIBLE
                webView.visibility = View.GONE
            }

            override fun onHideCustomView() {
                if (customView == null) return
                fullscreenContainer.visibility = View.GONE
                fullscreenContainer.removeView(customView)
                customViewCallback?.onCustomViewHidden()
                customView = null
                customViewCallback = null
                webView.visibility = View.VISIBLE
            }

            // Permissions (e.g. video/audio capture if needed)
            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.grant(request.resources)
            }
        }

        // Native Download handling via Android DownloadManager
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            try {
                val filename = URLUtil.guessFileName(url, contentDisposition, mimetype)
                val request = DownloadManager.Request(Uri.parse(url)).apply {
                    setMimeType(mimetype)
                    addRequestHeader("User-Agent", userAgent)
                    setDescription("Downloading $filename")
                    setTitle(filename)
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
                }
                val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                manager.enqueue(request)
                Toast.makeText(this@MainActivity, "Downloading: $filename", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(intent)
                } catch (ex: Exception) {
                    Toast.makeText(this@MainActivity, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Load the local app asset with high-speed internal origin
        webView.loadUrl("https://appassets.androidplatform.net/assets/index.html")
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (customView != null) {
                    // Exit fullscreen video
                    webView.webChromeClient?.onHideCustomView()
                } else if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    // In-App APK Downloader and Installer
    inner class AndroidUpdaterBridge {
        @JavascriptInterface
        fun startDownloadAndInstall(apkUrl: String, versionName: String) {
            runOnUiThread {
                downloadAndInstallUpdate(apkUrl, versionName)
            }
        }
    }

    private fun downloadAndInstallUpdate(apkUrl: String, versionName: String) {
        Thread {
            try {
                var currentUrl = apkUrl
                var connection: HttpURLConnection
                var redirects = 0

                // Follow redirects (GitHub Releases redirect to AWS S3 CDN)
                while (true) {
                    val url = URL(currentUrl)
                    connection = url.openConnection() as HttpURLConnection
                    connection.instanceFollowRedirects = false
                    connection.connectTimeout = 15000
                    connection.readTimeout = 30000
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 R2DriveNativeUpdater")
                    connection.connect()

                    val status = connection.responseCode
                    if (status == HttpURLConnection.HTTP_MOVED_TEMP ||
                        status == HttpURLConnection.HTTP_MOVED_PERM ||
                        status == HttpURLConnection.HTTP_SEE_OTHER ||
                        status == 307 || status == 308
                    ) {
                        currentUrl = connection.getHeaderField("Location")
                        connection.disconnect()
                        redirects++
                        if (redirects > 8) throw Exception("Too many redirects")
                        continue
                    }
                    break
                }

                val fileLength = connection.contentLength
                val inputStream: InputStream = connection.inputStream

                val downloadDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: cacheDir
                val outputFile = File(downloadDir, "R2-Drive-v${versionName}.apk")
                if (outputFile.exists()) {
                    outputFile.delete()
                }

                val outputStream = FileOutputStream(outputFile)
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytesRead: Long = 0

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead

                    val percent = if (fileLength > 0) ((totalBytesRead * 100) / fileLength).toInt() else 0
                    val loadedStr = formatFileSize(totalBytesRead)
                    val totalStr = if (fileLength > 0) formatFileSize(fileLength.toLong()) else "..."

                    runOnUiThread {
                        webView.evaluateJavascript(
                            "if(window.onUpdateDownloadProgress) window.onUpdateDownloadProgress($percent, '$loadedStr', '$totalStr');",
                            null
                        )
                    }
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()
                connection.disconnect()

                runOnUiThread {
                    webView.evaluateJavascript(
                        "if(window.onUpdateDownloadComplete) window.onUpdateDownloadComplete();",
                        null
                    )
                    promptInstall(outputFile)
                }

            } catch (e: Exception) {
                runOnUiThread {
                    val msg = e.message?.replace("'", "\\'") ?: "Unknown error"
                    webView.evaluateJavascript(
                        "if(window.onUpdateDownloadError) window.onUpdateDownloadError('$msg');",
                        null
                    )
                    Toast.makeText(this@MainActivity, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun promptInstall(apkFile: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!packageManager.canRequestPackageInstalls()) {
                pendingApkFile = apkFile
                Toast.makeText(this, "Please allow R2 Drive to install updates", Toast.LENGTH_LONG).show()
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:$packageName")
                }
                installPermissionLauncher.launch(intent)
                return
            }
        }
        launchInstallIntent(apkFile)
    }

    private fun launchInstallIntent(apkFile: File) {
        try {
            val contentUri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                apkFile
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(installIntent)
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to launch installer: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun formatFileSize(bytes: Long): String {
        val mb = bytes.toDouble() / (1024 * 1024)
        return String.format("%.1f MB", mb)
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
