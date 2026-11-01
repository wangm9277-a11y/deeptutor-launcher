package asia.wmv587.deeptutorlauncher

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PREFS = "deeptutor_prefs"
        private const val KEY_URL = "last_url"
        private const val KEY_IMMERSIVE = "immersive"
        const val URL_LOCAL = "http://127.0.0.1:3782"
        const val URL_WIFI = "http://192.168.31.137:3782"
        const val URL_TAILSCALE = "http://100.64.233.119:3782"
        private const val MENU_HIDE_MS = 3500L
    }

    private lateinit var webView: WebView
    private lateinit var floatingMenu: LinearLayout
    private lateinit var root: FrameLayout
    private val hideHandler = Handler(Looper.getMainLooper())
    private var immersive = true
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    private val hideMenuRunnable = Runnable {
        if (immersive) {
            floatingMenu.animate().alpha(0f).setDuration(200).withEndAction {
                floatingMenu.visibility = View.GONE
            }.start()
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        root = findViewById(R.id.root)
        webView = findViewById(R.id.webView)
        floatingMenu = findViewById(R.id.floatingMenu)

        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        immersive = prefs.getBoolean(KEY_IMMERSIVE, true)
        val startUrl = prefs.getString(KEY_URL, URL_TAILSCALE) ?: URL_TAILSCALE

        setupWebView()
        setupMenu()
        applyImmersive(immersive)

        webView.loadUrl(startUrl)
        scheduleHideMenu()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val s = webView.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.loadWithOverviewMode = true
        s.useWideViewPort = true
        s.builtInZoomControls = true
        s.displayZoomControls = false
        s.setSupportZoom(true)
        s.mediaPlaybackRequiresUserGesture = false
        s.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        s.cacheMode = WebSettings.LOAD_DEFAULT
        s.allowFileAccess = true
        s.allowContentAccess = true

        WebView.setWebContentsDebuggingEnabled(true)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                url?.let { saveUrl(it) }
            }

            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                Toast.makeText(
                    this@MainActivity,
                    "加载失败: ${description ?: "网络错误"}，点右上角换地址",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback
                webView.visibility = View.GONE
                floatingMenu.visibility = View.GONE
                root.addView(
                    view,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )
                applyImmersive(true)
            }

            override fun onHideCustomView() {
                customView?.let { root.removeView(it) }
                customView = null
                customViewCallback?.onCustomViewHidden()
                customViewCallback = null
                webView.visibility = View.VISIBLE
                showMenuTemporarily()
                applyImmersive(immersive)
            }
        }

        webView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                showMenuTemporarily()
            }
            false
        }
    }

    private fun setupMenu() {
        findViewById<ImageButton>(R.id.btnRefresh).setOnClickListener {
            webView.reload()
            showMenuTemporarily()
        }
        findViewById<ImageButton>(R.id.btnUrl).setOnClickListener {
            showUrlPicker()
            showMenuTemporarily()
        }
        findViewById<ImageButton>(R.id.btnImmersive).setOnClickListener {
            immersive = !immersive
            getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_IMMERSIVE, immersive).apply()
            applyImmersive(immersive)
            if (!immersive) {
                floatingMenu.animate().cancel()
                floatingMenu.alpha = 1f
                floatingMenu.visibility = View.VISIBLE
                hideHandler.removeCallbacks(hideMenuRunnable)
            } else {
                scheduleHideMenu()
            }
            Toast.makeText(
                this,
                if (immersive) "已开全屏（系统栏会藏）" else "已关全屏（系统栏常显）",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun showUrlPicker() {
        val labels = arrayOf(
            getString(R.string.url_tailscale) + "\n$URL_TAILSCALE",
            getString(R.string.url_wifi) + "\n$URL_WIFI",
            getString(R.string.url_local) + "\n$URL_LOCAL",
            getString(R.string.url_custom)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.menu_url)
            .setItems(labels) { _, which ->
                when (which) {
                    0 -> loadAndSave(URL_TAILSCALE)
                    1 -> loadAndSave(URL_WIFI)
                    2 -> loadAndSave(URL_LOCAL)
                    3 -> showCustomUrlDialog()
                }
            }
            .show()
    }

    private fun showCustomUrlDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.custom_hint)
            setText(webView.url ?: URL_TAILSCALE)
            setSelection(text.length)
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.custom_title)
            .setView(input)
            .setPositiveButton(R.string.ok) { _, _ ->
                var u = input.text.toString().trim()
                if (u.isNotEmpty()) {
                    if (!u.startsWith("http://") && !u.startsWith("https://")) {
                        u = "http://$u"
                    }
                    loadAndSave(u)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun loadAndSave(url: String) {
        saveUrl(url)
        webView.loadUrl(url)
        Toast.makeText(this, "打开: $url", Toast.LENGTH_SHORT).show()
    }

    private fun saveUrl(url: String) {
        // Don't persist chrome/error pages
        if (url.startsWith("http://") || url.startsWith("https://")) {
            getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_URL, url).apply()
        }
    }

    private fun applyImmersive(enable: Boolean) {
        WindowCompat.setDecorFitsSystemWindows(window, !enable)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (enable) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun showMenuTemporarily() {
        floatingMenu.animate().cancel()
        floatingMenu.alpha = 1f
        floatingMenu.visibility = View.VISIBLE
        scheduleHideMenu()
    }

    private fun scheduleHideMenu() {
        hideHandler.removeCallbacks(hideMenuRunnable)
        if (immersive) {
            hideHandler.postDelayed(hideMenuRunnable, MENU_HIDE_MS)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            customView != null -> {
                (webView.webChromeClient as? WebChromeClient)?.onHideCustomView()
            }
            webView.canGoBack() -> webView.goBack()
            else -> super.onBackPressed()
        }
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        applyImmersive(immersive)
    }

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        hideHandler.removeCallbacks(hideMenuRunnable)
        webView.destroy()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && immersive) {
            applyImmersive(true)
        }
    }
}
