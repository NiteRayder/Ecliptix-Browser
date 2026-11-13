package com.meridian.browser

import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.mozilla.geckoview.*

class MainActivity : AppCompatActivity() {

    private class Tab(val session: GeckoSession, var url: String)

    private val engines = listOf(
        "DuckDuckGo" to "https://duckduckgo.com/?q=",
        "Brave" to "https://search.brave.com/search?q=",
        "Startpage" to "https://www.startpage.com/do/search?q=",
        "Google" to "https://www.google.com/search?q=")
    private val accents = listOf(
        "Silver" to 0xFFC0C0C0.toInt(), "White" to 0xFFFFFFFF.toInt(), "Grey" to 0xFF8A8A8A.toInt())

    private lateinit var prefs: SharedPreferences
    private lateinit var root: LinearLayout
    private lateinit var toolbar: LinearLayout
    private lateinit var gecko: GeckoView
    private lateinit var urlBar: EditText
    private lateinit var progress: ProgressBar
    private lateinit var tabBtn: Button
    private lateinit var backBtn: Button
    private lateinit var menuBtn: Button

    private val tabs = mutableListOf<Tab>()
    private var current: Tab? = null
    private var canGoBack = false

    companion object {
        const val START = "resource://android/assets/start.html"
        private var runtime: GeckoRuntime? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences("meridian", MODE_PRIVATE)
        root = findViewById(R.id.root); toolbar = findViewById(R.id.toolbar)
        gecko = findViewById(R.id.gecko); urlBar = findViewById(R.id.url)
        progress = findViewById(R.id.progress); tabBtn = findViewById(R.id.tabs)
        backBtn = findViewById(R.id.back); menuBtn = findViewById(R.id.menu)

        // ---- Privacy layer (Firefox / GeckoView) ----
        if (runtime == null) {
            val cb = ContentBlocking.Settings.Builder()
                .antiTracking(ContentBlocking.AntiTracking.STRICT)
                .enhancedTrackingProtectionLevel(ContentBlocking.EtpLevel.STRICT)
                .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS)
                .strictSocialTrackingProtection(true)
                .build()
            val rs = GeckoRuntimeSettings.Builder()
                .contentBlocking(cb)
                .globalPrivacyControlEnabled(true)
                .allowInsecureConnections(GeckoRuntimeSettings.HTTPS_ONLY)
                .consoleOutput(false)
                .build()
            runtime = GeckoRuntime.create(applicationContext, rs)
        }

        backBtn.setOnClickListener { goBackOrClose() }
        tabBtn.setOnClickListener { showTabs() }
        menuBtn.setOnClickListener { showMenu() }
        urlBar.setOnEditorActionListener { v, id, _ ->
            if (id == EditorInfo.IME_ACTION_GO) {
                current?.session?.loadUri(normalize(v.text.toString())); v.clearFocus(); gecko.requestFocus(); true
            } else false
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (canGoBack) current?.session?.goBack()
                else if (tabs.size > 1) closeTab(current!!)
                else finish()
            }
        })

        applyToolbarPosition(); applyAccent()
        newTab(intent?.data?.toString() ?: START)
    }

    // ---- Tabs ----
    private fun newTab(uri: String) {
        val s = GeckoSession(GeckoSessionSettings.Builder()
            .usePrivateMode(prefs.getBoolean("private", true)).build())
        val tab = Tab(s, uri)
        s.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(session: GeckoSession, url: String?,
                perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>, hasUserGesture: Boolean) {
                tab.url = url ?: ""
                if (tab === current) urlBar.setText(display(tab.url))
            }
            override fun onCanGoBack(session: GeckoSession, can: Boolean) { if (tab === current) canGoBack = can }
        }
        s.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onProgressChange(session: GeckoSession, p: Int) {
                if (tab === current) progress.progress = if (p >= 100) 0 else p
            }
        }
        s.open(runtime!!)
        tabs.add(tab); select(tab); s.loadUri(uri)
    }

    private fun select(tab: Tab) {
        current = tab; canGoBack = false
        gecko.setSession(tab.session)
        urlBar.setText(display(tab.url)); tabBtn.text = tabs.size.toString()
    }

    private fun closeTab(tab: Tab) {
        tabs.remove(tab); tab.session.close()
        if (tabs.isEmpty()) newTab(START) else if (current === tab) select(tabs.last()) else tabBtn.text = tabs.size.toString()
    }

    private fun showTabs() {
        val names = tabs.map { (if (it === current) "● " else "") + display(it.url).ifEmpty { "Meridian Home" } }
        AlertDialog.Builder(this).setTitle("Tabs (${tabs.size})")
            .setItems(names.toTypedArray()) { _, i -> select(tabs[i]) }
            .setPositiveButton("New tab") { _, _ -> newTab(START) }
            .setNeutralButton("Close tab") { _, _ -> current?.let { closeTab(it) } }
            .show()
    }

    // ---- Customization (Vivaldi / GX-style) ----
    private fun showMenu() {
        val bottom = prefs.getBoolean("bottom", false)
        val priv = prefs.getBoolean("private", true)
        val items = arrayOf("Forward", "Reload", "Home",
            "Toolbar: ${if (bottom) "Bottom" else "Top"}",
            "Accent: ${accents[prefs.getInt("accent", 0)].first}",
            "Search: ${engines[prefs.getInt("engine", 0)].first}",
            "Private tabs: ${if (priv) "On" else "Off"}",
            "Clean all data")
        AlertDialog.Builder(this).setItems(items) { _, i ->
            when (i) {
                0 -> current?.session?.goForward()
                1 -> current?.session?.reload()
                2 -> current?.session?.loadUri(START)
                3 -> { prefs.edit().putBoolean("bottom", !bottom).apply(); applyToolbarPosition() }
                4 -> { prefs.edit().putInt("accent", (prefs.getInt("accent", 0) + 1) % accents.size).apply(); applyAccent() }
                5 -> { prefs.edit().putInt("engine", (prefs.getInt("engine", 0) + 1) % engines.size).apply() }
                6 -> prefs.edit().putBoolean("private", !priv).apply()
                7 -> { runtime!!.storageController.clearData(StorageController.ClearFlags.ALL)
                       Toast.makeText(this, "Cookies, cache and site data cleared", Toast.LENGTH_SHORT).show() }
            }
        }.show()
    }

    private fun applyToolbarPosition() {
        root.removeView(toolbar)
        root.addView(toolbar, if (prefs.getBoolean("bottom", false)) root.childCount else 0)
    }

    private fun applyAccent() {
        val c = accents[prefs.getInt("accent", 0)].second
        progress.progressTintList = ColorStateList.valueOf(c)
        listOf(backBtn, tabBtn, menuBtn).forEach { it.setTextColor(c) }
    }

    // ---- Helpers ----
    private fun goBackOrClose() { if (canGoBack) current?.session?.goBack() else if (tabs.size > 1) closeTab(current!!) }

    private fun display(u: String) = if (u.startsWith("resource://")) "" else u

    private fun normalize(input: String): String {
        val t = input.trim()
        return when {
            t.startsWith("http://") || t.startsWith("https://") || t.startsWith("resource://") -> t
            !t.contains(' ') && t.contains('.') -> "https://$t"
            else -> engines[prefs.getInt("engine", 0)].second + Uri.encode(t)
        }
    }
}
