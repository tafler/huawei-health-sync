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
 * Google OAuth 2.0 authorisation flow inside a WebView.
 *
 * Opens the Google consent screen. When Google redirects to
 * https://localhost?code=..., intercepts the URL, extracts the
 * authorisation code and broadcasts it back to MainActivity.
 */
class GoogleAuthActivity : AppCompatActivity() {

    companion object {
        const val ACTION_GOOGLE_AUTH_CODE = "ru.tafinceva.health.ACTION_GOOGLE_AUTH_CODE"
        const val EXTRA_CODE              = "auth_code"
        const val EXTRA_ERROR             = "auth_error"

        private const val REDIRECT_URI = "https://localhost"

        fun buildAuthUrl(): String {
            val scope = listOf(
                "https://www.googleapis.com/auth/drive.file"
            ).joinToString(" ")

            return Uri.Builder()
                .scheme("https")
                .authority("accounts.google.com")
                .path("/o/oauth2/v2/auth")
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("client_id", GoogleDriveRepository.GOOGLE_CLIENT_ID)
                .appendQueryParameter("redirect_uri", REDIRECT_URI)
                .appendQueryParameter("scope", scope)
                .appendQueryParameter("access_type", "offline")
                .appendQueryParameter("prompt", "consent")
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
            settings.domStorageEnabled = true
            webViewClient = GoogleOAuthClient()
            loadUrl(buildAuthUrl())
        }
    }

    inner class GoogleOAuthClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            if (url.startsWith(REDIRECT_URI)) {
                handleRedirect(Uri.parse(url))
                return true
            }
            return false
        }

        @Deprecated("Deprecated in Java")
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
            if (url.startsWith(REDIRECT_URI)) {
                handleRedirect(Uri.parse(url))
                return true
            }
            return false
        }
    }

    private fun handleRedirect(uri: Uri) {
        val code  = uri.getQueryParameter("code")
        val error = uri.getQueryParameter("error")

        val intent = Intent(ACTION_GOOGLE_AUTH_CODE).apply {
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
