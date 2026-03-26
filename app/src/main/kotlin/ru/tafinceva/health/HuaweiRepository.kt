package ru.tafinceva.health

import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.roundToInt

/**
 * Fetches and parses Huawei Health Kit data for a given date.
 *
 * Handles token refresh transparently before every request.
 */
class HuaweiRepository(private val tokenManager: TokenManager) {

    companion object {
        const val APP_ID     = "6917601175022051786"
        // TODO: replace with your actual Huawei App Secret from AppGallery Connect
        const val APP_SECRET = "YOUR_HUAWEI_APP_SECRET"
        const val REDIRECT_URI = "https://localhost"

        private const val TYPE_STEPS      = "com.huawei.continuous.steps.total"
        private const val TYPE_HEART_RATE = "com.huawei.continuous.heart.rate.statistics"
        private const val TYPE_SLEEP      = "com.huawei.sleep.record"
    }

    // ── Token management ─────────────────────────────────────

    /** Exchange OAuth authorization code → store access + refresh tokens. */
    suspend fun exchangeCode(code: String) {
        val response = HuaweiApiFactory.authApi.exchangeCode(
            clientId     = APP_ID,
            clientSecret = APP_SECRET,
            code         = code,
            redirectUri  = REDIRECT_URI
        )
        tokenManager.saveHuaweiTokens(response)
    }

    /** Ensure a valid access token exists, refreshing if necessary. */
    suspend fun ensureValidToken(): String {
        if (tokenManager.isHuaweiTokenValid()) {
            return tokenManager.huaweiAccessToken!!
        }
        val refreshToken = tokenManager.huaweiRefreshToken
            ?: error("Huawei refresh token not set — please authorise first")
        val response = HuaweiApiFactory.authApi.refreshToken(
            clientId     = APP_ID,
            clientSecret = APP_SECRET,
            refreshToken = refreshToken
        )
        tokenManager.saveHuaweiTokens(response)
        return tokenManager.huaweiAccessToken!!
    }

    // ── Health data queries ──────────────────────────────────

    /** Fetch a full day's health snapshot for [date] (defaults to today). */
    suspend fun fetchHealthData(date: LocalDate = LocalDate.now()): HealthData {
        val token = ensureValidToken()
        val bearer = "Bearer $token"

        val startMs = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val endMs   = date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

        val steps     = fetchSteps(bearer, startMs, endMs)
        val heartRate = fetchHeartRate(bearer, startMs, endMs)
        val sleep     = fetchSleep(bearer, startMs, endMs)

        return HealthData(
            date      = date.toString(),
            steps     = steps,
            heartRate = heartRate,
            sleep     = sleep
        )
    }

    // ── Private helpers ──────────────────────────────────────

    private suspend fun fetchSteps(bearer: String, start: Long, end: Long): Int {
        val response = HuaweiApiFactory.healthApi.querySampleSet(
            authorization = bearer,
            request = HealthQueryRequest(TYPE_STEPS, start, end)
        )
        val points = response.sampleSets?.firstOrNull()?.samplePoints ?: return 0
        return points.sumOf { pt ->
            pt.fields?.firstOrNull { it.name == "steps" }?.value?.roundToInt() ?: 0
        }
    }

    private suspend fun fetchHeartRate(bearer: String, start: Long, end: Long): HeartRateData {
        val response = HuaweiApiFactory.healthApi.querySampleSet(
            authorization = bearer,
            request = HealthQueryRequest(TYPE_HEART_RATE, start, end)
        )
        val points = response.sampleSets?.firstOrNull()?.samplePoints ?: emptyList()

        val avgs = points.mapNotNull { pt -> pt.fieldValue("avg_heart_rate") }
        val mins = points.mapNotNull { pt -> pt.fieldValue("min_heart_rate") }
        val maxs = points.mapNotNull { pt -> pt.fieldValue("max_heart_rate") }

        return HeartRateData(
            avg = avgs.average().roundToIntOrZero(),
            min = mins.minOrNull()?.roundToInt() ?: 0,
            max = maxs.maxOrNull()?.roundToInt() ?: 0
        )
    }

    /**
     * Parses sleep records.
     * sleep_type values: 0=awake, 1=light, 2=deep, 3=REM
     */
    private suspend fun fetchSleep(bearer: String, start: Long, end: Long): SleepData {
        val response = HuaweiApiFactory.healthApi.querySampleSet(
            authorization = bearer,
            request = HealthQueryRequest(TYPE_SLEEP, start, end)
        )
        val points = response.sampleSets?.firstOrNull()?.samplePoints ?: emptyList()

        var deepMs  = 0L
        var remMs   = 0L
        var totalMs = 0L

        for (pt in points) {
            val sleepType = pt.fieldValue("sleep_type")?.roundToInt() ?: continue
            val duration  = pt.endTime - pt.startTime
            when (sleepType) {
                1 -> totalMs += duration
                2 -> { deepMs += duration; totalMs += duration }
                3 -> { remMs  += duration; totalMs += duration }
            }
        }

        fun Long.toHours() = this / 3_600_000.0

        return SleepData(
            totalHours = totalMs.toHours().roundTo1(),
            deepHours  = deepMs.toHours().roundTo1(),
            remHours   = remMs.toHours().roundTo1()
        )
    }

    // ── Utils ────────────────────────────────────────────────

    private fun SamplePoint.fieldValue(name: String): Double? =
        fields?.firstOrNull { it.name == name }?.value

    private fun List<Double>.average(): Double =
        if (isEmpty()) 0.0 else sum() / size

    private fun Double.roundToIntOrZero(): Int =
        if (isNaN() || isInfinite()) 0 else roundToInt()

    private fun Double.roundTo1(): Double =
        (this * 10).roundToInt() / 10.0
}
