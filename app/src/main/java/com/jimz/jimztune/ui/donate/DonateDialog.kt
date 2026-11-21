package com.jimz.jimztune.ui.donate

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.ui.components.JimzDialog
import com.jimz.jimztune.ui.components.PillTextButton
import com.jimz.jimztune.ui.theme.LocalCoverTheme
import com.jimz.jimztune.util.openUrl

/** Dialog donasi. Judul, pesan, dan tautannya datang dari server supaya bisa diganti tanpa rilis ulang. */
@Composable
fun DonateDialog(onDismiss: () -> Unit) {
    val donation = AppGraph.remote.config?.donation
    val context = LocalContext.current
    JimzDialog(
        onDismiss = onDismiss,
        icon = Icons.Rounded.Coffee,
        title = donation?.title ?: "Traktir kopi ☕",
        message = donation?.message?.ifBlank { null }
            ?: "Aoto gratis dan tanpa iklan. Kalau kamu suka, traktir kopi ya, biar semangat bikin update!",
        confirmLabel = null,
        dismissLabel = "Nanti dulu"
    ) {
        donation?.links.orEmpty().forEachIndexed { i, link ->
            if (link.url.substringBefore('?').lowercase().let { u -> listOf(".png", ".jpg", ".jpeg", ".webp").any { u.endsWith(it) } }) {
                // Tautan berupa gambar (mis. kode QRIS): ditampilkan langsung di dialog, bisa dipindai dari HP lain.
                androidx.compose.foundation.layout.Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    coil.compose.AsyncImage(
                        model = link.url,
                        contentDescription = link.label,
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        modifier = Modifier
                            .size(220.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .padding(10.dp)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(link.label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
                }
                Spacer(Modifier.height(10.dp))
                return@forEachIndexed
            }
            PillTextButton(
                text = link.label,
                onClick = { context.openUrl(link.url) },
                primary = i == 0,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Kartu di akhir Beranda. Tidak muncul kalau donasi dimatikan atau belum ada tautan. */
@Composable
fun DonateCard(onClick: () -> Unit) {
    val accent = LocalCoverTheme.current.accent
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .clip(shape)
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.24f), Color.White.copy(alpha = 0.05f))))
            .border(1.dp, accent.copy(alpha = 0.38f), shape)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(accent, lerp(accent, Color.White, 0.4f)))),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(12.dp))
            Icon(Icons.Rounded.Coffee, null, tint = Color(0xFF0B1030), modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Suka Aoto?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White)
            Text(
                "Traktir kopi biar developernya semangat",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.65f)
            )
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = accent)
    }
}
