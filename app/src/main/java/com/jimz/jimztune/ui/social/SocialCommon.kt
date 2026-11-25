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
import androidx.compose.runtime.LaunchedEffect
import com.jimz.jimztune.data.social.CosmeticsStore
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
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
    leading: (@Composable () -> Unit)? = null,
    plainBack: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val cover = LocalCoverTheme.current
    BackHandler(onBack = onBack)
    // Beberapa HP sudah mengecilkan jendela saat keyboard muncul; kalau ime dihitung lagi, isi layar terdorong dua kali.
    // Jadi tambahan jarak bawah hanya sebesar bagian keyboard yang memang masih menutupi kolom ini.
    val density = androidx.compose.ui.platform.LocalDensity.current
    val screenH = androidx.compose.ui.platform.LocalContext.current.resources.displayMetrics.heightPixels
    val imeBottom = WindowInsets.ime.getBottom(density)
    val navBottom = WindowInsets.navigationBars.getBottom(density)
    var colBottom by remember { mutableStateOf(0f) }
    val covered = if (imeBottom > 0 && colBottom > 0f) (colBottom - (screenH - imeBottom)).coerceIn(0f, imeBottom.toFloat()) else 0f
    val bottomPad = with(density) { (if (imeBottom > 0) covered else navBottom.toFloat()).toDp() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0E14))
            .statusBarsPadding()
            .onGloballyPositioned { colBottom = it.boundsInWindow().bottom }
            .padding(bottom = bottomPad)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (plainBack) androidx.compose.material3.Icon(
                Icons.AutoMirrored.Rounded.ArrowBack, "Kembali", tint = Color.White,
                modifier = Modifier.size(34.dp).clip(androidx.compose.foundation.shape.CircleShape).clickable(onClick = onBack).padding(4.dp)
            ) else GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali", onClick = onBack)
            Spacer(Modifier.width(if (plainBack) 8.dp else 14.dp))
            if (leading != null) { leading(); Spacer(Modifier.width(10.dp)) }
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
    if (userId != null) LaunchedEffect(userId) { CosmeticsStore.request(userId) }
    val framed = userId?.let { CosmeticsStore.map[it]?.frame } != null
    Box(Modifier.size(size)) {
        FramedAvatar(size, userId) { RankAvatar(url, name, size, ring = ring) }
        // Titik digambar SETELAH bingkai dan digeser keluar cincin, supaya tidak tertutup kosmetik.
        if (online == true) {
            val dot = size * (if (framed) 0.28f else 0.26f)
            val shift = if (framed) size * 0.14f else 0.dp
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = shift, y = shift)
                    .size(dot)
                    .clip(CircleShape)
                    .background(OnlineGreen)
                    .border(2.dp, Color(0xFF12163A), CircleShape)
            )
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
