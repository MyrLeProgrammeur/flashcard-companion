package com.matheo.flashcardcompanion

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

private const val BACKEND_URL = "http://127.0.0.1:8420/"
private const val HEALTH_URL = "http://127.0.0.1:8420/api/health"
private const val HEALTH_TIMEOUT_MS = 1500

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var fallbackScreen: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = WebViewClient()
        }

        fallbackScreen = buildFallbackScreen()

        setContentView(webView)
        checkHealthAndLoad()
    }

    private fun buildFallbackScreen(): LinearLayout {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }
        val message = TextView(this).apply {
            text = "Backend introuvable.\n\n" +
                "Ouvre Termux et lance :\n" +
                "~/flashcard-companion/backend/termux/start.sh\n\n" +
                "Puis appuie sur Réessayer."
            textSize = 16f
        }
        val retryButton = Button(this).apply {
            text = "Réessayer"
            setOnClickListener { checkHealthAndLoad() }
        }
        val launchButton = Button(this).apply {
            text = "Tenter le lancement auto (Termux:API)"
            setOnClickListener {
                tryRunCommand()
                checkHealthAndLoad()
            }
        }
        layout.addView(message)
        layout.addView(retryButton)
        layout.addView(launchButton)
        return layout
    }

    /** Best-effort convenience only — silently no-ops if Termux:API isn't
     * installed or hasn't granted RUN_COMMAND. Manual start.sh (or the
     * Retry button above) is the guaranteed path. */
    private fun tryRunCommand() {
        try {
            val intent = Intent().apply {
                setClassName("com.termux", "com.termux.app.RunCommandService")
                action = "com.termux.RUN_COMMAND"
                putExtra(
                    "com.termux.RUN_COMMAND_PATH",
                    "/data/data/com.termux/files/home/flashcard-companion/backend/termux/start.sh"
                )
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            }
            startService(intent)
        } catch (_: Exception) {
            // Termux:API absent/not granted — no-op, user falls back to manual start.
        }
    }

    private fun checkHealthAndLoad() {
        thread {
            val healthy = isBackendHealthy()
            runOnUiThread {
                if (healthy) {
                    setContentView(webView)
                    webView.loadUrl(BACKEND_URL)
                } else {
                    setContentView(fallbackScreen)
                }
            }
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
