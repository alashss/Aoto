import java.net.URI

import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Nilai khusus pemilik build (server, Google, alamat repo). Urutan: variabel lingkungan -> local.properties -> placeholder.
// Tidak ada satu pun nilai asli yang ditulis di repo ini.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun buildSetting(name: String, placeholder: String): String =
    System.getenv(name)?.takeIf { it.isNotBlank() }
        ?: localProps.getProperty(name)?.takeIf { it.isNotBlank() }
        ?: placeholder
// Alamat halaman web bagikan (lihat web/index.html dan docs/PANDUAN_BAGIKAN.md). Kosong = pakai tautan jimztune://p/KODE.
val shareBaseUrl = buildSetting("SHARE_BASE_URL", "")
val sharePath: String = try { URI(shareBaseUrl).path.orEmpty().ifBlank { "/" } } catch (_: Exception) { "/" }
val shareHost: String = try { URI(shareBaseUrl).host ?: "share.invalid" } catch (_: Exception) { "share.invalid" }
fun String.asBuildConfigString(): String = "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.jimz.jimztune"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jimz.jimztune"
        minSdk = 26
        targetSdk = 35
        versionCode = 13
        versionName = "1.9.2"

        buildConfigField("String", "CLOUD_URL", buildSetting("CLOUD_URL", "GANTI_CLOUD_URL").asBuildConfigString())
        buildConfigField("String", "CLOUD_KEY", buildSetting("CLOUD_KEY", "GANTI_CLOUD_KEY").asBuildConfigString())
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", buildSetting("GOOGLE_WEB_CLIENT_ID", "GANTI_WEB_CLIENT_ID").asBuildConfigString())
        buildConfigField("String", "SHARE_BASE_URL", shareBaseUrl.asBuildConfigString())
        manifestPlaceholders["shareHost"] = shareHost
        manifestPlaceholders["sharePath"] = sharePath
        buildConfigField("String", "SOURCE_URL", buildSetting("SOURCE_URL", "GANTI_SOURCE_URL").asBuildConfigString())
    }

    // Tanda tangan rilis. Kunci HARUS sama di setiap build, kalau tidak pembaruan akan ditolak Android
    // ("konflik tanda tangan"). Di GitHub Actions kunci diambil dari Secrets (lihat PANDUAN_TAHAP_5-7.md).
    val releaseKeystore = System.getenv("KEYSTORE_PATH")
    signingConfigs {
        if (!releaseKeystore.isNullOrBlank()) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Dimatikan: R8 tanpa aturan khusus bisa merusak NewPipeExtractor/Media3 (crash/gagal putar).
            // Pemakaian pribadi, jadi ukuran APK bukan masalah. Aturan cadangan ada di proguard-rules.pro.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Tanpa keystore (build lokal): pakai kunci debug agar langsung bisa dipasang.
            signingConfig = if (releaseKeystore.isNullOrBlank()) {
                signingConfigs.getByName("debug")
            } else {
                signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/DEPENDENCIES"
            // kuromoji-core dan kuromoji-ipadic sama-sama membawa berkas info ini.
            pickFirsts += setOf("META-INF/CONTRIBUTORS.md", "META-INF/LICENSE.md", "META-INF/NOTICE.md", "META-INF/README.md", "META-INF/LICENSE", "META-INF/NOTICE")
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    // Dekoder GIF / WebP animasi untuk foto profil & banner.
    implementation("io.coil-kt:coil-gif:2.7.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Login Google langsung di dalam aplikasi (tanpa browser).
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")
    implementation("com.github.teamnewpipe:newpipeextractor:v0.26.5")
    // Kamus morfologi Jepang (kanji -> bacaan kana) untuk romanisasi lirik. ICU membaca kanji ala Mandarin, jadi tidak cukup.
    implementation("com.atilika.kuromoji:kuromoji-ipadic:0.9.0")
    // NewPipeExtractor butuh versi "_nio" kalau minSdk < 33 (tanpa ini NewPipe gagal saat dipakai).
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
