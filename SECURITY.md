# Kebijakan keamanan

## Melaporkan celah

Kirim laporan **secara pribadi** lewat tab *Security -> Report a vulnerability* di repo ini (GitHub Private Vulnerability
Reporting). Jangan membuka issue publik untuk celah keamanan. Sertakan langkah memunculkan, dampak, dan versi aplikasi.
Kami berusaha menanggapi dalam 7 hari.

## Yang termasuk

- Kebocoran data pengguna lain, atau melewati pembatasan akun
- Eksekusi kode atau akses file lewat tautan (deep link / tautan bagikan)
- Kunci atau rahasia yang tidak sengaja ikut di repo

## Prinsip yang dipegang repo ini

- Tidak ada kunci, token, atau alamat server asli di repo. Nilai itu disuntikkan saat build dari `local.properties`
  atau GitHub Secrets (lihat [`docs/BUILD.md`](docs/BUILD.md)).
- Kunci yang ikut terbundel di APK adalah kunci publik klien; keamanan data bergantung pada aturan akses di sisi
  server, bukan pada kerahasiaan kunci itu.
- Keystore rilis (`*.jks`) tidak boleh di-commit.
