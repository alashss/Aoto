# Susunan kode

Satu modul (`app`), paket dasar `com.jimz.jimztune`.

| Paket | Isi |
|---|---|
| `player/` | `PlaybackService` (Media3 ExoPlayer + sesi media), `ChunkedDataSource`, timer tidur, SponsorBlock, efek audio (`fx/`) |
| `data/stream/` | `StreamResolver`: mencari URL audio dari beberapa sumber sekaligus (tercepat menang), cache URL, prefetch |
| `data/search/` | Pencarian lagu/album/artis dari beberapa penyedia dengan cadangan |
| `data/lyrics/` | `LyricsRepository` mengambil lirik dari banyak sumber, memeriksa kecocokan, menyimpan di perangkat |
| `data/stats/` | Catatan waktu dengar, streak, peringkat |
| `data/cloud/`, `data/auth/`, `data/realtime/` | Klien server opsional: akun, sinkronisasi, pengumuman, peringkat |
| `widget/` | Widget lirik dan widget streak |
| `ui/` | Layar Compose (beranda, pencarian, pemutar, pustaka, profil, setelan) |

## Alur putar

1. UI memanggil `PlayerViewModel.play`, yang mulai `StreamResolver.prefetch` untuk lagu yang diketuk sambil menyiapkan antrean.
2. ExoPlayer meminta data lewat `ResolvingDataSource`; resolver memberi URL dari unduhan lokal, cache, atau pencarian baru.
3. Pencarian baru: beberapa klien YouTube berlomba, tiap URL dicek singkat (`Http.probe`); NewPipe menyusul belakangan;
   instance publik hanya dipakai kalau semuanya gagal.
4. `ChunkedDataSource` mengambil 4 MB pertama dengan permintaan ber-ujung agar cepat mulai; `CacheDataSource` menyimpan audio.
5. Begitu lagu berbunyi, lagu berikutnya di antrean di-prefetch. Watchdog dan percobaan ulang menangani macet.

## Streak

`Streak` menghitung hari berturut-turut dari `PlayLog` (minimal 60 detik per hari), dengan pemulihan satu hari terlewat
(maks. 2 per bulan). `PlayLog.touch()` memperbarui UI dan widget streak.
