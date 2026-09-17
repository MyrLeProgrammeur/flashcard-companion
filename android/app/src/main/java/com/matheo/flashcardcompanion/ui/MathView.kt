package com.matheo.flashcardcompanion.ui

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject

/**
 * Renders card text, which is full of LaTeX, using the KaTeX bundled in
 * `assets/katex`. Nothing is fetched: the CSS, the JS and the maths fonts are
 * all in the APK, so a card renders identically with the radio off.
 *
 * Compose has no maths typesetter and no HTML renderer, and the card bodies
 * carry both, so this is a deliberate island of WebView inside an otherwise
 * native UI rather than a port of the old web app.
 *
 * The page is loaded once and then fed new card text over `setContent(...)`.
 * Reloading the document per card made every flip flash white.
 */
private fun template(
    inkHex: String,
    mutedHex: String,
    fontSizePx: Int,
    align: String,
    serif: Boolean,
): String = """
<!DOCTYPE html>
<html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
<link rel="stylesheet" href="katex/katex.min.css">
<script src="katex/katex.min.js"></script>
<script src="katex/contrib/auto-render.min.js"></script>
<style>
  html, body {
    margin: 0; padding: 0; background: transparent;
    color: $inkHex;
    font-family: ${if (serif) "Georgia, 'Times New Roman', serif" else "system-ui, sans-serif"};
    font-size: ${fontSizePx}px;
    line-height: 1.45;
    -webkit-text-size-adjust: none;
  }
  body { text-align: $align; overflow-wrap: anywhere; word-break: normal; }
  #content { padding: 0; }
  code { font-family: ui-monospace, monospace; font-size: .92em; }
  p { margin: 0 0 .6em; }
  p:last-child { margin-bottom: 0; }
  ul, ol { margin: .4em 0; padding-left: 1.3em; text-align: left; }
  em { font-style: italic; }
  /* Wide display equations are scaled to fit rather than clipped; this keeps
     them bounded even before the fitting pass runs. */
  .katex-display { overflow: hidden; width: 100%; max-width: 100%; margin: .5em 0; }
  .katex-display > .katex { white-space: nowrap; }
  .muted { color: $mutedHex; }
</style></head>
<body><div id="content"></div>
<script>
  var DELIMS = [
    { left: "$$", right: "$$", display: true },
    { left: "\\[", right: "\\]", display: true },
    { left: "\\(", right: "\\)", display: false },
    { left: "$", right: "$", display: false }
  ];
  function fitDisplayMath() {
    document.querySelectorAll(".katex-display").forEach(function (disp) {
      var k = disp.querySelector(".katex");
      if (!k) return;
      k.style.transform = "";
      k.style.transformOrigin = "left top";
      disp.style.height = "";
      var natural = k.scrollWidth;
      if (!natural) return;
      var avail = document.body.clientWidth;
      if (!avail || natural <= avail) return;
      var scale = (avail - 2) / natural;
      k.style.display = "inline-block";
      k.style.transform = "scale(" + scale + ")";
      disp.style.height = k.getBoundingClientRect().height + "px";
    });
  }
  function setContent(html) {
    var el = document.getElementById("content");
    el.innerHTML = html;
    try {
      renderMathInElement(el, {
        delimiters: DELIMS,
        throwOnError: false,
        ignoredTags: ["script", "noscript", "style", "textarea", "pre", "code", "option"]
      });
    } catch (e) { /* leave the raw text rather than blanking the card */ }
    fitDisplayMath();
    requestAnimationFrame(fitDisplayMath);
    if (document.fonts && document.fonts.ready) {
      document.fonts.ready.then(fitDisplayMath).catch(function () {});
    }
    window.scrollTo(0, 0);
  }

  /* The WebView consumes touches, so a tap on the card would never reach the
     Compose click handler that flips it. Taps are detected here and forwarded;
     anything that moves or lingers is left alone so the page can still scroll
     and text can still be selected. */
  (function () {
    var x = 0, y = 0, t = 0;
    document.addEventListener("touchstart", function (e) {
      var p = e.changedTouches[0];
      x = p.clientX; y = p.clientY; t = Date.now();
    }, { passive: true });
    document.addEventListener("touchend", function (e) {
      var p = e.changedTouches[0];
      var moved = Math.abs(p.clientX - x) + Math.abs(p.clientY - y);
      if (moved < 12 && Date.now() - t < 500 && window.AndroidTap) {
        AndroidTap.onTap();
      }
    }, { passive: true });
  })();
</script></body></html>
"""

/** Per-WebView bookkeeping: which document is loaded and which text is shown. */
private class MathViewState {
    var page: String? = null
    var pending: String = ""
    var rendered: String? = null
    var loaded: Boolean = false
    /** Re-read on every tap so the JS bridge always calls the current handler. */
    var onTap: (() -> Unit)? = null
}

private fun Color.toCssHex(): String = String.format("#%06X", 0xFFFFFF and toArgb())

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MathText(
    html: String,
    modifier: Modifier = Modifier,
    color: Color = LocalFcColors.current.ink,
    muted: Color = LocalFcColors.current.muted,
    fontSizeSp: Int = 17,
    centered: Boolean = false,
    serif: Boolean = true,
    onTap: (() -> Unit)? = null,
) {
    val inkHex = color.toCssHex()
    val mutedHex = muted.toCssHex()
    val align = if (centered) "center" else "left"
    // Re-created only when the styling changes, never per card.
    val page = remember(inkHex, mutedHex, fontSizeSp, align, serif) {
        template(inkHex, mutedHex, fontSizeSp, align, serif)
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                val state = MathViewState()
                tag = state
                addJavascriptInterface(object {
                    @android.webkit.JavascriptInterface
                    fun onTap() {
                        post { state.onTap?.invoke() }
                    }
                }, "AndroidTap")
                setBackgroundColor(AndroidColor.TRANSPARENT)
                isVerticalScrollBarEnabled = true
                isHorizontalScrollBarEnabled = false
                overScrollMode = WebView.OVER_SCROLL_NEVER
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                // Everything it loads is in the APK; no network, ever.
                settings.blockNetworkLoads = true
                settings.textZoom = 100
            }
        },
        update = { web ->
            val state = web.tag as? MathViewState ?: MathViewState().also { web.tag = it }
            state.onTap = onTap
            if (state.page != page) {
                // Styling changed (or first bind): reload the document, then push
                // the text once it is ready.
                state.page = page
                state.rendered = null
                state.loaded = false
                web.webViewClient = object : android.webkit.WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        state.loaded = true
                        state.rendered = state.pending
                        view.evaluateJavascript("setContent(${JSONObject.quote(state.pending)})", null)
                    }
                }
                state.pending = html
                web.loadDataWithBaseURL("file:///android_asset/", page, "text/html", "utf-8", null)
            } else {
                state.pending = html
                if (state.loaded && state.rendered != html) {
                    state.rendered = html
                    web.evaluateJavascript("setContent(${JSONObject.quote(html)})", null)
                }
            }
        },
    )
}
