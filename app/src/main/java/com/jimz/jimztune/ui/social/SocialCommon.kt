package com.jimz.jimztune.ui.social

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jimz.jimztune.ui.components.GlassIconButton
import com.jimz.jimztune.ui.ranking.RankAvatar
import com.jimz.jimztune.ui.theme.LocalCoverTheme
import com.jimz.jimztune.util.TimeUtil

val OnlineGreen = Color(0xFF3DDC84)

/** Kerangka halaman sosial: latar mengikuti tema sampul, tombol kembali, aksi di kanan. */
@Composable
fun SocialScaffold(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    val cover = LocalCoverTheme.current
    BackHandler(onBack = onBack)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(0f to cover.top, 0.5f to cover.mid, 1f to cover.bottom))
            .statusBarsPadding()
            .imePadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali", onClick = onBack)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        subtitle, style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.65f), maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            actions()
        }
        content()
    }
}

/** Foto profil dengan titik hijau kalau online. [online] null atau false = tanpa titik. */
@Composable
fun AvatarWithDot(url: String?, name: String, size: Dp, online: Boolean?, ring: Color? = null) {
    Box(modifier = Modifier.size(size)) {
        RankAvatar(url, name, size, ring = ring)
        if (online == true) {
            val dot = size * 0.26f
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(dot)
                    .clip(CircleShape)
                    .background(OnlineGreen)
                    .border(2.dp, Color(0xFF12163A), CircleShape)
            )
        }
    }
}

/** "baru saja", "5 menit lalu" dari waktu ISO server. */
fun agoText(iso: String?): String = TimeUtil.parse(iso)?.let { TimeUtil.ago(it) } ?: ""

/** Jam singkat untuk gelembung chat. */
fun hourText(iso: String?): String = TimeUtil.parse(iso)?.let { TimeUtil.formatHour(it) } ?: ""

fun parseHex(hex: String, fallback: Color = Color(0xFF6C93FF)): Color =
    try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        fallback
    }
