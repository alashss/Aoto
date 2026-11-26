## 2.17.0
- Lirik di layar kunci asli: saklar di Pengaturan + izin tampil di atas aplikasi lain; muncul saat layar dinyalakan.
- Kosmetik Raja Iblis (bingkai, plat, efek, gelembung) khusus owner; lencana owner bertema iblis dengan sayap.
- Catatan orang lain tidak lagi menampilkan mini player; sentuhan tidak lagi tembus ke layar belakang.
- Kosmetik profil otomatis dicoba ulang bila gagal dimuat.

# Catatan perubahan

## 2.16.0
- Mode Lirik baru (ikon kutip di pemutar atau Pengaturan): lirik layar penuh, potret atau lanskap.
- Lirik catatan IG memakai komponen halaman lirik (isi per kata).
- Kosmetik: +42 item baru, jenis baru Gelembung chat (DM dan Chat Global), banyak yang bergerak; dikelompokkan per jenis lalu rarity.
- Profil orang: efek kosmetik punya animasi masuk; titik online tidak lagi tertutup bingkai.
- Profil sendiri punya dua tampilan: Saya dan Publik. Butuh SQL tahap 33.

## 2.15.0
- Layar kunci: kotak hitam di atas lirik hilang (pakai topeng pudar), lirik terisi per kata seperti halaman lirik, latar sampul kabur, kartu pemutar kini Liquid Glass (lensa pembias latar, tepi bercahaya, kilap menyapu).
- Catatan ala Instagram: potongan lagu 4 baris + lirik beranimasi per kata, lagu diputar dari awal potongan dan diulang, catatan sendiri juga beranimasi ("Tinggalkan catatan baru" / "Hapus catatan"), catatan berlaku 24 jam. Butuh SQL tahap 32.
- Chat (DM) bergaya Instagram: header dengan foto, gelembung gradasi ungu-biru dan abu gelap, foto lawan bicara di sisi gelembung, penanda waktu, kolom ketik berbentuk pil.

## 2.14.2
- Chat Global: tab Pesan/Teman dihapus, bug layar terdorong saat mengetik diperbaiki (keyboard dihitung dua kali).
- Catatan ala Instagram: lagu langsung terdengar saat catatan dibuka, lirik muncul beranimasi; pemilih lagu punya pratinjau suara.
- Mode Kunci Layar beranimasi; tombolnya dipindah ke header pemutar agar tidak bentrok dengan pengaturan lirik.
- Elemen peringkat di Beranda dihapus.

## 2.14.0
- Mode Kunci Layar: tampilan ala layar kunci (jam besar, lirik berjalan, kartu pemutar dengan sebelumnya/jeda/berikutnya), layar tetap menyala. Buka dari ikon gembok di panel lirik atau Pengaturan.
- Chat Global dirombak (kartu pesan, peringatan, pesan baru, balas, penghitung huruf, tab Global/Pesan/Teman).
- Tampilan sosial memakai latar gelap polos ala Instagram; catatan lebih besar; pemilih lagu punya tab Untuk Anda / Sedang tren.
- Animasi peringkat kini hanya di dalam kotak, dan kotaknya juga tampil di Beranda.

## 2.13.1
- Catatan gaya Instagram: tombol lagu (cari lagu), pilih satu baris lirik, editor warna terpisah (Terapkan/Hapus), ketuk lagu di catatan teman untuk memutar.
- Pemilihan pengguna di panel owner (Kosmetik) berupa daftar yang bisa dicentang.
- Lagu yang butuh login YouTube otomatis dicarikan unggahan lain.
- Chat global dan Pesan berdesain gelembung berkelompok; daftar teman dipisah Online/Offline.

## 2.13.0
- Dihapus: lirik buatan AI.
- Pesan, Teman, Chat global didesain ulang bergaya Instagram (catatan berwarna + lagu, balas lewat lembar bawah, gelembung gradasi, tab meluncur).
- Kosmetik profil: bingkai foto, plat nama, efek profil (termasuk set emas). Diberikan owner atau terbuka di level tertentu.
- Navbar: tombol garis tiga beranimasi, menu bisa digeser seperti navbar bawah, navbar tidak turun saat tidak ada lagu.
- Privasi profil didesain ulang; panel owner: Kosmetik dan Ringkasan sosial.

## 2.12.0
- Gambar di halaman Cari dan daftar lagu selalu tampil: cadangan thumbnail otomatis + placeholder, bukan kotak kosong.
- Playback: error IO berulang kini membuang cache lagu itu dan pindah ke NewPipe; percobaan ulang ditambah.
- Lirik: pencocokan lebih ketat (sumber tanpa verifikasi diberi skor rendah), cache lirik lama dibuang.
- Lirik AI sebagai pilihan terakhir (butuh Edge Function ai-lyrics).
- Streak: api berdenyut, angka menghitung naik, progres beranimasi.
- Peringkat: latar animasi berbeda untuk Mendengar dan Donasi.
- Detail playlist: simpan & bagikan digabung ke satu menu; tombol ikon punya animasi tekan.

## 2.11.0
- Sosial: ketuk nama di peringkat untuk membuka profil orang lain (status online, lagu yang sedang didengar, statistik, lagu favorit).
- Teman: permintaan teman, daftar teman, cari pengguna, blokir.
- Pesan: chat pribadi gaya Instagram dengan catatan 24 jam; chat global.
- Privasi profil: pilih siapa yang bisa melihat & bagian yang disembunyikan.
- Navbar baru: tombol tiga garis, menu terbuka ke atas (ada mini player) atau ke samping.
- Setelan dipecah jadi menu dan sub-halaman.
- Panel owner: moderasi chat global, kata terlarang, mode lambat, bisu.
- Halaman profil web dipercantik.

## 2.10.0

- **Panel Owner**: pengumuman terjadwal (pilih hari, jam mulai/selesai, tanggal mulai, dan "muncul lagi tiap hari"), pesan ke satu pengguna lewat username, tombol "Kirim tes ke akunku", salin pengumuman lama ke formulir, dan ringkasan donasi (hari ini, bulan ini, total).

## 2.9.2

- **Bagikan lagu & playlist diaktifkan kembali.** Tautan `aoto://p/KODE` dan `https://domainmu/p/KODE` kini benar-benar membuka Aoto (sebelumnya manifest tidak punya filter tautan, jadi tombol "Buka di Aoto" tidak berfungsi).
- Halaman web memberi pesan jelas kalau `CLOUD_URL` / `CLOUD_KEY` belum diisi di Vercel.

## 2.9.1

- **Notifikasi donasi** sekarang punya jalur cadangan: selain realtime, owner memeriksa donasi baru tiap ±15 detik selama Aoto berjalan.
- **Profil publik** dipindah ke kartu Akun di Setelan (langsung di bawah "Sinkronkan sekarang").
- **Setelan dirapikan**: pilihan Ikon aplikasi dan Font dihapus. Font kembali ke bawaan.

## 2.9.0

- **Donasi masuk otomatis (owner)**: webhook Trakteer, Saweria, atau umum mencatat donasi ke server, dan HP owner langsung menerima notifikasi "Donasi masuk" lewat koneksi realtime. Donasi ganda dicegah, dan pesan berisi `@username` otomatis menautkan donasi ke akun Aoto itu.
- **Profil publik**: Setelan > Privasi > "Halaman profil publik" (mati secara bawaan). Level, total dengar, dan 5 lagu teratas bisa dibuka lewat link web tanpa aplikasi.

## 2.8.0

- **Playlist cerdas** di Koleksi: "Baru kamu dengar", "Favorit bulan ini", dan "Lama nggak diputar" (favorit/isi playlist yang belum diputar 45 hari). Dibuat otomatis dari riwayat putar.
- **Hapus lagu kembar** dan **Gabungkan playlist** di halaman playlist.
- **Cari lewat lirik**: chip di halaman Cari. Ketik potongan lirik, hasilnya dicocokkan ke lagu yang bisa diputar.
- **Kartu "Sedang kuputar"**: menu lagu > Bagikan kartu lagu. Format Kotak atau Story, simpan ke galeri atau bagikan.
- **Tema ikut jam** dan **Mode hemat baterai** di Setelan > Personalisasi.
- **Getaran halus** saat pindah halaman pemutar dan saat menutupnya.
- **Panel Owner**: halaman "Lagu gagal diputar" (dicatat lokal, dengan ringkasan penyebab terbanyak).

## 2.7.0

- **Mode lirik**: pilih Daftar (menggulir seperti biasa) atau **Fokus** (satu baris besar di tengah, satu baris sebelumnya dan dua sesudahnya meredup, berganti dengan geser halus). Karaoke per kata, terjemahan, dan romanisasi tetap jalan di mode Fokus. Lirik tanpa waktu otomatis memakai Daftar.
- **Warna isian lirik**: Putih, Aksen (mengikuti sampul), Kuning, Mint, atau Pink untuk baris yang sedang dinyanyikan.

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
