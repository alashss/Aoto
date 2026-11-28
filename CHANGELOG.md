## 2.27.1
- "Lirik di layar kunci" kini ada di Pengaturan untuk semua pengguna (sebelumnya hanya owner).

## 2.27.0
- Centang pesan seperti WhatsApp: ✓ terkirim, ✓✓ sampai di HP teman, ✓✓ biru dibaca. Privasi → Chat → "Tanda dibaca" bisa dimatikan (kalau dimatikan, kamu juga tidak melihat tanda dibaca teman). Jalankan tahap42_centang.sql.
- Saran lagu playlist dirapikan: dipindah ke bawah daftar lagu, dipisah garis pembatas dan kartu tersendiri ("belum ada di playlist"), baris rapi dengan tombol + berbingkai.

## 2.26.0
- Notifikasi chat bisa dibalas langsung dari notifikasi (seperti WhatsApp) dan punya tombol "Tandai dibaca"; tampilan percakapan, balasanmu muncul di notifikasi.
- Status online teman lebih real-time: detak tiap 20 detik, jendela online 50 detik, dan offline seketika saat aplikasi ditutup / musik berhenti (jalankan tahap41_presence.sql).
- Baris status push di Pengaturan dihapus.

## 2.25.1
- Pengaturan → Tentang & bantuan: baris "Notifikasi chat (push)" menampilkan status pendaftaran dan bisa didaftarkan ulang dengan satu ketukan.

## 2.25.0
- Lirik: BetterLyrics sekarang sumber utama (lalu LrcLib dan lainnya); cache lirik lama dibuang agar urutan baru berlaku.
- Saran lagu di playlist (seperti Spotify): muncul saat playlist dibuka, mengikuti artis lagu yang terakhir ditambahkan (playlist kosong: dari artis yang sering kamu dengar). Tombol + menambah, ketuk memutar, ikon segarkan untuk saran lain.
- Pesan diagnosis "Push: ..." dihilangkan. google-services.json juga dibaca langsung oleh Gradle dari Secret sebagai cadangan.

## 2.24.1
- Diagnosis push: muncul pesan singkat ("Push: ...") yang menunjukkan status pendaftaran notifikasi (aktif / Firebase tidak ikut build / gagal daftar).

## 2.24.0
- Notifikasi chat real-time lewat FCM: pesan teman masuk seketika walau aplikasi ditutup (server mengirim push saat pesan terkirim). Pengecekan berkala tetap jadi cadangan.
- Token perangkat didaftarkan setelah login dan dihapus saat keluar akun. Tanpa google-services.json aplikasi tetap terbangun (tanpa push).

## 2.23.0
- Notifikasi pesan teman: muncul saat ada chat masuk (dicek tiap ±8 detik selama aplikasi hidup, dan tiap ±15 menit lewat WorkManager walau aplikasi ditutup); diketuk langsung membuka chat-nya.
- Ikon notifikasi sekarang lambang AOTO (cincin + huruf A) untuk semua notifikasi, termasuk pemutar musik.

## 2.22.0
- Pencarian lagu: rilis resmi dari artisnya (YouTube Music, urut kepopuleran) digabung dan dinaikkan ke atas untuk hampir semua lagu, tanpa duplikat.
- Intro Raja Iblis: kepala naga depan didesain ulang (surai api, kumis, tanduk beruas, sisik, mata menyala, rahang bergerak); api kini bola api besar dari mulut + gelombang kejut + bara, bukan memenuhi layar. Naga terbang tidak diubah.
- Gelembung Raja Iblis: isi dalam dirancang ulang (obsidian bersisik, urat lava, api di dasar, bingkai emas); panjang naga dibatasi (maks 150dp) sehingga tidak ikut memanjang.
- Plat nama: bingkai foto, tanduk & sayap tidak lagi terpotong batas plat.

## 2.21.2
- Intro Raja Iblis: lambang kepala naga kini menghadap LURUS ke layar (tanduk, mata menyala, taring) dan menyemburkan api ke arah penonton; api memancar dari mulut dan menjalar dari seluruh tepi layar sampai layar terasa terbakar.

## 2.21.1
- Intro profil Raja Iblis: lambang kepala naga bernapas api di lingkaran sihir muncul dulu, baru naga raksasa masuk (±11 detik).
- Gelembung Raja Iblis: naga lebih besar dan rebahan memanjang di atas gelembung, mendengkur, sesekali mendengus api kecil dan "z z z".
- Pelat nama Raja Iblis dirancang ulang: obsidian bersisik, retakan lava, sayap naga, lidah api, bingkai emas bergerigi, tanduk sudut.
- Pencarian: rilis resmi dari artisnya (YouTube Music) didahulukan dari unggahan label; kanal lirik/cover/karaoke diturunkan.

## 2.21.0
- Raja Iblis didesain ulang total: naga raksasa (badan bersisik, sayap bertulang, tanduk, kumis, napas api) masuk saat profil dibuka, berputar keliling layar ±9 detik sambil menyemburkan api, lalu melesat pergi.
- Bingkai foto Raja Iblis: naga kecil mengepak mengorbit foto; gelembung chat Raja Iblis: naga bertengger di atas gelembung, ekor berayun & sesekali menyembur api.
- Lencana OWNER dirancang ulang: pelat berbingkai emas, tanduk & api kecil, kilau menyusuri tepi, sayap naga mengepak.

## 2.20.1
- Pencarian lagu: kata kunci di JUDUL didahulukan dari yang cuma cocok nama artis; tab Lagu hanya berisi lagu; kartu artis hanya untuk artis terkenal (>=300 rb subscriber).
- Lirik: sumber tanpa metadata ditolak untuk judul pendek/umum dan yang tanpa waktu; cache lirik lama dibuang.

## 2.20.0
- Pencarian lagu: kalau ketikan cocok dengan nama artis, kartu artis muncul paling atas, diikuti lagu aslinya; lagu yang dibawakan artis itu didahulukan dari cover.
- Chat teman: geser pesan ke kanan untuk membalas (kartu kutipan ala WA). Balasan catatan tampil rapi sebagai kartu, tanpa teks aneh.
- Indikator mengetik real-time (hilang seketika saat kolom kosong / pesan terkirim); chat teman diperbarui tiap 1,5 detik.
- Server (tahap 38).

## 2.19.2
- Saat memilih lagu untuk catatan, kartu mini tidak muncul lagi dan antreanmu dipulihkan setelah selesai.
- Lagu & lirik hasil pencarian catatan diprefetch supaya mulai lebih cepat.

## 2.19.1
- Perbaikan build 2.19.0 (animasi titik mengetik).

## 2.19.0
- Efek legendary & mythic punya adegan pembuka saat profil dibuka (naga terbang Raja Iblis, komet emas, pemindai kode, badai es) lalu menghilang pelan.
- Chat teman: gelembung titik tiga saat teman mengetik (tidak ada di chat global); animasi pop saat pesan baru di chat teman & global.
- Balasan catatan tampil sebagai kartu kutipan (judul lagu + isi), tanpa kode mentah.
- Catatan musik langsung putar dari awal potongan tanpa menunggu.
- Server (tahap 37): tabel/fungsi "mengetik".

## 2.18.3
- Perbaikan build 2.18.2 (kotak playlist).

## 2.18.2
- Akun baru langsung diarahkan ke halaman "Lengkapi profilmu" (nama, username, bio, foto); sekali per akun, bisa dilewati.

## 2.18.1
- Keamanan: gambar profil/banner/sampul dari data orang lain hanya dimuat dari host tepercaya; izin yang tidak perlu dihapus; mode kunci layar tidak bisa dipaksa aplikasi lain.
- Server (tahap 36): semua fungsi tertutup untuk tamu kecuali yang memang publik, fungsi bantu internal tidak bisa dipanggil langsung, URL foto dibatasi ke penyimpanan sendiri / Google.

## 2.18.0
- Efek profil memenuhi seluruh halaman: kuat di awal, lalu menipis jadi partikel jarang. Efek baru "Badai Es".
- Foto profil & gambar lebih cepat (cache disk, ukuran decode, muat lebih dulu di chat global).
- Foto lagu di catatan tidak lagi punya ruang kosong.
- Kartu mini tidak sempat muncul saat melihat catatan; lagu catatan mulai langsung dari potongan.
- Grid kotak dirapikan, plus mode kotak untuk playlist.
- Pencarian dan lirik lebih cepat (batas tunggu lebih pendek, lirik disiapkan lebih awal, hasil pencarian diingat).

## 2.17.3
- Lagu mulai lebih cepat: NewPipe tanpa uji awal, cadangan tidak menunggu lama, penyangga awal lebih kecil, dan alamat audio disiapkan lebih dulu untuk hasil pencarian, beranda, dan catatan teman.

## 2.17.2
- Kosmetik: pratinjau tidak lagi membengkak menutupi layar saat efek dipilih (tombol jadi bisa ditekan lagi).
- Catatan teman: lagu kita dijeda lalu dilanjutkan persis seperti semula setelah keluar; kartu mini tidak tersisa.
- Isi playlist: semua aksi (unduh, urutkan, tampilan, tambah, atur urutan, bagikan) digabung di satu tombol ⋮.

## 2.17.1
- Perbaikan build 2.17.0.
- Tautan bagikan playlist (pencarian / disukai) kini tautan Aoto; tombol bagikan di playlist disukai.
- Favorit, Riwayat, Unduhan: pilihan tampilan daftar atau kotak.
- Playlist yang baru diubah / dibuka pindah ke atas.
- Beranda: kartu lagu baru, daftar dua baris, lebih banyak lagu.

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
