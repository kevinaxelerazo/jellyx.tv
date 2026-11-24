import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.androidx.room3)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

java { toolchain { languageVersion = Versions.JAVA } }

android {
    namespace = "dev.jdtech.jellyfin.data"
    compileSdk = Versions.COMPILE_SDK
    buildToolsVersion = Versions.BUILD_TOOLS

    defaultConfig {
        minSdk = Versions.MIN_SDK

        val localProperties =
            Properties().apply {
                val propsFile = rootProject.file("local.properties")
                if (propsFile.exists()) {
                    propsFile.inputStream().use { load(it) }
                }
            }
        val defaultServerUrl =
            localProperties
                .getProperty("JELLYX_DEFAULT_SERVER_URL")
                ?.trim()
                .orEmpty()
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
        val fallbackServerUrl =
            localProperties
                .getProperty("JELLYX_FALLBACK_SERVER_URL")
                ?.trim()
                .orEmpty()
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
        val defaultServerName =
            localProperties
                .getProperty("JELLYX_DEFAULT_SERVER_NAME")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.replace("\\", "\\\\")
                ?.replace("\"", "\\\"")
                ?: "Jellyx.tv"
        val tmdbApiKey =
            localProperties
                .getProperty("JELLYX_TMDB_API_KEY")
                ?.trim()
                .orEmpty()
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
        val omdbApiKey =
            localProperties
                .getProperty("JELLYX_OMDB_API_KEY")
                ?.trim()
                .orEmpty()
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
        val mdblistApiKey =
            localProperties
                .getProperty("JELLYX_MDBLIST_API_KEY")
                ?.trim()
                .orEmpty()
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
        val updateRepo =
            localProperties
                .getProperty("JELLYX_UPDATE_REPO")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.replace("\\", "\\\\")
                ?.replace("\"", "\\\"")
                ?: "kevinaxelerazo/jellyx.tv"
        val updateManifestUrl =
            localProperties
                .getProperty("JELLYX_UPDATE_MANIFEST_URL")
                ?.trim()
                .orEmpty()
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")

        buildConfigField("int", "VERSION_CODE", Versions.APP_CODE.toString())
        buildConfigField("String", "VERSION_NAME", "\"${Versions.APP_NAME}\"")
        buildConfigField("String", "DEFAULT_SERVER_URL", "\"$defaultServerUrl\"")
        buildConfigField("String", "FALLBACK_SERVER_URL", "\"$fallbackServerUrl\"")
        buildConfigField("String", "DEFAULT_SERVER_NAME", "\"$defaultServerName\"")
        buildConfigField("String", "TMDB_API_KEY", "\"$tmdbApiKey\"")
        buildConfigField("String", "OMDB_API_KEY", "\"$omdbApiKey\"")
        buildConfigField("String", "MDBLIST_API_KEY", "\"$mdblistApiKey\"")
        buildConfigField("String", "UPDATE_REPO", "\"$updateRepo\"")
        buildConfigField("String", "UPDATE_MANIFEST_URL", "\"$updateManifestUrl\"")

        consumerProguardFile("proguard-rules.pro")
    }

    buildTypes {
        named("release") { isMinifyEnabled = false }
        register("staging") { initWith(getByName("release")) }
    }

    buildFeatures { buildConfig = true }

    testOptions { unitTests.isReturnDefaultValues = true }
}

room3 { schemaDirectory("$projectDir/schemas") }

dependencies {
    implementation(projects.settings)
    implementation(libs.androidx.paging)
    implementation(libs.androidx.room3.runtime)
    ksp(libs.androidx.room3.compiler)
    implementation(libs.jellyfin.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.timber)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}
