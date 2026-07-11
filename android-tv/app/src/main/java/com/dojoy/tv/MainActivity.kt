package com.dojoy.tv

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

private const val DOJOY_URL = "http://192.168.1.12:8090/"
private const val RETRY_DELAY_MS = 4000L
private const val EXIT_CONFIRM_WINDOW_MS = 2000L

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val retryHandler = Handler(Looper.getMainLooper())
    private val mainHandler = Handler(Looper.getMainLooper())
    private var loadFailed = false

    // 0 = profile-select screen, 1 = kid overview, 2 = kid edit-points screen
    @Volatile private var screenDepth = 0
    private var lastBackPressAt = 0L

    inner class JsBridge {
        @JavascriptInterface
        fun onRouteChanged(depth: Int) {
            mainHandler.post { screenDepth = depth }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)
        applyImmersiveMode()

        webView.setBackgroundColor(Color.parseColor("#111814"))
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.databaseEnabled = true
        webView.settings.loadWithOverviewMode = true
        webView.settings.useWideViewPort = true
        webView.settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.addJavascriptInterface(JsBridge(), "AndroidBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                loadFailed = false
            }

            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                loadFailed = true
                scheduleRetry()
            }
        }

        webView.loadUrl(DOJOY_URL)
        webView.requestFocus()

        Updater.checkForUpdate(this)
    }

    private fun scheduleRetry() {
        retryHandler.postDelayed({
            if (loadFailed) webView.loadUrl(DOJOY_URL)
        }, RETRY_DELAY_MS)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (screenDepth > 0) {
                webView.evaluateJavascript("window.dojoyGoBack && window.dojoyGoBack();", null)
                return true
            }
            val now = System.currentTimeMillis()
            if (now - lastBackPressAt < EXIT_CONFIRM_WINDOW_MS) {
                return super.onKeyDown(keyCode, event)
            }
            lastBackPressAt = now
            Toast.makeText(this, "Press back again to exit", Toast.LENGTH_SHORT).show()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersiveMode()
    }

    private fun applyImmersiveMode() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
    }
}
