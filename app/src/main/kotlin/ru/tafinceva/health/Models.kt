package ru.tafinceva.health

import com.google.gson.annotations.SerializedName

// ──────────────────────────────────────────────
// Huawei OAuth
// ──────────────────────────────────────────────

data class HuaweiTokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String? = null,
    @SerializedName("expires_in") val expiresIn: Int = 3600,
    @SerializedName("token_type") val tokenType: String = "Bearer"
)

// ──────────────────────────────────────────────
// Huawei Health Kit — request / response
// ──────────────────────────────────────────────

data class HealthQueryRequest(
    @SerializedName("dataTypeName") val dataTypeName: String,
    @SerializedName("startTime") val startTime: Long,   // epoch ms
    @SerializedName("endTime") val endTime: Long        // epoch ms
)

data class HealthQueryResponse(
    @SerializedName("code") val code: Int = 0,
    @SerializedName("msg") val msg: String? = null,
    @SerializedName("sampleSets") val sampleSets: List<SampleSet>? = null
)

data class SampleSet(
    @SerializedName("dataTypeName") val dataTypeName: String = "",
    @SerializedName("samplePoints") val samplePoints: List<SamplePoint>? = null
)

data class SamplePoint(
    @SerializedName("startTime") val startTime: Long = 0L,
    @SerializedName("endTime") val endTime: Long = 0L,
    @SerializedName("fields") val fields: List<HealthField>? = null
)

data class HealthField(
    @SerializedName("name") val name: String = "",
    @SerializedName("value") val value: Double = 0.0   // Gson parses numbers as Double
)

// ──────────────────────────────────────────────
// Output health snapshot (written to Drive)
// ──────────────────────────────────────────────

data class HealthData(
    val date: String,
    val steps: Int,
    val heartRate: HeartRateData,
    val sleep: SleepData
)

data class HeartRateData(
    val avg: Int,
    val min: Int,
    val max: Int
)

data class SleepData(
    val totalHours: Double,
    val deepHours: Double,
    val remHours: Double
)

// ──────────────────────────────────────────────
// Google OAuth
// ──────────────────────────────────────────────

data class GoogleTokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("expires_in") val expiresIn: Int = 3600,
    @SerializedName("token_type") val tokenType: String = "Bearer"
)

// ──────────────────────────────────────────────
// Google Drive
// ──────────────────────────────────────────────

data class DriveFileListResponse(
    @SerializedName("files") val files: List<DriveFile>? = null
)

data class DriveFile(
    @SerializedName("id") val id: String = "",
    @SerializedName("name") val name: String = ""
)
