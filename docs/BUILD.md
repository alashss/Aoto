# Membangun Aoto

Prasyarat: JDK 17, Android SDK 35. Gradle wrapper dibuat otomatis oleh Android Studio.

## Lokal

1. Buka folder repo di Android Studio, tunggu sync, Run.
2. (Opsional) Aktifkan fitur akun: salin `local.properties.example` menjadi `local.properties`, isi nilainya.

| Nama | Isi |
|---|---|
| `CLOUD_URL` | Alamat dasar server aplikasi |
| `CLOUD_KEY` | Kunci **publik** klien. Jangan pernah kunci rahasia/administrator |
| `GOOGLE_WEB_CLIENT_ID` | OAuth Client ID bertipe *Web application* |
| `SOURCE_URL` | Alamat repo (tampil di Setelan); otomatis di GitHub Actions |
| `SHARE_BASE_URL` | (Opsional) alamat halaman web bagikan, lihat folder `web/` |

Urutan pembacaan: variabel lingkungan, lalu `local.properties`, lalu placeholder `GANTI_...` (fitur terkait dilewati).

## GitHub Actions

- **Build APK**: jalan tiap push ke `main`; APK debug ada di Artifacts. Tambahkan secret `CLOUD_URL`, `CLOUD_KEY`,
  `GOOGLE_WEB_CLIENT_ID` (Settings -> Secrets and variables -> Actions) kalau ingin fitur akun aktif.
- **Release APK**: push tag `v*`. Untuk APK bertanda tangan tambahkan secret `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`,
  `KEY_ALIAS`, `KEY_PASSWORD`. Kunci HARUS sama di setiap rilis, kalau tidak Android menolak pembaruan.
  **Jangan commit file keystore (`.jks`).**
- **Cek update NewPipeExtractor**: tiap Senin, membuka issue kalau ada versi baru.

## Halaman web bagikan (opsional)

`web/` adalah halaman statis untuk tautan bagikan playlist. Isi `CLOUD_URL`/`CLOUD_KEY` lewat variabel build di
hosting (lihat `web/vercel.json`) dan ganti sidik jari di `web/.well-known/assetlinks.json` dengan SHA-256 keystore rilismu.

## Menguji pemutaran

Setelan -> Uji pemutaran menjalankan pencarian lagu, pencarian sumber audio, dan pemutaran dari tiap sumber, lalu
menampilkan hasilnya. Jalankan setelah menaikkan versi NewPipeExtractor.
