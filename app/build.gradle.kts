import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationScope
import simpletv.buildlogic.AndroidReleaseInputs
import simpletv.buildlogic.ReleaseStrings
import java.security.KeyStore
import java.security.MessageDigest
import java.util.HexFormat
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ktlint)
}

// Версия: в CI берётся из тега (v1.2.3) и номера сборки, локально — значения по умолчанию.
val ciVersionName: String? = System.getenv("VERSION_NAME")?.removePrefix("v")?.ifBlank { null }
val ciVersionCode: Int? = System.getenv("VERSION_CODE")?.toIntOrNull()

// Ключ подписи релизов: из переменных окружения (секреты GitHub) или keystore/release.properties.
val keystoreProps =
    Properties().apply {
        val f = rootProject.file("keystore/release.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }

fun signingValue(
    env: String,
    prop: String,
): String? = System.getenv(env)?.ifBlank { null } ?: keystoreProps.getProperty(prop)
val releaseStoreFile: String? = signingValue("SIGNING_STORE_FILE", "storeFile")
val releaseSigner =
    releaseStoreFile?.let { path ->
        val storePassword =
            requireNotNull(signingValue("SIGNING_STORE_PASSWORD", "storePassword")) {
                "Release signing store password is required"
            }
        val keyAlias =
            requireNotNull(signingValue("SIGNING_KEY_ALIAS", "keyAlias")) {
                "Release signing key alias is required"
            }
        val store = KeyStore.getInstance(rootProject.file(path), storePassword.toCharArray())
        val certificate = requireNotNull(store.getCertificate(keyAlias)) { "Release signing certificate not found" }
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(certificate.encoded))
    }.orEmpty()
val configuredTlsPins =
    providers.gradleProperty("simpletvTlsPins")
        .orElse(providers.environmentVariable("SIMPLETV_TLS_PINS"))
        .orElse("")

// Единый источник версии для локальных сборок. CI передаёт VERSION_NAME/VERSION_CODE.
val versionProps =
    Properties().apply {
        rootProject.file("version.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }
val defaultVersionName: String = versionProps.getProperty("versionName")
    ?: error("version.properties is missing versionName")
val defaultVersionCode: Int = versionProps.getProperty("versionCode")?.toIntOrNull()
    ?: error("version.properties is missing a valid versionCode")

android {
    namespace = "io.mediacast.simpletv"
    ndkVersion = "28.2.13676358"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.mediacast.simpletv"
        minSdk = if (configuredTlsPins.get().isBlank()) 23 else 24
        targetSdk = 37
        versionCode = ciVersionCode ?: defaultVersionCode
        versionName = ciVersionName ?: defaultVersionName
        // Приложение переведено только на русский (по умолчанию) и английский:
        // без явного списка прочие локали тишиной получали русские ресурсы.
        manifestPlaceholders["networkSecurityConfig"] = "@xml/network_security_config"
    }

    androidResources {
        localeFilters += listOf("ru", "en")
    }

    sourceSets.named("main") {
        assets.directories.add(rootProject.file("desktop/assets/catalog").path)
    }
    sourceSets.named("debug") {
        assets.directories.add(rootProject.file("desktop/assets").path)
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/jni/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = signingValue("SIGNING_STORE_PASSWORD", "storePassword")
                keyAlias = signingValue("SIGNING_KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("SIGNING_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isDebuggable = false
            manifestPlaceholders["networkSecurityConfig"] = "@xml/network_security_config_release"
            optimization {
                enable = true
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Без ключа релиза подписываем debug-ключом, чтобы локальный APK ставился.
            // В CI (когда задан VERSION_NAME) отсутствие ключа — ошибка, иначе релиз уйдёт с debug-подписью.
            val releaseSigning = signingConfigs.findByName("release")
            check(releaseSigning != null || ciVersionName == null) {
                "Release signing is not configured. Set SIGNING_STORE_FILE, SIGNING_STORE_PASSWORD, " +
                    "SIGNING_KEY_ALIAS and SIGNING_KEY_PASSWORD (or keystore/release.properties)."
            }
            signingConfig = releaseSigning ?: signingConfigs.getByName("debug")
        }
    }
    lint {
        checkReleaseBuilds = true
        abortOnError = true
        warningsAsErrors = false
        checkDependencies = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions.freeCompilerArgs.add("-Xstring-concat=inline")
}

androidComponents.onVariants(androidComponents.selector().withBuildType("release")) { variant ->
    val inputs =
        tasks.register<AndroidReleaseInputs>("generateSimpleTvReleaseInputs") {
            playlistsDirectory.set(rootProject.layout.projectDirectory.dir("desktop/assets/playlists"))
            assetsDirectory.set(layout.buildDirectory.dir("generated/simpletv/release/assets"))
            resourcesDirectory.set(layout.buildDirectory.dir("generated/simpletv/release/res"))
            tlsPins.set(configuredTlsPins)
            signer.set(releaseSigner)
        }
    variant.sources.assets?.addGeneratedSourceDirectory(inputs) { it.assetsDirectory }
    variant.sources.res?.addGeneratedSourceDirectory(inputs) { it.resourcesDirectory }
    variant.instrumentation.transformClassesWith(ReleaseStrings::class.java, InstrumentationScope.ALL) { params ->
        params.signer.set(releaseSigner)
    }
    variant.instrumentation.setAsmFramesComputationMode(FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_CLASSES)
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.dash)
    implementation(libs.media3.hls)
    implementation(libs.media3.ui)
    implementation(libs.media3.ffmpeg)
    implementation(libs.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
}
