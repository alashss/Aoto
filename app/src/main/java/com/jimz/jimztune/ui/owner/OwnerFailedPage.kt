package com.jimz.jimztune.ui.owner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jimz.jimztune.ui.components.GroupCard
import com.jimz.jimztune.ui.components.PillTextButton
import com.jimz.jimztune.util.FailedSongs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Lagu yang gagal diputar di perangkat ini, dari yang paling baru. Membantu melihat pola masalah (kode error yang sama berulang). */
@Composable
internal fun FailedPage(onBack: () -> Unit) {
    var items by remember { mutableStateOf(FailedSongs.load()) }
    val fmt = remember { SimpleDateFormat("d MMM HH:mm", Locale("id", "ID")) }
    OwnerScaffold(title = "Lagu gagal diputar", subtitle = "Dicatat di perangkat ini, tidak dikirim ke mana pun", onBack = onBack) {
        if (items.isEmpty()) {
            Text(
                "Belum ada lagu yang gagal diputar.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )
            return@OwnerScaffold
        }
        // Ringkasan: kode error yang paling sering muncul.
        val top = items.groupBy { it.code }.mapValues { e -> e.value.sumOf { it.count } }.entries.sortedByDescending { it.value }.take(3)
        OwnerSection("Penyebab terbanyak")
        GroupCard {
            top.forEach { (code, n) ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(code.ifBlank { "Tidak diketahui" }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text("${n}x", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        OwnerSection("Terbaru")
        GroupCard {
            items.forEach { e ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(e.title.ifBlank { e.id }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${e.code.ifBlank { "Tidak diketahui" }} · ${e.count}x · ${fmt.format(Date(e.lastMs))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        PillTextButton("Hapus catatan", onClick = { FailedSongs.clear(); items = emptyList() }, primary = false)
    }
}
