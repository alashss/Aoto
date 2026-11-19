# Aoto

[![Build](https://github.com/USERNAME/REPO/actions/workflows/build.yml/badge.svg)](../../actions/workflows/build.yml)
[![Lisensi: GPL v3+](https://img.shields.io/badge/lisensi-GPL--3.0--or--later-blue.svg)](LICENSE)
![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84.svg)
![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF.svg)

Pemutar musik Android tanpa iklan, dibuat dengan Kotlin dan Jetpack Compose. Audio, pencarian, dan lirik diambil dari
sumber publik.

> **Penafian.** Aoto proyek hobi/pembelajaran dan **tidak berafiliasi, didukung, atau disponsori** oleh YouTube,
> Google, Spotify, Deezer, atau layanan musik mana pun. Aplikasi mengakses layanan itu lewat antarmuka tidak resmi,
> yang bisa melanggar syarat layanan mereka dan bisa berhenti bekerja kapan saja. Kamu bertanggung jawab atas cara
> memakainya. Hormati hak cipta musik yang kamu putar.

## Fitur

- Pencarian lagu, album, playlist, dan artis, dengan saran dan riwayat
- Pemutar dengan lirik tersinkron per kata/baris (+ terjemahan dan romanisasi), enam sumber lirik yang dicocokkan
  otomatis, antrean yang bisa diatur, timer tidur, efek suara
- Mulai putar cepat: pencarian sumber audio paralel, pemanasan sambungan, prefetch lagu berikutnya, cache audio
- Unduhan offline, playlist, favorit, impor playlist dari tautan (bagikan lagu/playlist dinonaktifkan sementara)
- **Streak** harian dengan api bertingkat, pemulihan, dan rekap; **widget** lirik dan streak di layar utama
- Akun, profil, sinkronisasi pustaka, waktu mendengar, dan peringkat (opsional, butuh server sendiri)

## Membangun

Butuh JDK 17 dan Android SDK 35. Buka folder ini di Android Studio, tunggu sync, lalu Run.
Tanpa konfigurasi tambahan aplikasi tetap terbangun; fitur akun, sinkronisasi, dan peringkat nonaktif.
Panduan lengkap (konfigurasi, CI, rilis bertanda tangan): [`docs/BUILD.md`](docs/BUILD.md).

## Dokumentasi

- [`docs/BUILD.md`](docs/BUILD.md): membangun, konfigurasi, GitHub Actions, rilis
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md): susunan kode dan alur pemutaran
- [`CONTRIBUTING.md`](CONTRIBUTING.md), [`SECURITY.md`](SECURITY.md), [`CHANGELOG.md`](CHANGELOG.md)

## Privasi

Jika fitur akun diaktifkan, aplikasi menyimpan di server milik pengelola: profil (nama, username, foto), pustaka yang
disinkronkan, riwayat putar, waktu mendengar harian, dan status online. Data ini dipakai untuk peringkat dan panel
pengelola. Tanpa konfigurasi server, semua data hanya ada di perangkatmu. Siapa pun yang menjalankan instance sendiri
bertanggung jawab atas data penggunanya dan wajib memberi tahu mereka.

## Lisensi

[GNU General Public License v3.0 atau lebih baru](LICENSE). Aoto memakai
[NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) (GPL-3.0-or-later), sehingga seluruh aplikasi ikut
GPL. Membagikan APK hasil build wajib menyertakan lisensi dan menyediakan kode sumbernya. Pustaka dan aset lain:
[`THIRD_PARTY_LICENSES.md`](THIRD_PARTY_LICENSES.md).
