package com.jagr.fridamusic.utils.saavn

import com.jagr.fridamusic.BuildConfig
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/** Client for a Frida-controlled backend compatible with anxkhn/jiosaavn-api. */
class SaavnDirectProvider {
    data class Candidate(
        val id: String,
        val title: String,
        val artists: String,
        val durationSeconds: Long,
        val streamUrl: String,
        val imageUrl: String?,
        val bitrate: Int,
    )

    val isConfigured: Boolean
        get() = BuildConfig.FRIDA_SAAVN_API_BASE_URL.isNotBlank()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(6, TimeUnit.SECONDS)
        .build()

    suspend fun searchSongs(query: String): Result<List<Candidate>> = runCatching {
        check(isConfigured) { "Direct media provider is not configured" }

        withContext(Dispatchers.IO) {
            val endpoint = "${BuildConfig.FRIDA_SAAVN_API_BASE_URL.trimEnd('/')}/song/"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("query", query)
                .addQueryParameter("lyrics", "false")
                .addQueryParameter("songdata", "true")
                .build()

            val requestBuilder = Request.Builder()
                .url(endpoint)
                .get()
                .header("Accept", "application/json")
                .header("User-Agent", "FridaMusic/1.0")

            BuildConfig.FRIDA_SAAVN_API_TOKEN
                .takeIf { it.isNotBlank() }
                ?.let { requestBuilder.header("Authorization", "Bearer $it") }

            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Direct media search failed: HTTP ${response.code}")
                }

                val body = response.body?.string().orEmpty()
                val candidates = extractCandidateArray(json.parseToJsonElement(body))
                    .mapNotNull(::parseCandidate)
                if (candidates.isEmpty()) {
                    throw NoSuchElementException("Direct media search returned no playable candidates")
                }
                candidates
            }
        }
    }

    private fun extractCandidateArray(element: JsonElement): JsonArray = when (element) {
        is JsonArray -> element
        is JsonObject -> {
            val nested = element["data"] ?: element["songs"] ?: element["results"]
            when (nested) {
                is JsonArray -> nested
                is JsonObject -> (nested["songs"] ?: nested["results"]) as? JsonArray ?: JsonArray(emptyList())
                else -> JsonArray(emptyList())
            }
        }
        else -> JsonArray(emptyList())
    }

    private fun parseCandidate(element: JsonElement): Candidate? {
        val item = element as? JsonObject ?: return null
        val streamUrl = item.string("media_url") ?: return null
        val parsedUrl = streamUrl.toHttpUrlOrNull() ?: return null
        if (parsedUrl.scheme !in setOf("http", "https") || isPreviewUrl(streamUrl)) return null

        val title = item.string("song", "name", "title")?.takeIf { it.isNotBlank() } ?: return null
        val artists = item.string("primary_artists", "artists", "singers")
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val id = item.string("id")?.takeIf { it.isNotBlank() } ?: return null
        val duration = item.long("duration") ?: 0L
        val supports320 = item.boolean("320kbps") == true ||
            item.string("320kbps")?.equals("true", ignoreCase = true) == true

        return Candidate(
            id = id,
            title = title,
            artists = artists,
            durationSeconds = duration,
            streamUrl = streamUrl,
            imageUrl = item.string("image")?.takeIf { it.toHttpUrlOrNull() != null },
            bitrate = when {
                supports320 || streamUrl.contains("_320.", ignoreCase = true) -> 320_000
                streamUrl.contains("_160.", ignoreCase = true) -> 160_000
                streamUrl.contains("_96.", ignoreCase = true) -> 96_000
                else -> 0
            },
        )
    }

    private fun JsonObject.string(vararg names: String): String? = names.firstNotNullOfOrNull { name ->
        this[name]?.jsonPrimitive?.contentOrNull
    }

    private fun JsonObject.long(name: String): Long? =
        this[name]?.jsonPrimitive?.let { it.longOrNull ?: it.contentOrNull?.toLongOrNull() }

    private fun JsonObject.boolean(name: String): Boolean? =
        this[name]?.jsonPrimitive?.let {
            it.booleanOrNull ?: it.contentOrNull?.toBooleanStrictOrNull()
        }

    private fun isPreviewUrl(url: String): Boolean =
        url.contains("preview", ignoreCase = true) ||
            url.contains("_96_p.", ignoreCase = true)
}
