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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.runtime.remember
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
            .background(Color(0xFF0B0E14))
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

/** Foto profil dengan titik hijau kalau online dan bingkai kosmetik (kalau [userId] diisi). */
@Composable
fun AvatarWithDot(url: String?, name: String, size: Dp, online: Boolean?, ring: Color? = null, userId: String? = null) {
    FramedAvatar(size, userId) {
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
}

/** Lembar dari bawah (gaya Instagram): latar gelap meredup, tarikan kecil di atas, isi di dalam. */
@Composable
fun BottomSheetLite(visible: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.fadeOut()
    ) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))
                .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, onClick = onDismiss)
        )
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        androidx.compose.animation.AnimatedVisibility(
            visible = visible,
            enter = androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(280)) { it } + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutVertically(androidx.compose.animation.core.tween(220)) { it } + androidx.compose.animation.fadeOut()
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(Color(0xFF1B1F33))
                    .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {}
                    .padding(horizontal = 16.dp).padding(top = 10.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(Modifier.width(40.dp).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.3f)))
                Spacer(Modifier.height(12.dp))
                content()
            }
        }
    }
}

/** Warna gelembung catatan yang bisa dipilih. */
val NoteColorPages = listOf(
    listOf("#A78BFF", "#7B3FFF", "#4300C2", "#FF8FF5", "#D100C4", "#8A0083"),
    listOf("#FF7A90", "#FF3D5E", "#B3123A", "#FFB199", "#FF7043", "#C2410C"),
    listOf("#8EDBFF", "#4FC3F7", "#1E88E5", "#2DD4BF", "#0F766E", "#1E3A8A"),
    listOf("#B9F6CA", "#3DDC84", "#15803D", "#FFE082", "#FFB300", "#A16207"),
    listOf("#F5F5F5", "#B0BEC5", "#607D8B", "#37474F", "#1F2937", "#000000")
)
val NoteColors = NoteColorPages.flatten()

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
