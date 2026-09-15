import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
    alias(libs.plugins.screenshot)
    alias(libs.plugins.aboutlibraries)
}

// Release signing: keystore.properties locally (a gitignored symlink outside the repo),
// or PARKS_* environment variables in CI. AGP 9's signing DSL rejects imperative code
// inside signingConfigs { }, so everything is computed up here first.
val keystorePropertiesFile = File(System.getProperty("user.home"), ".config/parks/keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) keystorePropertiesFile.inputStream().use { load(it) }
}
fun signing(localKey: String, envKey: String): String? =
    keystoreProperties.getProperty(localKey) ?: System.getenv(envKey)

val releaseStoreFile: File? = signing("storeFile", "PARKS_KEYSTORE_PATH")?.let { file(it) }
val releaseStorePassword: String? = signing("storePassword", "PARKS_KEYSTORE_PASSWORD")
val releaseKeyAlias: String? = signing("keyAlias", "PARKS_KEY_ALIAS")
val releaseKeyPassword: String? = signing("keyPassword", "PARKS_KEY_PASSWORD")

android {
    namespace = "contact.kaufman.parks"
    compileSdk = 37
    // Compose alphas require 37.1+; a bare `compileSdk = 37` resolves to 37.0.
    compileSdkMinor = 2

    defaultConfig {
        applicationId = "contact.kaufman.parks"
        minSdk = 31
        targetSdk = 37
        // CI supplies these from the tag; the fallbacks are for local builds only.
        // AGP rejects a versionCode of 0, so an unusable value falls back rather than
        // failing configuration.
        versionCode = System.getenv("PARKS_VERSION_CODE")?.toIntOrNull()?.takeIf { it > 0 } ?: 1
        versionName = System.getenv("PARKS_VERSION_NAME") ?: "0.1.0"

        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("release") {
            storeFile = releaseStoreFile
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (releaseStoreFile != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // The screenshot-test plugin wants this in BOTH places: gradle.properties for the
    // plugin's own configuration, and here for the module's source set.
    experimentalProperties["android.experimental.enableScreenshotTest"] = true

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        // Adopting the expressive components is the whole point of running the alpha
        // Compose line, so opt in once here rather than at every call site.
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
        )
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    screenshotTestImplementation(libs.androidx.compose.ui.tooling)
    screenshotTestImplementation(libs.screenshot.validation.api)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.logging)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.datetime)


    implementation(libs.aboutlibraries.compose.m3)

    testImplementation(libs.junit)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlinx.coroutines.test)
}
