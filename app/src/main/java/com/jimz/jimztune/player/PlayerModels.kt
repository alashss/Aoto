package com.jimz.jimztune.player

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.jimz.jimztune.data.model.SearchItem
import com.jimz.jimztune.data.model.SearchType

data class PlayerUiState(
    val current: SearchItem? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val shuffle: Boolean = false,
    val repeat: Int = Player.REPEAT_MODE_OFF,
    val queue: List<SearchItem> = emptyList(),
    val queueIndex: Int = 0,
    val error: String? = null
)

private const val EXTRA_DURATION = "duration"
private const val EXTRA_ARTIST_ID = "artistId"
private const val EXTRA_ALBUM_ID = "albumId"

fun SearchItem.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id)
    .setUri("jimztune://song/$id")
    .setCustomCacheKey(id)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(subtitle.substringBefore(" • "))
            .setArtworkUri(thumbnailUrl?.let { Uri.parse(it.hiRes()) })
            .setExtras(
                Bundle().apply {
                    putString(EXTRA_DURATION, durationText)
                    putString(EXTRA_ARTIST_ID, artistId)
                    putString(EXTRA_ALBUM_ID, albumId)
                }
            )
            .build()
    )
    .build()

fun MediaItem.toSong(): SearchItem = SearchItem(
    id = mediaId,
    type = SearchType.SONG,
    title = mediaMetadata.title?.toString().orEmpty(),
    subtitle = mediaMetadata.artist?.toString().orEmpty(),
    thumbnailUrl = mediaMetadata.artworkUri?.toString(),
    durationText = mediaMetadata.extras?.getString(EXTRA_DURATION),
    artistId = mediaMetadata.extras?.getString(EXTRA_ARTIST_ID),
    albumId = mediaMetadata.extras?.getString(EXTRA_ALBUM_ID)
)

/** Memperbesar cover dari server Google (…=w226-h226 menjadi …=w544-h544). */
fun String.hiRes(): String = replace(Regex("=w\\d+-h\\d+"), "=w544-h544")

fun formatMs(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

fun parseDurationMs(text: String?): Long {
    if (text.isNullOrBlank()) return 0L
    if (!com.jimz.jimztune.data.model.looksLikeDuration(text)) return 0L
    val parts = text.trim().split(':', '.').mapNotNull { it.toLongOrNull() }
    if (parts.isEmpty()) return 0L
    return parts.fold(0L) { acc, v -> acc * 60 + v } * 1000
}
