# Catatan perubahan

## 2.6.0

- **Tema baru: Liquid Glass** (Setelan > Tampilan > Gaya). Lebih bening dari Kaca: lekuk cahaya tebal, bayangan dalam, tepi berpendar sian/magenta. Bar bawah ikut memburamkan isi di belakangnya.
- **Lebih mulus**: blur latar sungguhan tidak lagi merekam layar saat pemutar penuh terbuka, dan bisa dimatikan di Setelan > Personalisasi.
- **Bagikan lirik**: sheet kini bisa digulir (opsi bawah tidak lagi terpotong), ada tombol **Simpan** ke galeri (Pictures/Aoto), opsi Sampul tampil/sembunyi dan Tanda logo Aoto; pratinjau dirender ringan, hasil ekspor tetap ukuran penuh.
- **Pemutar**: geser kiri hanya sampai Lirik; Antrean dibuka lewat tab, jadi mudah kembali.
- **Widget dan Dynamic Island**: tombol sebelumnya/putar-jeda/berikutnya dirombak (lingkaran kaca, ikon bersudut membulat).
- **Setelan**: Audio digabung satu kartu, tinggi baris seragam, saklar blur latar, subjudul baru.

## 2.5.0

- **Atur urutan playlist**: tombol ⇅ di playlist membuka layar seret-untuk-memindah (plus tombol "ke paling atas"). Urutan tersimpan sebagai "Urutan saya".
- **Impor playlist dari CSV** (Exportify, TuneMyMusic, dll.) lewat "Tempel daftar" > "Pilih berkas CSV"; kolom judul/artis dikenali otomatis.
- **Halaman Cari** menampilkan rak "Terakhir diputar" dan "Paling sering".
- **Beranda**: chip suasana (Bahagia, Santai, Perjalanan, Semangat, Galau, Fokus) yang langsung memutar, kisi "Speed dial" 3 kolom dari lagu paling sering diputar (dilengkapi favorit), dan "Terakhir diputar" jadi "Lanjut dengar".
- **Poster lirik**: 7 font (tambah Ramping, Santai, Tulis) dan 9 warna teks; "Otomatis" kini condong ke warna sampul.
- **Dialog donasi** menampilkan tautan berupa gambar (mis. kode QRIS) langsung di dalam dialog.

## 2.4.0

- **Navbar melipat saat scroll ke bawah** dan muncul lagi saat scroll ke atas; mini player ikut turun dan menempel di dasar layar.
- **Setelan ditata ulang**: urutan baru Akun, Audio (kualitas + kontrol suara), Tampilan & fitur, Personalisasi, Pemutaran, Penyimpanan, Privasi & cadangan, Aplikasi. Baris "Penyimpanan" yang dobel digabung.
- **Panel Owner dikelompokkan**: Pantau, Konten, Sistem; ikon dobel diganti.
- **Koleksi**: label "Playlist saya" selalu tampil.

## 2.3.0

- **Pemutar**: geser kiri untuk Lirik, geser lagi untuk Antrean (kanan untuk kembali), dengan animasi antar halaman. Tarik ke bawah untuk menutup: halaman ikut turun dan mengecil, lalu kembali ke beranda. Animasi buka/tutup dari mini player juga ikut membesar/mengecil.
- **Bagikan lirik jadi poster**: ada sampul dan judul, pratinjau langsung, latar Gradasi/Polos/Buram/Kaca, format Kotak/Story, 4 font, tebal, rata teks, dan warna teks.
- **Pencarian lebih andal**: kartu hasil memakai kunci stabil dan buang duplikat; sampul kartu genre dimuat bergantian (maks. 2) dan disimpan di perangkat, tidak lagi menyaingi pencarian sungguhan.
- **Pemilih ikon aplikasi** ditata ulang (pratinjau ikon + nama).
- Dihapus: fitur "Jeda riwayat dengar" dan lencana VIP.

## 2.2.3

- Perbaikan build: `addOutline`/`clipPath` pada tema kaca, delegasi state pada pilihan font.

## 2.2.2

- **Bluetooth resume diperbaiki**: sebelumnya tidak akan pernah terpicu di Android 12+ karena butuh izin Bluetooth; sekarang memakai deteksi perangkat audio tanpa izin.
- **Animasi widget lirik tidak lagi terpotong** oleh update progres yang masuk di tengah animasi.
- **Ikon alternatif**: nama komponen dibuat tetap supaya tidak gagal pada varian build dengan applicationId berbeda.
- **Keamanan**: tautan hanya dibuka lewat https (http dulu diizinkan), dan tautan bagikan dari aplikasi lain diabaikan selama fitur bagikan dimatikan.

## 2.2.1

- **Keamanan**: pemutar hanya menerima pengendali tepercaya (sistem, Android Auto, Wear, Bluetooth, Assistant), aplikasi lain tidak bisa mengendalikan atau membaca lagu yang sedang diputar.
- **Keamanan**: berkas cadangan dan M3U dibatasi ukurannya, dan tipe data cadangan diperiksa agar berkas rusak/jahat tidak membuat aplikasi crash.
- **Keamanan**: workflow GitHub tidak menyimpan kredensial checkout, dan artefak debug dihapus otomatis setelah 7 hari.

## 2.2.0

- **Navbar kaca sungguhan** (Android 12+): isi layar di belakang navbar benar-benar diburamkan dan ditembus, lalu diberi kilap dan tepi bercahaya. Di Android lebih lama tampil sebagai kaca bening biasa.

## 2.1.0

- **Urutkan daftar lagu** (favorit, playlist, unduhan): ditambahkan, terbaru, judul A–Z/Z–A, artis, durasi. Pilihan diingat per daftar dan ikut menentukan urutan putar.
- **Tema Kaca dibuat ulang**: kilap, pantulan cahaya, pita cahaya diagonal, dan tepi bercahaya, bukan sekadar blur.

## 2.0.0

- **Ekspor/impor playlist M3U** (tautan YouTube / YouTube Music).
- **Lanjut otomatis saat Bluetooth tersambung** (opsional, hanya jika lagu baru dijeda dalam 10 menit).

## 1.9.9

- **Navbar bisa digeser**: tekan lalu geser kiri/kanan, indikator mengikuti jari (membesar, ada getar halus) dan tab berpindah saat dilewati.
- **Pilihan font** (Bawaan, Sans, Serif, Mono, Tulis) di Pengaturan > Personalisasi.

## 1.9.8

- **Ikon aplikasi alternatif** (Biru, Neon, Ruby, Hutan, Senja, Mono), dipilih di Pengaturan > Personalisasi.
- **Refresh rate tinggi** (120 Hz) opsional.
- **Jeda riwayat dengar**: lagu yang diputar tidak dicatat ke riwayat dan statistik.

## 1.9.7

- **Cadangkan & pulihkan pengaturan** (tema, gaya, widget, streak, riwayat) ke berkas JSON lokal; data akun dan token tidak ikut.
- **Blokir tangkapan layar** dan pratinjau Recents (opsional).
- **Ubin Pengaturan Cepat** untuk putar/jeda tanpa membuka aplikasi.

## 1.9.6

- **Widget lirik beranimasi lagi**: saat baris berganti, baris lama naik & memudar lalu baris baru naik dari bawah (seperti di Dynamic Island).
  Animasi digambar sebagai gambar bertahap, jadi tidak ada lagi baris kosong atau kedip.

## 1.9.5

- **Widget lirik**: area lirik sekarang digambar sebagai satu gambar (bukan TextView/ViewFlipper), jadi baris tidak lagi kosong atau
  berkedip saat berganti; ukuran huruf menyesuaikan otomatis.
- **NewPipe jadi sumber utama** pencarian audio: berjalan lebih dulu, klien langsung ikut berlomba sebagai cadangan setelah 2,5 detik
  (langsung jalan kalau NewPipe baru gagal).
- **Judul panjang bergeser pelan** (marquee) di pemutar, mini player, dan Dynamic Island, tidak lagi terpotong "...".

## 1.9.4

- **Perbaikan crash saat dibuka**: build tanpa `CLOUD_URL` / `CLOUD_KEY` yang dipasang di atas versi lama (yang masih menyimpan sesi login)
  membuat aplikasi crash. Permintaan ke server sekarang dilewati kalau server belum dikonfigurasi.

## 1.9.3

- Pembaruan widget streak dipindah ke thread terpisah dan aman dipanggil di proses yang baru dimulai.

## 1.9.2

- Setelan -> **Lisensi & kode sumber** membuka repo kode sumber (kewajiban GPL bagi penerima APK).
- Tambah `NOTICE.md` (pemberitahuan GPL) dan catatan nama/logo di README.

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
