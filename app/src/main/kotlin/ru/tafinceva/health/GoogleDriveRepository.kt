package ru.tafinceva.health

import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
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
 * Auth: refreshes a stored refresh token to obtain a short-lived access token.
 */
class GoogleDriveRepository(private val tokenManager: TokenManager) {

    companion object {
        // TODO: replace with your Google OAuth client secret from Google Cloud Console
        const val GOOGLE_CLIENT_ID     = "179608551541-scn6ls5cnsobm7l0gsaidsee18brinlb.apps.googleusercontent.com"
        const val GOOGLE_CLIENT_SECRET = "YOUR_GOOGLE_CLIENT_SECRET"
        const val DRIVE_FOLDER_ID      = "1oGxmtsrFKcG-2izXelGlQ5rZ_wNKyGgx"

        private const val TOKEN_URL      = "https://oauth2.googleapis.com/token"
        private const val FILES_URL      = "https://www.googleapis.com/drive/v3/files"
        private const val UPLOAD_URL     = "https://www.googleapis.com/upload/drive/v3/files"
        private const val JSON_MIME      = "application/json"
        private const val BOUNDARY       = "health_sync_boundary"
    }

    private val gson = Gson()

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    // ── Public API ────────────────────────────────────────────

    /** Serialize [data] and upload/update the file in Drive. */
    fun uploadHealthData(data: HealthData) {
        val accessToken = refreshAccessToken()
        val fileName    = "health-${data.date}.json"
        val content     = gson.toJson(data)

        val existingId = findFile(accessToken, fileName)
        if (existingId != null) {
            updateFile(accessToken, existingId, content)
        } else {
            createFile(accessToken, fileName, content)
        }
    }

    // ── Token ─────────────────────────────────────────────────

    private fun refreshAccessToken(): String {
        val refreshToken = tokenManager.googleRefreshToken
            ?: error("Google refresh token not set")

        val body = "grant_type=refresh_token" +
                "&client_id=${GOOGLE_CLIENT_ID}" +
                "&client_secret=${GOOGLE_CLIENT_SECRET}" +
                "&refresh_token=${refreshToken}"

        val request = Request.Builder()
            .url(TOKEN_URL)
            .post(body.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            .build()

        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string()
            ?: throw IOException("Empty response from Google token endpoint")

        if (!response.isSuccessful) {
            throw IOException("Token refresh failed (${response.code}): $responseBody")
        }

        return JSONObject(responseBody).getString("access_token")
    }

    // ── Drive helpers ─────────────────────────────────────────

    private fun findFile(accessToken: String, fileName: String): String? {
        val query = "name='$fileName' and '${DRIVE_FOLDER_ID}' in parents and trashed=false"
        val url   = "$FILES_URL?q=${query.urlEncode()}&fields=files(id,name)"

        val request = Request.Builder()
            .url(url)
            .get()
            .header("Authorization", "Bearer $accessToken")
            .build()

        val response = httpClient.newCall(request).execute()
        val body = response.body?.string() ?: return null
        if (!response.isSuccessful) return null

        val parsed = gson.fromJson(body, DriveFileListResponse::class.java)
        return parsed.files?.firstOrNull()?.id
    }

    private fun createFile(accessToken: String, fileName: String, content: String) {
        val metadata = """{"name":"$fileName","parents":["$DRIVE_FOLDER_ID"],"mimeType":"$JSON_MIME"}"""
        val requestBody = buildMultipartBody(metadata, content)

        val request = Request.Builder()
            .url("$UPLOAD_URL?uploadType=multipart")
            .post(requestBody)
            .header("Authorization", "Bearer $accessToken")
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("Drive create failed (${response.code}): ${response.body?.string()}")
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

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("Drive update failed (${response.code}): ${response.body?.string()}")
        }
    }

    /**
     * Builds a multipart/related body used by Drive's multipart upload:
     *   Part 1 — JSON metadata
     *   Part 2 — file content
     */
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
