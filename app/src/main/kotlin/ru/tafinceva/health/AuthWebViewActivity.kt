package ru.tafinceva.health

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import ru.tafinceva.health.databinding.ActivityAuthWebviewBinding

/**
 * Displays the Huawei OAuth 2.0 authorisation page in a WebView.
 *
 * When Huawei redirects to https://localhost?code=..., the WebViewClient
 * intercepts the URL, extracts the authorisation code and broadcasts it
 * back to MainActivity via an implicit Intent action.
 */
class AuthWebViewActivity : AppCompatActivity() {

    companion object {
        const val ACTION_AUTH_CODE = "ru.tafinceva.health.ACTION_HUAWEI_AUTH_CODE"
        const val EXTRA_CODE       = "auth_code"
        const val EXTRA_ERROR      = "auth_error"

        private const val REDIRECT_URI = "https://localhost"

        fun buildAuthUrl(): String {
            val scopes = listOf(
                "https://www.health.huawei.com/healthkit/healthSteps.read",
                "https://www.health.huawei.com/healthkit/heartrate.read",
                "https://www.health.huawei.com/healthkit/sleep.read"
            ).joinToString(" ")

            return Uri.Builder()
                .scheme("https")
                .authority("oauth-login.cloud.huawei.com")
                .path("/oauth2/v3/authorize")
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("client_id", HuaweiRepository.APP_ID)
                .appendQueryParameter("redirect_uri", REDIRECT_URI)
                .appendQueryParameter("scope", scopes)
                .appendQueryParameter("access_type", "offline")
                .build()
                .toString()
        }
    }

    private lateinit var binding: ActivityAuthWebviewBinding

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthWebviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        with(binding.webView) {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled  = true
            webViewClient = HuaweiOAuthClient()
            loadUrl(buildAuthUrl())
        }
    }

    // ── WebViewClient ─────────────────────────────────────────

    inner class HuaweiOAuthClient : WebViewClient() {

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            if (url.startsWith(REDIRECT_URI)) {
                handleRedirect(Uri.parse(url))
                return true
            }
            return false
        }

        /** Legacy callback for API < 24 (still required by some WebView builds). */
        @Deprecated("Deprecated in Java")
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
            if (url.startsWith(REDIRECT_URI)) {
                handleRedirect(Uri.parse(url))
                return true
            }
            return false
        }
    }

    // ── Redirect handling ─────────────────────────────────────

    private fun handleRedirect(uri: Uri) {
        val code  = uri.getQueryParameter("code")
        val error = uri.getQueryParameter("error")

        val intent = Intent(ACTION_AUTH_CODE).apply {
            `package` = packageName
            when {
                code  != null -> putExtra(EXTRA_CODE,  code)
                error != null -> putExtra(EXTRA_ERROR, error)
                else          -> putExtra(EXTRA_ERROR, "unknown_error")
            }
        }
        sendBroadcast(intent)
        finish()
    }
}
