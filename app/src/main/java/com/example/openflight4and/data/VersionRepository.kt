package com.example.openflight4and.data

import com.example.openflight4and.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class VersionStatus(
    val currentVersion: String,
    val allowedVersion: String,
    val recentVersion: String,
    val requirement: UpdateRequirement
)

enum class UpdateRequirement {
    NONE,
    RECOMMENDED,
    REQUIRED
}

class VersionRepository(
    private val reportError: (String, Throwable) -> Unit
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchVersionStatus(): VersionStatus? = withContext(Dispatchers.IO) {
        val connection = try {
            (URL("${BuildConfig.VERSION_API_BASE_URL}/version").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                doInput = true
            }
        } catch (e: Exception) {
            reportError("Failed to open version API connection", e)
            return@withContext null
        }

        try {
            val statusCode = connection.responseCode
            val responseText = if (statusCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }

            if (statusCode !in 200..299) {
                reportError("Version API responded with HTTP $statusCode: $responseText", IllegalStateException("HTTP $statusCode"))
                return@withContext null
            }

            val response = json.decodeFromString<VersionApiResponse>(responseText)
            if (!response.ok || response.allowedVersion.isNullOrBlank() || response.recentVersion.isNullOrBlank()) {
                reportError("Version API returned invalid payload", IllegalStateException(responseText))
                return@withContext null
            }

            val currentVersion = BuildConfig.VERSION_NAME
            val requirement = when {
                compareVersions(currentVersion, response.allowedVersion) < 0 -> UpdateRequirement.REQUIRED
                compareVersions(currentVersion, response.recentVersion) < 0 -> UpdateRequirement.RECOMMENDED
                else -> UpdateRequirement.NONE
            }

            VersionStatus(
                currentVersion = currentVersion,
                allowedVersion = response.allowedVersion,
                recentVersion = response.recentVersion,
                requirement = requirement
            )
        } catch (e: Exception) {
            reportError("Failed to fetch version status", e)
            null
        } finally {
            connection.disconnect()
        }
    }

    internal fun compareVersions(current: String, target: String): Int {
        val currentParts = current.extractVersionParts()
        val targetParts = target.extractVersionParts()
        val maxSize = maxOf(currentParts.size, targetParts.size)

        for (index in 0 until maxSize) {
            val currentPart = currentParts.getOrElse(index) { 0 }
            val targetPart = targetParts.getOrElse(index) { 0 }
            if (currentPart != targetPart) {
                return currentPart.compareTo(targetPart)
            }
        }
        return 0
    }

    private fun String.extractVersionParts(): List<Int> =
        trim()
            .removePrefix("v")
            .removePrefix("V")
            .split('.')
            .mapNotNull { part -> part.takeWhile { it.isDigit() }.takeIf { it.isNotEmpty() }?.toIntOrNull() }
            .ifEmpty { listOf(0) }

    @Serializable
    private data class VersionApiResponse(
        val ok: Boolean = false,
        @SerialName("allowedVersion") val allowedVersion: String? = null,
        @SerialName("recentVersion") val recentVersion: String? = null
    )
}
