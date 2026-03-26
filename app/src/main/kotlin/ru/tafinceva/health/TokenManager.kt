package ru.tafinceva.health

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Secure token storage backed by EncryptedSharedPreferences (AES256).
 */
class TokenManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "health_tokens",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // ── Huawei ──────────────────────────────────────────────

    var huaweiAccessToken: String?
        get() = prefs.getString(KEY_HUAWEI_ACCESS, null)
        set(v) = prefs.edit().putString(KEY_HUAWEI_ACCESS, v).apply()

    var huaweiRefreshToken: String?
        get() = prefs.getString(KEY_HUAWEI_REFRESH, null)
        set(v) = prefs.edit().putString(KEY_HUAWEI_REFRESH, v).apply()

    var huaweiTokenExpiry: Long
        get() = prefs.getLong(KEY_HUAWEI_EXPIRY, 0L)
        set(v) = prefs.edit().putLong(KEY_HUAWEI_EXPIRY, v).apply()

    fun isHuaweiTokenValid(): Boolean =
        !huaweiAccessToken.isNullOrEmpty() &&
                System.currentTimeMillis() < huaweiTokenExpiry - 60_000L

    fun saveHuaweiTokens(response: HuaweiTokenResponse) {
        huaweiAccessToken = response.accessToken
        response.refreshToken?.let { huaweiRefreshToken = it }
        huaweiTokenExpiry = System.currentTimeMillis() + response.expiresIn * 1_000L
    }

    fun clearHuaweiTokens() {
        prefs.edit()
            .remove(KEY_HUAWEI_ACCESS)
            .remove(KEY_HUAWEI_REFRESH)
            .remove(KEY_HUAWEI_EXPIRY)
            .apply()
    }

    // ── Google ───────────────────────────────────────────────

    var googleRefreshToken: String?
        get() = prefs.getString(KEY_GOOGLE_REFRESH, null)
        set(v) = prefs.edit().putString(KEY_GOOGLE_REFRESH, v).apply()

    var lastSyncTime: Long
        get() = prefs.getLong(KEY_LAST_SYNC, 0L)
        set(v) = prefs.edit().putLong(KEY_LAST_SYNC, v).apply()

    val isConfigured: Boolean
        get() = !huaweiRefreshToken.isNullOrEmpty() && !googleRefreshToken.isNullOrEmpty()

    companion object {
        private const val KEY_HUAWEI_ACCESS  = "huawei_access_token"
        private const val KEY_HUAWEI_REFRESH = "huawei_refresh_token"
        private const val KEY_HUAWEI_EXPIRY  = "huawei_token_expiry"
        private const val KEY_GOOGLE_REFRESH = "google_refresh_token"
        private const val KEY_LAST_SYNC      = "last_sync_time"
    }
}
