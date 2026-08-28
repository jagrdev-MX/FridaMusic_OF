package com.jagr.fridamusic.utils.qobuz

import com.jagr.fridamusic.BuildConfig
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/** Authorized Qobuz API client. Credentials are supplied at build time, never hardcoded. */
class AuthorizedLosslessClient {
    data class ResolvedFile(
        val url: String,
        val formatId: Int,
        val mimeType: String,
        val bitrate: Int,
        val sampleRate: Int?,
        val bitDepth: Int?,
        val contentLength: Long?,
    )

    @Serializable
    private data class FileUrlResponse(
        val url: String? = null,
        @SerialName("format_id") val formatId: Int? = null,
        @SerialName("mime_type") val mimeType: String? = null,
        @SerialName("sampling_rate") val samplingRate: Double? = null,
        @SerialName("bit_depth") val bitDepth: Int? = null,
        @SerialName("bit_rate") val bitRate: Int? = null,
        @SerialName("file_size") val fileSize: Long? = null,
        val duration: Double? = null,
        val sample: Boolean = false,
        val restrictions: JsonElement? = null,
    )

    val isConfigured: Boolean
        get() = BuildConfig.QOBUZ_API_BASE_URL.isNotBlank() &&
            BuildConfig.QOBUZ_APP_ID.isNotBlank() &&
            BuildConfig.QOBUZ_APP_SECRET.isNotBlank() &&
            BuildConfig.QOBUZ_USER_AUTH_TOKEN.isNotBlank()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(7, TimeUnit.SECONDS)
        .callTimeout(9, TimeUnit.SECONDS)
        .build()

    suspend fun search(query: String, offset: Int = 0): QobuzSearchData = withContext(Dispatchers.IO) {
        ensureConfigured()
        val url = endpoint("track/search").newBuilder()
            .addQueryParameter("query", query)
            .addQueryParameter("limit", "10")
            .addQueryParameter("offset", offset.toString())
            .build()
        execute<QobuzSearchData>(request(url.toString()))
    }

    suspend fun getBestFileUrl(trackId: Long): ResolvedFile? = withContext(Dispatchers.IO) {
        ensureConfigured()
        for (formatId in QUALITY_ORDER) {
            val result = runCatching { getFileUrl(trackId, formatId) }.getOrNull()
            if (result != null) return@withContext result
        }
        null
    }

    private fun getFileUrl(trackId: Long, requestedFormatId: Int): ResolvedFile? {
        val requestTimestamp = (System.currentTimeMillis() / 1_000L).toString()
        val intent = "stream"
        val signaturePayload = buildString {
            append("trackgetFileUrlformat_id")
            append(requestedFormatId)
            append("intent")
            append(intent)
            append("track_id")
            append(trackId)
            append(requestTimestamp)
            append(BuildConfig.QOBUZ_APP_SECRET)
        }
        val signature = MessageDigest.getInstance("MD5")
            .digest(signaturePayload.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

        val url = endpoint("track/getFileUrl").newBuilder()
            .addQueryParameter("format_id", requestedFormatId.toString())
            .addQueryParameter("intent", intent)
            .addQueryParameter("track_id", trackId.toString())
            .addQueryParameter("request_ts", requestTimestamp)
            .addQueryParameter("request_sig", signature)
            .build()
        val response = execute<FileUrlResponse>(request(url.toString()))
        val streamUrl = response.url?.takeIf(::isFullPlayableUrl) ?: return null
        if (response.sample || hasRestrictions(response.restrictions)) return null

        val sampleRate = response.samplingRate?.let { rate ->
            if (rate >= 1_000) rate.toInt() else (rate * 1_000).toInt()
        }
        val calculatedBitrate = if (
            response.fileSize != null && response.fileSize > 0 &&
            response.duration != null && response.duration > 0
        ) {
            ((response.fileSize * 8.0) / response.duration).toInt()
        } else {
            0
        }

        return ResolvedFile(
            url = streamUrl,
            formatId = response.formatId ?: requestedFormatId,
            mimeType = response.mimeType?.takeIf { it.isNotBlank() } ?: "audio/flac",
            bitrate = response.bitRate?.takeIf { it > 0 } ?: calculatedBitrate,
            sampleRate = sampleRate,
            bitDepth = response.bitDepth,
            contentLength = response.fileSize?.takeIf { it > 0 },
        )
    }

    private inline fun <reified T> execute(request: Request): T {
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Authorized lossless request failed: HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) throw IOException("Authorized lossless response was empty")
            return runCatching { json.decodeFromString<T>(body) }
                .getOrElse { throw IOException("Authorized lossless response was malformed", it) }
        }
    }

    private fun request(url: String): Request = Request.Builder()
        .url(url)
        .get()
        .header("Accept", "application/json")
        .header("User-Agent", "FridaMusic/1.0")
        .header("X-App-Id", BuildConfig.QOBUZ_APP_ID)
        .header("X-User-Auth-Token", BuildConfig.QOBUZ_USER_AUTH_TOKEN)
        .build()

    private fun endpoint(path: String) =
        "${BuildConfig.QOBUZ_API_BASE_URL.trimEnd('/')}/$path".toHttpUrl()

    private fun ensureConfigured() {
        check(isConfigured) { "Authorized lossless provider is not configured" }
    }

    private fun isFullPlayableUrl(url: String): Boolean {
        val parsed = url.toHttpUrlOrNull() ?: return false
        return parsed.scheme in setOf("http", "https") &&
            !url.contains("preview", ignoreCase = true) &&
            !url.contains("sample", ignoreCase = true)
    }

    private fun hasRestrictions(restrictions: JsonElement?): Boolean = when (restrictions) {
        null, JsonNull -> false
        is JsonArray -> restrictions.isNotEmpty()
        is JsonObject -> restrictions.isNotEmpty()
        is JsonPrimitive -> restrictions.content
            .trim()
            .let { it.isNotEmpty() && it !in setOf("false", "null", "[]", "{}") }
        else -> true
    }

    private companion object {
        val QUALITY_ORDER = intArrayOf(
            QobuzQuality.FLAC_HIRES_192,
            QobuzQuality.FLAC_HIRES_96,
            QobuzQuality.FLAC_CD,
        )
    }
}
