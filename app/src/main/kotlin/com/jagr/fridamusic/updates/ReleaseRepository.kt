package com.jagr.fridamusic.updates

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

data class AppRelease(
    val tag: String,
    val title: String,
    val notes: String,
    val publishedDate: String?,
    val url: String,
)

object ReleaseRepository {
    private const val RELEASES_URL =
        "https://api.github.com/repos/jagrdev-MX/FridaMusic_OF/releases?per_page=10"
    private const val TIMEOUT_MS = 8_000
    private const val CACHE_FILE = "fridamusic_releases.json"
    private const val CACHE_TTL_MS = 30 * 60 * 1_000L

    private val json = Json { ignoreUnknownKeys = true }
    private var memoryCache: Pair<Long, List<AppRelease>>? = null

    suspend fun releases(context: Context): Result<List<AppRelease>> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        memoryCache?.takeIf { now - it.first < CACHE_TTL_MS }?.let {
            return@withContext Result.success(it.second)
        }

        val cacheFile = context.cacheDir.resolve(CACHE_FILE)
        runCatching {
            val response = requestReleases()
            val parsed = parse(response)
            require(parsed.isNotEmpty()) { "GitHub returned no public releases" }
            runCatching { cacheFile.writeText(response) }
            memoryCache = now to parsed
            parsed
        }.recoverCatching {
            val parsed = parse(cacheFile.readText())
            require(parsed.isNotEmpty()) { "Cached release list is empty" }
            memoryCache = now to parsed
            parsed
        }
    }

    private fun requestReleases(): String {
        val connection = URL(RELEASES_URL).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "FridaMusic-Android")
            if (connection.responseCode !in 200..299) {
                error("GitHub releases request failed: ${connection.responseCode}")
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(value: String): List<AppRelease> =
        json.decodeFromString<List<GitHubRelease>>(value).map { release ->
            AppRelease(
                tag = release.tagName,
                title = release.name?.takeIf(String::isNotBlank) ?: release.tagName,
                notes = cleanMarkdown(release.body.orEmpty()),
                publishedDate = release.publishedAt?.substringBefore('T'),
                url = release.htmlUrl,
            )
        }

    private fun cleanMarkdown(markdown: String): String = markdown
        .lineSequence()
        .map { line -> line.trim().removePrefix("###").removePrefix("##").removePrefix("#").trim() }
        .filterNot { it.startsWith("<!--") || it.startsWith("<details") || it.startsWith("</details") }
        .joinToString("\n")
        .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
        .replace(Regex("\\[(.*?)]\\((.*?)\\)"), "$1")
        .replace(Regex("<[^>]+>"), "")
        .trim()

    @Serializable
    private data class GitHubRelease(
        @SerialName("tag_name") val tagName: String,
        val name: String? = null,
        val body: String? = null,
        @SerialName("published_at") val publishedAt: String? = null,
        @SerialName("html_url") val htmlUrl: String,
    )
}
