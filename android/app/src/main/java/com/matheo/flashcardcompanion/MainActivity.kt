package com.matheo.flashcardcompanion

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

private const val BACKEND_URL = "http://127.0.0.1:8420/"
private const val HEALTH_URL = "http://127.0.0.1:8420/api/health"
private const val HEALTH_TIMEOUT_MS = 1500

// Fast, network-free launcher (venv + wakelock + uvicorn, with a no-double-start
// guard). Same script Termux:Boot runs at boot.
private const val START_SCRIPT =
    "/data/data/com.termux/files/home/.termux/boot/start-flashcard-backend.sh"

// After triggering the backend, poll health ~1s * N before giving up.
private const val AUTOSTART_ATTEMPTS = 20

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private var webViewShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Best-effort: make sure we hold Termux's RUN_COMMAND permission so the
        // auto-start can reach Termux. Silently no-ops if not grantable.
        try {
            requestPermissions(arrayOf("com.termux.permission.RUN_COMMAND"), 0)
        } catch (_: Exception) { /* not runtime-grantable — fallback covers it */ }

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = WebViewClient()
        }

        setContentView(loadingScreen("Connecting to backend…"))
        bootFlow()
    }

    /** Health check; if down, trigger the Termux backend once, poll until it's
     *  up, then load the UI. Falls back to a manual screen if it never comes up. */
    private fun bootFlow() {
        runOnUiThread { setContentView(loadingScreen("Connecting to backend…")) }
        Thread {
            if (isBackendHealthy()) { showWebView(); return@Thread }

            runOnUiThread { setContentView(loadingScreen("Starting backend…")) }
            tryRunCommand()

            var attempts = AUTOSTART_ATTEMPTS
            while (attempts-- > 0) {
                try { Thread.sleep(1000) } catch (_: InterruptedException) {}
                if (isBackendHealthy()) { showWebView(); return@Thread }
            }
            runOnUiThread { setContentView(fallbackScreen()) }
        }.start()
    }

    private fun showWebView() {
        runOnUiThread {
            setContentView(webView)
            webView.loadUrl(BACKEND_URL)
            webViewShown = true
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (webViewShown && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    private fun loadingScreen(msg: String): View {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }
        layout.addView(ProgressBar(this))
        layout.addView(TextView(this).apply {
            text = msg
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, 40, 0, 0)
        })
        return layout
    }

    private fun fallbackScreen(): View {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }
        layout.addView(TextView(this).apply {
            text = "The backend didn't start automatically.\n\n" +
                "Open Termux and run:\n" +
                "bash ~/flashcard-companion/backend/termux/start.sh\n\n" +
                "Then tap Retry."
            textSize = 16f
        })
        layout.addView(Button(this).apply {
            text = "Retry"
            setOnClickListener { bootFlow() }
        })
        return layout
    }

    /** Ask Termux to run the backend launcher. Needs allow-external-apps=true in
     *  termux.properties and the RUN_COMMAND permission (both set up on-device). */
    private fun tryRunCommand() {
        try {
            val intent = Intent().apply {
                setClassName("com.termux", "com.termux.app.RunCommandService")
                action = "com.termux.RUN_COMMAND"
                putExtra("com.termux.RUN_COMMAND_PATH", START_SCRIPT)
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            }
            startService(intent)
        } catch (_: Exception) {
            // Termux absent / not granted — the fallback screen handles it.
        }
    }

    private fun isBackendHealthy(): Boolean {
        return try {
            val conn = URL(HEALTH_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = HEALTH_TIMEOUT_MS
            conn.readTimeout = HEALTH_TIMEOUT_MS
            conn.requestMethod = "GET"
            val ok = conn.responseCode == 200
            conn.disconnect()
            ok
        } catch (_: IOException) {
            false
        }
    }
}
