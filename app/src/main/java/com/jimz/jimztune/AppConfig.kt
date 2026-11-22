package com.jimz.jimztune

/**
 * Pengaturan yang berbeda untuk tiap orang yang membangun aplikasi ini. Nilainya TIDAK ditulis di kode,
 * tapi disuntikkan saat build (lihat app/build.gradle.kts) dari:
 *   1. variabel lingkungan (GitHub Actions: Settings -> Secrets and variables -> Actions), atau
 *   2. file local.properties di komputermu (tidak ikut git; contoh: local.properties.example).
 *
 *  - CLOUD_URL        = alamat dasar server (tanpa garis miring di akhir)
 *  - CLOUD_KEY   = kunci PUBLIK klien. JANGAN PERNAH memakai kunci rahasia/administrator server.
 *  - GOOGLE_WEB_CLIENT_ID = Client ID bertipe "Web application" dari Google Cloud
 *  - SOURCE_URL          = alamat repo kode sumber (opsional, tidak lagi ditampilkan di Setelan)
 *
 * Selama masih berisi "GANTI_", fitur yang butuh server / Google dilewati dan aplikasi tetap jalan normal.
 */
object AppConfig {
    val CLOUD_URL: String get() = BuildConfig.CLOUD_URL
    val CLOUD_KEY: String get() = BuildConfig.CLOUD_KEY
    val GOOGLE_WEB_CLIENT_ID: String get() = BuildConfig.GOOGLE_WEB_CLIENT_ID
    val SOURCE_URL: String get() = BuildConfig.SOURCE_URL

    /**
     * Saklar tunggal fitur bagikan lagu & playlist (tautan, menu, dan pembuka tautan). Aktif. Ubah ke false untuk mematikan:
     * tombolnya disembunyikan dan tautan bagikan yang masuk diabaikan.
     * (Bagikan lirik / rekap / streak sebagai gambar tidak termasuk dan tetap aktif.)
     */
    const val SHARE_ENABLED: Boolean = true
    /** Alamat halaman web bagikan (opsional). Kosong = tautan jimztune://p/KODE. */
    val SHARE_BASE_URL: String get() = BuildConfig.SHARE_BASE_URL
}
