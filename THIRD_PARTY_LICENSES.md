# Lisensi pihak ketiga

Aoto dirilis di bawah **GNU General Public License v3.0 atau lebih baru** (lihat [`LICENSE`](LICENSE)).
Hal itu diwajibkan oleh NewPipeExtractor (GPL-3.0-or-later). Semua pustaka lain di bawah ini kompatibel dengan GPL v3.

## Pustaka yang dipakai langsung (`app/build.gradle.kts`)

| Pustaka | Lisensi |
|---|---|
| [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) `v0.26.5` | GPL-3.0-or-later |
| AndroidX (core-ktx, core-splashscreen, lifecycle, activity-compose, navigation-compose) | Apache-2.0 |
| Jetpack Compose (BOM 2024.10.01: ui, material3, material-icons-extended) | Apache-2.0 |
| AndroidX Media3 (exoplayer, session) 1.4.1 | Apache-2.0 |
| AndroidX Credentials + Google Identity (`googleid`) | Apache-2.0 (kode); layanan Google Play memakai ketentuan Google sendiri |
| kotlinx-coroutines | Apache-2.0 |
| OkHttp 4.12.0 | Apache-2.0 |
| Coil 2.7.0 (compose, gif) | Apache-2.0 |
| desugar_jdk_libs_nio | GPL-2.0 dengan Classpath Exception (turunan OpenJDK) |

Pustaka bawaan NewPipeExtractor (mis. jsoup, Rhino, nanojson, protobuf) ikut terbawa dengan lisensinya masing-masing
(MIT, MPL-2.0, Apache-2.0, BSD).

> Daftar ini ditulis dari berkas build, bukan hasil pemindaian otomatis. Sebelum rilis besar, cek ulang dengan
> `gradle :app:dependencies` atau plugin laporan lisensi (mis. `com.jaredsburrows.license`).

## Aset

- `aoto_logo`, `splash_icon`: milik pemilik proyek.
- `ic_google.xml`: logo "G" Google untuk tombol masuk, dipakai sesuai
  [pedoman merek Google](https://developers.google.com/identity/branding-guidelines). Hak ciptanya milik Google.

## Layanan pihak ketiga yang diakses aplikasi

Aplikasi memanggil layanan berikut lewat antarmuka tidak resmi/publik. Mereka punya syarat layanan sendiri, terpisah
dari lisensi kode ini: YouTube / YouTube Music, Piped dan Invidious (instance publik), LrcLib, NetEase, KuGou, SimpMusic,
BetterLyrics, Lyrics.ovh, Spotify (tautan sematan untuk impor playlist), Deezer, Google Translate, dan server milik pengelola aplikasi.
