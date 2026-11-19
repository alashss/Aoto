# Catatan perubahan

## 1.9.1

- **Widget lirik**: baris lirik tidak lagi hilang sesaat saat berganti baris (animasi ViewFlipper di widget dimatikan;
  teks diganti langsung). Pilihan "Animasi pergantian baris" dihapus dari Setelan.
- **Bagikan lagu & playlist dinonaktifkan sementara** lewat satu saklar (`AppConfig.SHARE_ENABLED`): tombol disembunyikan,
  tautan bagikan yang masuk diabaikan, dan filter tautan dihapus dari manifest. Bagikan lirik/rekap/streak sebagai gambar
  tetap aktif.

## 1.9.0

- **Streak**: kartu Beranda menampilkan progres menit hari ini dan peringatan saat streak hampir putus; ringkasan
  berisi jumlah hari terhitung minggu ini.
- **Widget streak** baru (2x2): api, angka, dan tujuh hari terakhir. Bisa dipasang dari lembar Streak.
- **Lirik**: tambahan sumber cadangan Lyrics.ovh untuk lirik biasa kalau sumber bersinkron tidak menemukan apa-apa.
- **Pemutaran**: mulai berbunyi lebih cepat (buffer awal 0,6 dtk, pengecekan URL memakai sambungan yang sudah
  dihangatkan, pemanasan sambungan ke lebih banyak host, pembersihan cache lama tidak lagi di thread utama).
- **Layar pembuka** tidak menahan layar lebih lama dari animasinya.
- **Repo**: dibersihkan untuk publik (dokumentasi, templat issue/PR, kebijakan keamanan, Dependabot); konfigurasi
  server memakai nama netral (`CLOUD_URL`, `CLOUD_KEY`).

> Pengguna GitHub Actions: nama secret konfigurasi server berubah menjadi `CLOUD_URL` dan `CLOUD_KEY` (lihat `docs/BUILD.md`).
