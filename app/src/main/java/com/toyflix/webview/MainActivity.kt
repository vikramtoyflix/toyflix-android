package com.toyflix.webview

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat

/**
 * Android WebView app matching iOS Capacitor behavior:
 * - No custom scroll (native scroll like WKWebView)
 * - Bounces disabled (overScrollMode = NEVER)
 * - Hardware-accelerated, cache enabled for images
 * - Same popup suppression and smooth-scroll injection as before
 */
class MainActivity : AppCompatActivity() {
    private lateinit var progressBar: ProgressBar

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            window.statusBarColor = Color.WHITE
        }
        WindowCompat.setDecorFitsSystemWindows(window, true)

        progressBar = ProgressBar(this).apply {
            visibility = View.VISIBLE
        }

        val webView = WebView(this).apply {
            // Clear cache on every launch so stale JS/keys never get served
            clearCache(true)
            clearHistory()
            // Match iOS: no bounce (scrollView.bounces = false)
            overScrollMode = View.OVER_SCROLL_NEVER
            // Smoother scrolling like WKWebView
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            isVerticalScrollBarEnabled = true

            webViewClient = object : WebViewClient() {
                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && request?.isForMainFrame == true) {
                        progressBar.visibility = View.GONE
                        Toast.makeText(this@MainActivity, "Loading failed. Check your connection.", Toast.LENGTH_LONG).show()
                    }
                }
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    progressBar.visibility = View.GONE
                    view?.evaluateJavascript(INJECTED_JS, null)
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    if (newProgress >= 100) progressBar.visibility = View.GONE
                }
                override fun onGeolocationPermissionsShowPrompt(
                    origin: String?,
                    callback: GeolocationPermissions.Callback?
                ) {
                    callback?.invoke(origin, true, false)
                }
            }
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                setGeolocationEnabled(true)
                cacheMode = WebSettings.LOAD_NO_CACHE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }
                builtInZoomControls = false
                displayZoomControls = false
                blockNetworkImage = false
                blockNetworkLoads = false
                // Use a standard Chrome user agent so Supabase/CDN don't block requests
                userAgentString = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 TOYFLIX-APP/1.0"
            }

            loadUrl("https://toyflix.in")
        }

        // Enable cookies + third-party cookies (required for Supabase auth & API)
        CookieManager.getInstance().setAcceptCookie(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        }

        val container = FrameLayout(this).apply {
            addView(webView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            addView(progressBar, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = android.view.Gravity.CENTER
            })
        }
        setContentView(container)
    }

    companion object {
        private const val INJECTED_JS = """
(function() {
try {
  document.documentElement.style.scrollBehavior = 'smooth';
  document.documentElement.style.overscrollBehavior = 'none';
  if (document.body) document.body.style.overscrollBehavior = 'none';
  sessionStorage.setItem('toyflix_app_download_popup_shown', '1');
  var hide = function() {
    try {
      document.querySelectorAll('[role=dialog]').forEach(function(d) {
        if (d.textContent.indexOf('Toyflix app') >= 0) {
          var root = d;
          while (root.parentElement && root.parentElement !== document.body) root = root.parentElement;
          root.style.setProperty('display', 'none', 'important');
        }
      });
    } catch (e) {}
  };
  hide();
  setTimeout(hide, 500);
  setTimeout(hide, 1500);
  if (document.body) {
    var o = new MutationObserver(hide);
    o.observe(document.body, { childList: true, subtree: true });
    setTimeout(function() { o.disconnect(); }, 5000);
  }
} catch (e) {}
})();
"""
    }
}
