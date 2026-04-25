import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// local.properties에서 API Key 읽기 로직
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}
val mapsApiKey: String = localProperties.getProperty("MAPS_API_KEY")?.takeUnless { it.isBlank() }
    ?: System.getenv("MAPS_API_KEY")?.takeUnless { it.isBlank() }
    ?: ""
val maps3dApiKey: String = localProperties.getProperty("MAPS3D_API_KEY")?.takeUnless { it.isBlank() }
    ?: System.getenv("MAPS3D_API_KEY")?.takeUnless { it.isBlank() }
    ?: mapsApiKey
val testAdmobAppId = "ca-app-pub-3940256099942544~3347511713"
val testAdmobRewardedAdUnitId = "ca-app-pub-3940256099942544/5224354917"
val releaseAdmobAppId: String? = localProperties.getProperty("ADMOB_APP_ID")?.takeUnless { it.isBlank() }
    ?: System.getenv("ADMOB_APP_ID")?.takeUnless { it.isBlank() }
val releaseAdmobRewardedAdUnitId: String? = localProperties.getProperty("ADMOB_REWARDED_AD_UNIT_ID")?.takeUnless { it.isBlank() }
    ?: System.getenv("ADMOB_REWARDED_AD_UNIT_ID")?.takeUnless { it.isBlank() }
val resolvedReleaseAdmobAppId = releaseAdmobAppId ?: "__missing_admob_app_id__"
val resolvedReleaseAdmobRewardedAdUnitId =
    releaseAdmobRewardedAdUnitId ?: "__missing_rewarded_ad_unit_id__"
val redeemApiBaseUrl: String = localProperties.getProperty("REDEEM_API_BASE_URL")?.takeUnless { it.isBlank() }
    ?: System.getenv("REDEEM_API_BASE_URL")?.takeUnless { it.isBlank() }
    ?: "https://openflight-redeem-api.junuh145858.workers.dev"
val versionApiBaseUrl: String = localProperties.getProperty("VERSION_API_BASE_URL")?.takeUnless { it.isBlank() }
    ?: System.getenv("VERSION_API_BASE_URL")?.takeUnless { it.isBlank() }
    ?: redeemApiBaseUrl
val githubReleasesUrl: String = localProperties.getProperty("OPENFLIGHT_RELEASES_URL")?.takeUnless { it.isBlank() }
    ?: System.getenv("OPENFLIGHT_RELEASES_URL")?.takeUnless { it.isBlank() }
    ?: "https://github.com/Transient-Onlooker/openflight4and/releases/latest"
val releaseChannel: String = localProperties.getProperty("OPENFLIGHT_RELEASE_CHANNEL")?.takeUnless { it.isBlank() }
    ?: System.getenv("OPENFLIGHT_RELEASE_CHANNEL")?.takeUnless { it.isBlank() }
    ?: "stable"
val releaseVersionName: String = localProperties.getProperty("OPENFLIGHT_VERSION_NAME")?.takeUnless { it.isBlank() }
    ?: System.getenv("OPENFLIGHT_VERSION_NAME")?.takeUnless { it.isBlank() }
    ?: "V2.9.1"
val releaseKeystorePath: String? = localProperties.getProperty("RELEASE_KEYSTORE_PATH")?.takeUnless { it.isBlank() }
    ?: System.getenv("RELEASE_KEYSTORE_PATH")?.takeUnless { it.isBlank() }
val releaseKeystorePassword: String? = localProperties.getProperty("KEYSTORE_PASSWORD")?.takeUnless { it.isBlank() }
    ?: System.getenv("KEYSTORE_PASSWORD")?.takeUnless { it.isBlank() }
val releaseKeyAlias: String? = localProperties.getProperty("KEY_ALIAS")?.takeUnless { it.isBlank() }
    ?: System.getenv("KEY_ALIAS")?.takeUnless { it.isBlank() }
val releaseKeyPassword: String? = localProperties.getProperty("KEY_PASSWORD")?.takeUnless { it.isBlank() }
    ?: System.getenv("KEY_PASSWORD")?.takeUnless { it.isBlank() }
val hasReleaseSigning =
    releaseKeystorePath != null &&
        releaseKeystorePassword != null &&
        releaseKeyAlias != null &&
        releaseKeyPassword != null
if (gradle.startParameter.taskNames.any { taskName -> taskName.contains("Release", ignoreCase = true) }) {
    require(!releaseAdmobAppId.isNullOrBlank()) {
        "Release build requires ADMOB_APP_ID in local.properties or environment variables."
    }
    require(!releaseAdmobRewardedAdUnitId.isNullOrBlank()) {
        "Release build requires ADMOB_REWARDED_AD_UNIT_ID in local.properties or environment variables."
    }
}

fun String.toVersionCode(): Int {
    val normalized = trim()
    val stableMatch = Regex("""^V?(\d+)\.(\d{1,2})\.(\d{1,2})$""").matchEntire(normalized)
    if (stableMatch != null) {
        val (major, minor, patch) = stableMatch.destructured
        return "$major${minor.padStart(2, '0')}${patch.padStart(2, '0')}0000".toInt()
    }

    val betaMatch = Regex("""^V?(\d+)\.(\d{1,2})\.(\d{1,2})\.Beta\.(\d{4})$""").matchEntire(normalized)
    if (betaMatch != null) {
        val (major, minor, patch, betaSequence) = betaMatch.destructured
        return "$major${minor.padStart(2, '0')}${patch.padStart(2, '0')}$betaSequence".toInt()
    }

    error("Unsupported OPENFLIGHT_VERSION_NAME format: $this")
}

require(releaseChannel in setOf("stable", "beta")) {
    "OPENFLIGHT_RELEASE_CHANNEL must be either 'stable' or 'beta'."
}

android {
    namespace = "com.example.openflight4and"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.openflight4and.app.android"
        minSdk = 28
        targetSdk = 36
        versionCode = releaseVersionName.toVersionCode()
        versionName = releaseVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "REDEEM_API_BASE_URL", "\"$redeemApiBaseUrl\"")
        buildConfigField("String", "VERSION_API_BASE_URL", "\"$versionApiBaseUrl\"")
        buildConfigField("String", "GITHUB_RELEASES_URL", "\"$githubReleasesUrl\"")
        buildConfigField("String", "RELEASE_CHANNEL", "\"$releaseChannel\"")

        // Manifest에 API Key 주입
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        manifestPlaceholders["MAPS3D_API_KEY"] = maps3dApiKey
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "ADMOB_REWARDED_AD_UNIT_ID", "\"$testAdmobRewardedAdUnitId\"")
            manifestPlaceholders["ADMOB_APP_ID"] = testAdmobAppId
        }
        release {
            isMinifyEnabled = false
            buildConfigField("String", "ADMOB_REWARDED_AD_UNIT_ID", "\"$resolvedReleaseAdmobRewardedAdUnitId\"")
            manifestPlaceholders["ADMOB_APP_ID"] = resolvedReleaseAdmobAppId
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.material)

    // Google Maps
    implementation(libs.play.services.ads)
    implementation(libs.play.services.maps)
    implementation(libs.play.services.maps3d)
    implementation(libs.maps.compose)

    // DataStore & Serialization
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
