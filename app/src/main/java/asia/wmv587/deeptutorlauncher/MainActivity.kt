package asia.wmv587.deeptutorlauncher

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
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
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PREFS = "deeptutor_prefs"
        private const val KEY_URL = "last_url"
        private const val KEY_IMMERSIVE = "immersive"
        private const val KEY_FAB_X = "fab_x"
        private const val KEY_FAB_Y = "fab_y"
        const val URL_LOCAL = "http://127.0.0.1:3782"
        const val URL_WIFI = "http://192.168.31.137:3782"
        const val URL_TAILSCALE = "http://100.64.233.119:3782"
        private const val MENU_HIDE_MS = 3500L
    }

    private lateinit var webView: WebView
    private lateinit var fabHost: FrameLayout
    private lateinit var menuPanel: LinearLayout
    private lateinit var btnFab: ImageButton
    private lateinit var root: FrameLayout
    private val hideHandler = Handler(Looper.getMainLooper())
    private var immersive = true
    private var menuExpanded = false
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    private val hideMenuRunnable = Runnable {
        if (immersive) {
            collapseMenu(animate = false)
            fabHost.animate().alpha(0f).setDuration(200).withEndAction {
                fabHost.visibility = View.GONE
            }.start()
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge before setContentView so first frame already fills the cutout.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableCutoutEdgeToEdge()
        setContentView(R.layout.activity_main)

        root = findViewById(R.id.root)
        webView = findViewById(R.id.webView)
        fabHost = findViewById(R.id.fabHost)
        menuPanel = findViewById(R.id.menuPanel)
        btnFab = findViewById(R.id.btnFab)

        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        immersive = prefs.getBoolean(KEY_IMMERSIVE, true)
        val startUrl = prefs.getString(KEY_URL, URL_TAILSCALE) ?: URL_TAILSCALE

        setupWebView()
        setupFab()
        restoreFabPosition()
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
                    "加载失败: ${description ?: "网络错误"}，点设置按钮换地址",
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
                fabHost.visibility = View.GONE
                collapseMenu(animate = false)
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
                showFabTemporarily()
                applyImmersive(immersive)
            }
        }

        webView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                if (menuExpanded) {
                    collapseMenu(animate = true)
                }
                showFabTemporarily()
            }
            false
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFab() {
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop

        findViewById<ImageButton>(R.id.btnRefresh).setOnClickListener {
            webView.reload()
            collapseMenu(animate = true)
            showFabTemporarily()
        }
        findViewById<ImageButton>(R.id.btnUrl).setOnClickListener {
            collapseMenu(animate = true)
            showUrlPicker()
            showFabTemporarily()
        }
        findViewById<ImageButton>(R.id.btnImmersive).setOnClickListener {
            immersive = !immersive
            getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_IMMERSIVE, immersive).apply()
            applyImmersive(immersive)
            collapseMenu(animate = true)
            if (!immersive) {
                fabHost.animate().cancel()
                fabHost.alpha = 1f
                fabHost.visibility = View.VISIBLE
                hideHandler.removeCallbacks(hideMenuRunnable)
            } else {
                scheduleHideMenu()
            }
            Toast.makeText(
                this,
                if (immersive) "已开全屏（铺满到顶，含刘海）" else "已关全屏（系统栏常显）",
                Toast.LENGTH_SHORT
            ).show()
        }

        // Drag on the settings button; short tap toggles the menu.
        btnFab.setOnTouchListener(object : View.OnTouchListener {
            private var downRawX = 0f
            private var downRawY = 0f
            private var startX = 0f
            private var startY = 0f
            private var dragging = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                val lp = fabHost.layoutParams as FrameLayout.LayoutParams
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downRawX = event.rawX
                        downRawY = event.rawY
                        startX = fabHost.x
                        startY = fabHost.y
                        dragging = false
                        if (lp.gravity != 0) {
                            fabHost.x = fabHost.x
                            fabHost.y = fabHost.y
                            lp.gravity = 0
                            lp.leftMargin = 0
                            lp.topMargin = 0
                            lp.rightMargin = 0
                            lp.bottomMargin = 0
                            fabHost.layoutParams = lp
                        }
                        showFabTemporarily()
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - downRawX
                        val dy = event.rawY - downRawY
                        if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                            dragging = true
                            collapseMenu(animate = false)
                        }
                        if (dragging) {
                            val parentW = root.width.toFloat()
                            val parentH = root.height.toFloat()
                            val w = fabHost.width.toFloat()
                            val h = fabHost.height.toFloat()
                            var nx = startX + dx
                            var ny = startY + dy
                            nx = nx.coerceIn(0f, (parentW - w).coerceAtLeast(0f))
                            ny = ny.coerceIn(0f, (parentH - h).coerceAtLeast(0f))
                            fabHost.x = nx
                            fabHost.y = ny
                            showFabTemporarily()
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (dragging) {
                            saveFabPosition(fabHost.x, fabHost.y)
                        } else {
                            if (menuExpanded) {
                                collapseMenu(animate = true)
                            } else {
                                expandMenu()
                            }
                        }
                        showFabTemporarily()
                        return true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        if (dragging) {
                            saveFabPosition(fabHost.x, fabHost.y)
                        }
                        showFabTemporarily()
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun expandMenu() {
        menuExpanded = true
        menuPanel.alpha = 0f
        menuPanel.visibility = View.VISIBLE
        menuPanel.animate().alpha(1f).setDuration(150).start()
        btnFab.contentDescription = getString(R.string.menu_fab) + "（已展开）"
    }

    private fun collapseMenu(animate: Boolean) {
        if (!menuExpanded && menuPanel.visibility != View.VISIBLE) return
        menuExpanded = false
        btnFab.contentDescription = getString(R.string.menu_fab)
        if (animate) {
            menuPanel.animate().alpha(0f).setDuration(120).withEndAction {
                menuPanel.visibility = View.GONE
                menuPanel.alpha = 1f
            }.start()
        } else {
            menuPanel.animate().cancel()
            menuPanel.visibility = View.GONE
            menuPanel.alpha = 1f
        }
    }

    private fun restoreFabPosition() {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_FAB_X) || !prefs.contains(KEY_FAB_Y)) return
        val x = prefs.getFloat(KEY_FAB_X, -1f)
        val y = prefs.getFloat(KEY_FAB_Y, -1f)
        if (x < 0f || y < 0f) return
        fabHost.post {
            val lp = fabHost.layoutParams as FrameLayout.LayoutParams
            lp.gravity = 0
            lp.leftMargin = 0
            lp.topMargin = 0
            lp.rightMargin = 0
            lp.bottomMargin = 0
            fabHost.layoutParams = lp
            val parentW = root.width.toFloat()
            val parentH = root.height.toFloat()
            val w = fabHost.width.toFloat()
            val h = fabHost.height.toFloat()
            fabHost.x = x.coerceIn(0f, (parentW - w).coerceAtLeast(0f))
            fabHost.y = y.coerceIn(0f, (parentH - h).coerceAtLeast(0f))
        }
    }

    private fun saveFabPosition(x: Float, y: Float) {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_FAB_X, x)
            .putFloat(KEY_FAB_Y, y)
            .apply()
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
        if (url.startsWith("http://") || url.startsWith("https://")) {
            getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_URL, url).apply()
        }
    }

    /** Draw into display cutout / notch so content can go to the absolute top. */
    private fun enableCutoutEdgeToEdge() {
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                } else {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            window.attributes = lp
        }
    }

    @Suppress("DEPRECATION")
    private fun applyImmersive(enable: Boolean) {
        // Always edge-to-edge so hiding bars actually expands content to the top,
        // instead of leaving a black status-bar-sized gap.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableCutoutEdgeToEdge()

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (enable) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            // Legacy flags help some OEM skins (vivo/OPPO/etc.) actually expand layout.
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            window.clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN)
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
        }
    }

    private fun showFabTemporarily() {
        fabHost.animate().cancel()
        fabHost.alpha = 1f
        fabHost.visibility = View.VISIBLE
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
            menuExpanded -> collapseMenu(animate = true)
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
