package ru.tafinceva.health

import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import ru.tafinceva.health.BuildConfig
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import java.io.IOException

/**
 * Uploads a daily health JSON snapshot to a specific Google Drive folder.
 *
 * Uses the Google Drive REST v3 API directly (no SDK).
 * Auth: exchanges an OAuth authorisation code for tokens on first run,
 *       then refreshes the stored refresh token to obtain short-lived access tokens.
 */
class GoogleDriveRepository(private val tokenManager: TokenManager) {

    companion object {
        // Секреты читаются из local.properties через BuildConfig:
        val GOOGLE_CLIENT_ID     get() = BuildConfig.GOOGLE_CLIENT_ID
        val GOOGLE_CLIENT_SECRET get() = BuildConfig.GOOGLE_CLIENT_SECRET
        
        // DRIVE_FOLDER_ID теперь берется из tokenManager динамически.
        // Оставляем это здесь для обратной совместимости или как fallback, 
        // но основная логика теперь в методах.

        private const val TOKEN_URL      = "https://oauth2.googleapis.com/token"
        private const val FILES_URL      = "https://www.googleapis.com/drive/v3/files"
        private const val UPLOAD_URL     = "https://www.googleapis.com/upload/drive/v3/files"
        private const val JSON_MIME      = "application/json"
        private const val BOUNDARY       = "health_sync_boundary"
        private const val REDIRECT_URI   = "https://localhost"
    }

    private val gson = Gson()

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                    else HttpLoggingInterceptor.Level.NONE
        })
        .build()

    // ── OAuth: code exchange ──────────────────────────────────

    /**
     * Exchange a one-time authorisation [code] (from Google consent screen)
     * for access + refresh tokens. Saves the refresh token via [TokenManager].
     */
    fun exchangeCode(code: String) {
        val body = buildString {
            append("grant_type=authorization_code")
            append("&code=").append(code)
            append("&client_id=").append(GOOGLE_CLIENT_ID)
            append("&client_secret=").append(GOOGLE_CLIENT_SECRET)
            append("&redirect_uri=").append(REDIRECT_URI)
        }

        val request = Request.Builder()
            .url(TOKEN_URL)
            .post(body.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string()
                ?: throw IOException("Empty response from Google token endpoint")
            if (!response.isSuccessful) {
                throw IOException("Google code exchange failed (${response.code}): $responseBody")
            }
            val json = JSONObject(responseBody)
            val refreshToken = json.optString("refresh_token")
                .takeIf { it.isNotEmpty() }
                ?: throw IOException("Google did not return a refresh_token (prompt=consent required)")
            tokenManager.googleRefreshToken = refreshToken
        }
    }

    // ── Public API ────────────────────────────────────────────

    /** Serialize [data] and upload/update the file in Drive. */
    fun uploadHealthData(data: HealthData) {
        val folderId = tokenManager.googleDriveFolderId
            ?: error("Google Drive Folder ID not set")
            
        val accessToken = refreshAccessToken()
        val fileName    = "health-${data.date}.json"
        val content     = gson.toJson(data)

        val existingId = findFile(accessToken, fileName, folderId)
        if (existingId != null) {
            updateFile(accessToken, existingId, content)
        } else {
            createFile(accessToken, fileName, content, folderId)
        }
    }

    // ── Token ─────────────────────────────────────────────────

    private fun refreshAccessToken(): String {
        val refreshToken = tokenManager.googleRefreshToken
            ?: error("Google refresh token not set — please authorise via the app")

        val body = "grant_type=refresh_token" +
                "&client_id=${GOOGLE_CLIENT_ID}" +
                "&client_secret=${GOOGLE_CLIENT_SECRET}" +
                "&refresh_token=${refreshToken}"

        val request = Request.Builder()
            .url(TOKEN_URL)
            .post(body.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string()
                ?: throw IOException("Empty response from Google token endpoint")
            if (!response.isSuccessful) {
                throw IOException("Token refresh failed (${response.code}): $responseBody")
            }
            return JSONObject(responseBody).getString("access_token")
        }
    }

    // ── Drive helpers ─────────────────────────────────────────

    private fun findFile(accessToken: String, fileName: String, folderId: String): String? {
        val query = "name='$fileName' and '${folderId}' in parents and trashed=false"
        val url   = "$FILES_URL?q=${query.urlEncode()}&fields=files(id,name)"

        val request = Request.Builder()
            .url(url)
            .get()
            .header("Authorization", "Bearer $accessToken")
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: return null
            if (!response.isSuccessful) return null
            val parsed = gson.fromJson(body, DriveFileListResponse::class.java)
            return parsed.files?.firstOrNull()?.id
        }
    }

    private fun createFile(accessToken: String, fileName: String, content: String, folderId: String) {
        val metadata = """{"name":"$fileName","parents":["$folderId"],"mimeType":"$JSON_MIME"}"""
        val requestBody = buildMultipartBody(metadata, content)

        val request = Request.Builder()
            .url("$UPLOAD_URL?uploadType=multipart")
            .post(requestBody)
            .header("Authorization", "Bearer $accessToken")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Drive create failed (${response.code}): ${response.body?.string()}")
            }
        }
    }

    private fun updateFile(accessToken: String, fileId: String, content: String) {
        val metadata = """{"mimeType":"$JSON_MIME"}"""
        val requestBody = buildMultipartBody(metadata, content)

        val request = Request.Builder()
            .url("$UPLOAD_URL/$fileId?uploadType=multipart")
            .patch(requestBody)
            .header("Authorization", "Bearer $accessToken")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Drive update failed (${response.code}): ${response.body?.string()}")
            }
        }
    }

    private fun buildMultipartBody(metadata: String, content: String): okhttp3.RequestBody {
        val rawBody = buildString {
            append("--$BOUNDARY\r\n")
            append("Content-Type: $JSON_MIME; charset=UTF-8\r\n\r\n")
            append(metadata)
            append("\r\n--$BOUNDARY\r\n")
            append("Content-Type: $JSON_MIME\r\n\r\n")
            append(content)
            append("\r\n--$BOUNDARY--")
        }
        return rawBody.toRequestBody("multipart/related; boundary=$BOUNDARY".toMediaType())
    }

    private fun String.urlEncode(): String =
        java.net.URLEncoder.encode(this, "UTF-8")
}
