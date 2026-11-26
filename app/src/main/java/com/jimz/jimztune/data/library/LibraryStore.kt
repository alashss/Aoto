package com.jimz.jimztune.data.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.data.model.SearchItem
import com.jimz.jimztune.data.model.SearchType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

data class Playlist(
    val id: String,
    val name: String,
    val songs: List<SearchItem>,
    /** Warna tema (ARGB). 0 = pakai warna aksen bawaan. */
    val color: Int = 0,
    /** Lokasi gambar sampul pilihan pengguna, atau null kalau pakai kolase cover lagu. */
    val coverPath: String? = null,
    /** Alamat gambar sampul di cloud (penyimpanan cloud). Dipakai kalau file lokal hilang, mis. setelah pasang ulang. */
    val coverUrl: String? = null
)

/** Favorit dan playlist buatan pengguna, disimpan sebagai satu file JSON di penyimpanan aplikasi. */
class LibraryStore(context: Context) {
    private val file = File(context.filesDir, "library.json")
    private val io = Executors.newSingleThreadExecutor()
    private val appContext = context.applicationContext
    private val coverDir = File(context.filesDir, "playlist_covers").apply { mkdirs() }

    var favorites by mutableStateOf<List<SearchItem>>(emptyList())
        private set
    var playlists by mutableStateOf<List<Playlist>>(emptyList())
        private set

    /** Playlist YouTube Music dari hasil pencarian yang di-"like". Hanya rujukan (judul + sampul), isinya dimuat saat dibuka. */
    var likedPlaylists by mutableStateOf<List<SearchItem>>(emptyList())
        private set

    init {
        load()
    }

    fun isFavorite(id: String): Boolean = favorites.any { it.id == id }

    fun toggleFavorite(song: SearchItem) {
        favorites = if (isFavorite(song.id)) favorites.filter { it.id != song.id } else listOf(song) + favorites
        save()
    }

    private fun String.plKey() = removePrefix("VL")

    fun isPlaylistLiked(id: String): Boolean = likedPlaylists.any { it.id.plKey() == id.plKey() }

    /** Menyukai / batal menyukai playlist dari pencarian. Mengembalikan status baru (true = disukai). */
    fun togglePlaylistLike(ref: SearchItem): Boolean {
        val liked = isPlaylistLiked(ref.id)
        likedPlaylists = if (liked) {
            likedPlaylists.filter { it.id.plKey() != ref.id.plKey() }
        } else {
            listOf(ref.copy(type = SearchType.PLAYLIST)) + likedPlaylists
        }
        save()
        return !liked
    }

    /** Playlist yang baru diubah / dipakai pindah ke urutan paling atas. */
    fun bump(id: String) {
        val p = playlists.firstOrNull { it.id == id } ?: return
        if (playlists.firstOrNull()?.id == id) return
        playlists = listOf(p) + playlists.filter { it.id != id }
        save()
    }

    fun playlist(id: String?): Playlist? = if (id == null) null else playlists.firstOrNull { it.id == id }

    fun createPlaylist(name: String, first: SearchItem? = null, color: Int = 0): Playlist {
        val p = Playlist(
            UUID.randomUUID().toString(),
            name.trim().ifEmpty { "Playlist baru" },
            listOfNotNull(first),
            color
        )
        playlists = listOf(p) + playlists
        save()
        return p
    }

    /** Membuat playlist baru berisi [songs] sekaligus (hasil impor dari layanan lain). */
    fun importPlaylist(name: String, songs: List<SearchItem>): Playlist {
        val p = Playlist(UUID.randomUUID().toString(), name.trim().ifEmpty { "Playlist impor" }, songs.distinctBy { it.id })
        playlists = listOf(p) + playlists
        save()
        return p
    }

    fun rename(id: String, name: String) {
        val clean = name.trim().ifEmpty { return }
        playlists = playlists.map { if (it.id == id) it.copy(name = clean) else it }
        save()
    }

    fun update(id: String, name: String, color: Int) {
        val clean = name.trim().ifEmpty { return }
        playlists = playlists.map { if (it.id == id) it.copy(name = clean, color = color) else it }
        save()
    }

    fun delete(id: String) {
        playlist(id)?.coverPath?.let { File(it).delete() }
        playlists = playlists.filter { it.id != id }
        save()
    }

    /** Menyimpan gambar pilihan pengguna sebagai sampul playlist. uri = null berarti hapus sampul. */
    fun setCover(id: String, uri: Uri?) {
        io.execute {
            try {
                val old = playlist(id)?.coverPath
                val newPath = if (uri == null) null else (saveCover(id, uri) ?: return@execute)
                // Gambar baru: alamat cloud yang lama tidak berlaku lagi sampai unggahan baru selesai.
                playlists = playlists.map { if (it.id == id) it.copy(coverPath = newPath, coverUrl = null) else it }
                old?.let { File(it).delete() }
                save()
                if (newPath != null) AppGraph.cloud.uploadPlaylistCover(id, File(newPath))
            } catch (_: Exception) {
            }
        }
    }

    /** Memperkecil (maks 1024 px) dan memotong persegi dari tengah, lalu menyimpannya sebagai JPEG. */
    private fun saveCover(id: String, uri: Uri): String? {
        val resolver = appContext.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2
        val decode = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decode) } ?: return null
        val side = minOf(bmp.width, bmp.height)
        val square = Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side)
        val out = File(coverDir, "${id}_${System.currentTimeMillis()}.jpg")
        out.outputStream().use { square.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        return out.absolutePath
    }

    /** Mengembalikan false kalau lagu sudah ada di playlist itu. */
    fun addToPlaylist(id: String, song: SearchItem): Boolean {
        val target = playlist(id) ?: return false
        if (target.songs.any { it.id == song.id }) return false
        playlists = playlists.map { if (it.id == id) it.copy(songs = it.songs + song) else it }
        bump(id)
        save()
        return true
    }

    /** Menyimpan urutan baru lagu di playlist. Lagu yang tidak ada di [ordered] (mis. baru ditambah) tetap dipertahankan di akhir. */
    fun reorderPlaylist(id: String, ordered: List<SearchItem>) {
        val cur = playlist(id) ?: return
        val ids = cur.songs.map { it.id }.toSet()
        val kept = ordered.filter { it.id in ids }.distinctBy { it.id }
        val rest = cur.songs.filter { s -> kept.none { it.id == s.id } }
        playlists = playlists.map { if (it.id == id) it.copy(songs = kept + rest) else it }
        bump(id)
        save()
    }

    /** Membuang lagu kembar (id sama) di playlist. Mengembalikan jumlah yang dibuang. */
    fun dedupePlaylist(id: String): Int {
        val p = playlist(id) ?: return 0
        val unique = p.songs.distinctBy { it.id }
        val removed = p.songs.size - unique.size
        if (removed > 0) {
            playlists = playlists.map { if (it.id == id) it.copy(songs = unique) else it }
            save()
        }
        return removed
    }

    /** Menambahkan lagu dari [sourceId] yang belum ada ke [targetId]; [sourceId] tidak diubah. Mengembalikan jumlah yang ditambah. */
    fun mergeInto(targetId: String, sourceId: String): Int {
        val t = playlist(targetId) ?: return 0
        val s = playlist(sourceId) ?: return 0
        if (targetId == sourceId) return 0
        val have = t.songs.map { it.id }.toSet()
        val add = s.songs.filter { it.id !in have }.distinctBy { it.id }
        if (add.isNotEmpty()) {
            playlists = playlists.map { if (it.id == targetId) it.copy(songs = it.songs + add) else it }
            bump(targetId)
            save()
        }
        return add.size
    }

    fun removeFromPlaylist(id: String, songId: String) {
        playlists = playlists.map { if (it.id == id) it.copy(songs = it.songs.filter { s -> s.id != songId }) else it }
        bump(id)
        save()
    }


    // ------------------------------------------------------------------ sinkronisasi cloud

    /** Isi koleksi dalam bentuk JSON untuk disimpan ke cloud (tanpa path file lokal). */
    fun exportCloud(): JSONObject = JSONObject()
        .put("favorites", JSONArray().also { a -> favorites.forEach { a.put(it.toJson()) } })
        .put("liked_playlists", JSONArray().also { a -> likedPlaylists.forEach { a.put(it.toJson()) } })
        .put("playlists", JSONArray().also { a ->
            playlists.forEach { p ->
                a.put(
                    JSONObject().put("id", p.id).put("name", p.name).put("color", p.color)
                        .put("cover_url", p.coverUrl ?: "")
                        .put("songs", JSONArray().also { s -> p.songs.forEach { s.put(it.toJson()) } })
                )
            }
        })

    /**
     * Menerapkan data dari cloud. merge = true: gabungkan dengan isi lokal (tidak ada yang hilang).
     * merge = false: cloud menang penuh (perangkat lain sudah mengubahnya).
     */
    fun applyCloud(root: JSONObject, merge: Boolean) {
        val cloudFav = root.optJSONArray("favorites").songs()
        val arr = root.optJSONArray("playlists") ?: JSONArray()
        val cloudPls = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.mapNotNull {
            try {
                Playlist(
                    id = it.getString("id"),
                    name = it.optString("name"),
                    songs = it.optJSONArray("songs").songs(),
                    color = it.optInt("color", 0),
                    coverPath = null,
                    coverUrl = it.optString("cover_url").ifEmpty { null }
                )
            } catch (_: Exception) {
                null
            }
        }
        val localPls = playlists
        val mergedPls = if (merge) {
            val result = cloudPls.map { c ->
                val l = localPls.firstOrNull { it.id == c.id }
                if (l == null) c else c.copy(
                    songs = (l.songs + c.songs).distinctBy { it.id },
                    coverPath = l.coverPath,
                    coverUrl = l.coverUrl ?: c.coverUrl
                )
            }
            result + localPls.filter { l -> cloudPls.none { it.id == l.id } }
        } else {
            cloudPls.map { c ->
                val l = localPls.firstOrNull { it.id == c.id }
                // File lokal dipertahankan kalau sampulnya memang sama dengan yang di cloud.
                if (l != null && l.coverUrl == c.coverUrl) c.copy(coverPath = l.coverPath) else c
            }
        }
        favorites = if (merge) (favorites + cloudFav).distinctBy { it.id } else cloudFav
        // Data cloud lama belum punya kunci ini: jangan sampai menghapus playlist yang sudah disukai di perangkat ini.
        if (root.has("liked_playlists")) {
            val cloudLiked = root.optJSONArray("liked_playlists").playlistRefs()
            likedPlaylists = if (merge) (likedPlaylists + cloudLiked).distinctBy { it.id.plKey() } else cloudLiked
        }
        playlists = mergedPls
        save()
    }

    fun setCoverUrl(id: String, url: String?) {
        playlists = playlists.map { if (it.id == id) it.copy(coverUrl = url) else it }
        save()
    }

    /** Menghapus semua isi koleksi (dipakai saat keluar akun). */
    fun clearAll() {
        playlists.forEach { p -> p.coverPath?.let { File(it).delete() } }
        favorites = emptyList()
        playlists = emptyList()
        likedPlaylists = emptyList()
        save()
    }

    private fun load() {
        try {
            if (!file.exists()) return
            val root = JSONObject(file.readText())
            favorites = root.optJSONArray("favorites").songs()
            likedPlaylists = root.optJSONArray("likedPlaylists").playlistRefs()
            val arr = root.optJSONArray("playlists") ?: JSONArray()
            playlists = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.map {
                val cover = it.optString("cover").takeIf { path -> path.isNotEmpty() && File(path).exists() }
                Playlist(
                    it.getString("id"),
                    it.optString("name"),
                    it.optJSONArray("songs").songs(),
                    it.optInt("color", 0),
                    cover,
                    it.optString("coverUrl").ifEmpty { null }
                )
            }
        } catch (_: Exception) {
            // File rusak: mulai dari kosong daripada crash.
        }
    }

    private fun JSONArray?.songs(): List<SearchItem> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optJSONObject(it) }.mapNotNull {
            try { it.toSong() } catch (_: Exception) { null }
        }
    }

    private fun JSONArray?.playlistRefs(): List<SearchItem> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optJSONObject(it) }.mapNotNull {
            try { it.toPlaylistRef() } catch (_: Exception) { null }
        }
    }

    private fun save() {
        val fav = favorites
        val pls = playlists
        val liked = likedPlaylists
        AppGraph.cloud.markDirty()
        io.execute {
            try {
                val root = JSONObject()
                    .put("favorites", JSONArray().also { a -> fav.forEach { a.put(it.toJson()) } })
                    .put("likedPlaylists", JSONArray().also { a -> liked.forEach { a.put(it.toJson()) } })
                    .put("playlists", JSONArray().also { a ->
                        pls.forEach { p ->
                            a.put(
                                JSONObject().put("id", p.id).put("name", p.name)
                                    .put("color", p.color).put("cover", p.coverPath ?: "")
                                    .put("coverUrl", p.coverUrl ?: "")
                                    .put("songs", JSONArray().also { s -> p.songs.forEach { s.put(it.toJson()) } })
                            )
                        }
                    })
                val tmp = File(file.parentFile, "library.json.tmp")
                tmp.writeText(root.toString())
                tmp.renameTo(file)
            } catch (_: Exception) {
            }
        }
    }
}
