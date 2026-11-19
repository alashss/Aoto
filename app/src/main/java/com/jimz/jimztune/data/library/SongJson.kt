package com.jimz.jimztune.data.library

import com.jimz.jimztune.data.model.SearchItem
import com.jimz.jimztune.data.model.SearchType
import org.json.JSONObject

fun SearchItem.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("title", title)
    .put("subtitle", subtitle)
    .put("thumb", thumbnailUrl ?: "")
    .put("dur", durationText ?: "")
    .put("aid", artistId ?: "")
    .put("alb", albumId ?: "")

fun JSONObject.toSong(): SearchItem = SearchItem(
    id = getString("id"),
    type = SearchType.SONG,
    title = optString("title"),
    subtitle = optString("subtitle"),
    thumbnailUrl = optString("thumb").ifEmpty { null },
    durationText = optString("dur").ifEmpty { null },
    artistId = optString("aid").ifEmpty { null },
    albumId = optString("alb").ifEmpty { null }
)

/** Rujukan playlist dari pencarian (judul + sampul saja). */
fun JSONObject.toPlaylistRef(): SearchItem = SearchItem(
    id = getString("id"),
    type = SearchType.PLAYLIST,
    title = optString("title"),
    subtitle = optString("subtitle"),
    thumbnailUrl = optString("thumb").ifEmpty { null }
)
