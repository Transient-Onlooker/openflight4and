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
            val requirement = determineRequirement(
                releaseChannel = BuildConfig.RELEASE_CHANNEL,
                currentVersionCode = BuildConfig.VERSION_CODE,
                allowedVersion = response.allowedVersion,
                recentVersion = response.recentVersion
            )

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

    internal fun determineRequirement(
        releaseChannel: String,
        currentVersionCode: Int,
        allowedVersion: String,
        recentVersion: String
    ): UpdateRequirement {
        val allowedVersionCode = parseVersionCode(allowedVersion)
        val recentVersionCode = parseVersionCode(recentVersion)

        return when {
            currentVersionCode < allowedVersionCode -> UpdateRequirement.REQUIRED
            releaseChannel.equals("beta", ignoreCase = true) -> UpdateRequirement.NONE
            currentVersionCode < recentVersionCode -> UpdateRequirement.RECOMMENDED
            else -> UpdateRequirement.NONE
        }
    }

    internal fun compareVersions(current: String, target: String): Int =
        parseVersionCode(current).compareTo(parseVersionCode(target))

    internal fun parseVersionCode(version: String): Int {
        val normalized = version.trim()

        val stableMatch = STABLE_VERSION_REGEX.matchEntire(normalized)
        if (stableMatch != null) {
            val (major, minor, patch) = stableMatch.destructured
            return "$major${minor.padStart(2, '0')}${patch.padStart(2, '0')}0000".toInt()
        }

        val betaMatch = BETA_VERSION_REGEX.matchEntire(normalized)
        if (betaMatch != null) {
            val (major, minor, patch, betaSequence) = betaMatch.destructured
            return "$major${minor.padStart(2, '0')}${patch.padStart(2, '0')}$betaSequence".toInt()
        }

        throw IllegalArgumentException("Unsupported version format: $version")
    }

    @Serializable
    private data class VersionApiResponse(
        val ok: Boolean = false,
        @SerialName("allowedVersion") val allowedVersion: String? = null,
        @SerialName("recentVersion") val recentVersion: String? = null
    )

    private companion object {
        val STABLE_VERSION_REGEX = Regex("""^V?(\d+)\.(\d{1,2})\.(\d{1,2})$""")
        val BETA_VERSION_REGEX = Regex("""^V?(\d+)\.(\d{1,2})\.(\d{1,2})\.Beta\.(\d{4})$""")
    }
}
