import java.net.URI
import java.security.MessageDigest
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// ---- Xray core ---------------------------------------------------------------------------------

val xrayVersion = providers.gradleProperty("xray.lib.version").get()
val xraySha256 = providers.gradleProperty("xray.lib.sha256").get()
val xrayAar = layout.projectDirectory.file("libs/libv2ray-$xrayVersion.aar").asFile

val downloadXrayCore = tasks.register("downloadXrayCore") {
    description = "Downloads AndroidLibXrayLite ($xrayVersion) and verifies its checksum."
    val target = xrayAar
    val version = xrayVersion
    val expected = xraySha256
    outputs.file(target)
    onlyIf { !target.exists() }
    doLast {
        target.parentFile.mkdirs()
        val url = "https://github.com/2dust/AndroidLibXrayLite/releases/download/$version/libv2ray.aar"
        val tmp = File(target.parentFile, target.name + ".part")
        URI(url).toURL().openStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
        val actual = MessageDigest.getInstance("SHA-256").digest(tmp.readBytes())
            .joinToString("") { "%02x".format(it) }
        if (!actual.equals(expected, ignoreCase = true)) {
            tmp.delete()
            throw GradleException("libv2ray.aar checksum mismatch: expected $expected, got $actual")
        }
        tmp.renameTo(target)
    }
}

tasks.named("preBuild") { dependsOn(downloadXrayCore) }

// ---- Signing -----------------------------------------------------------------------------------

val signingProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun signingValue(key: String, env: String): String? =
    signingProps.getProperty(key) ?: System.getenv(env)

val releaseStoreFile = signingValue("storeFile", "AETHER_KEYSTORE")?.let { rootProject.file(it) }

android {
    namespace = "dev.aether.vpn"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.aether.vpn"
        minSdk = 26
        targetSdk = 36
        versionCode = 22
        versionName = "2.0"
    }

    signingConfigs {
        if (releaseStoreFile?.exists() == true) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = signingValue("storePassword", "AETHER_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "AETHER_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "AETHER_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = true
        }
    }

    packaging {
        // Compress the (large) Go core inside the APK
        jniLibs.useLegacyPackaging = true
        // ARM only â€” also keeps x86 libraries out of the universal APK
        jniLibs.excludes += listOf("lib/x86/**", "lib/x86_64/**")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
        )
    }
}

dependencies {
    implementation(files(xrayAar))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.graphics.shapes)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.work.runtime)
    implementation(libs.reorderable)
    implementation(libs.snakeyaml.engine)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}
