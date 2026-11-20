package com.jimz.jimztune.ui.components

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.jimz.jimztune.data.model.SearchItem
import java.text.Collator
import java.util.Locale

/** Cara mengurutkan daftar lagu. Hanya mengubah tampilan dan urutan putar; urutan asli tidak diubah. */
enum class SortMode(val label: String) {
    ORIGINAL("Urutan ditambahkan"),
    NEWEST("Terbaru ditambahkan"),
    TITLE_AZ("Judul A–Z"),
    TITLE_ZA("Judul Z–A"),
    ARTIST_AZ("Artis A–Z"),
    DURATION_SHORT("Durasi terpendek"),
    DURATION_LONG("Durasi terpanjang");

    companion object {
        private const val PREFS = "jimztune_settings"
        fun load(c: Context, key: String): SortMode {
            val n = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("sort_$key", null)
            return entries.firstOrNull { it.name == n } ?: ORIGINAL
        }

        fun save(c: Context, key: String, m: SortMode) {
            c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("sort_$key", m.name).apply()
        }
    }
}

private fun seconds(s: SearchItem): Int {
    val t = s.durationText ?: return Int.MAX_VALUE
    val parts = t.split(':', '.').mapNotNull { it.trim().toIntOrNull() }
    if (parts.isEmpty()) return Int.MAX_VALUE
    return parts.fold(0) { acc, v -> acc * 60 + v }
}

fun sortSongs(songs: List<SearchItem>, mode: SortMode): List<SearchItem> {
    val col = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.PRIMARY }
    return when (mode) {
        SortMode.ORIGINAL -> songs
        SortMode.NEWEST -> songs.asReversed()
        SortMode.TITLE_AZ -> songs.sortedWith { a, b -> col.compare(a.title, b.title) }
        SortMode.TITLE_ZA -> songs.sortedWith { a, b -> col.compare(b.title, a.title) }
        SortMode.ARTIST_AZ -> songs.sortedWith { a, b -> col.compare(a.subtitle, b.subtitle).let { if (it != 0) it else col.compare(a.title, b.title) } }
        SortMode.DURATION_SHORT -> songs.sortedBy { seconds(it) }
        SortMode.DURATION_LONG -> songs.sortedByDescending { seconds(it).let { s -> if (s == Int.MAX_VALUE) -1 else s } }
    }
}

/** Tombol urut + menu pilihan. Pilihan diingat per daftar lewat [key]. */
@Composable
fun SortButton(mode: SortMode, onChange: (SortMode) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        CompactIconButton(Icons.Rounded.Sort, "Urutkan", onClick = { open = true }, active = mode != SortMode.ORIGINAL)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SortMode.entries.forEach { m ->
                DropdownMenuItem(
                    text = { Text(m.label) },
                    trailingIcon = { if (m == mode) Icon(Icons.Rounded.Check, contentDescription = null) },
                    onClick = {
                        open = false
                        onChange(m)
                    }
                )
            }
        }
    }
}

/** State urutan yang tersimpan per daftar. */
@Composable
fun rememberSortMode(key: String): Pair<SortMode, (SortMode) -> Unit> {
    val ctx = LocalContext.current
    var mode by remember(key) { mutableStateOf(SortMode.load(ctx, key)) }
    return mode to { m: SortMode ->
        mode = m
        SortMode.save(ctx, key, m)
    }
}
