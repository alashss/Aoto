package com.jimz.jimztune.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jimz.jimztune.ui.theme.AppTheme
import com.jimz.jimztune.ui.theme.LocalBackdrop
import com.jimz.jimztune.ui.theme.LocalCoverTheme
import com.jimz.jimztune.ui.theme.UiStyle
import com.jimz.jimztune.ui.theme.backdropSupported
import com.jimz.jimztune.ui.theme.cardSurface
import com.jimz.jimztune.ui.theme.glassBackdrop
import com.jimz.jimztune.ui.theme.isGlassy

/** Satu menu di navbar. [badge] > 0 menampilkan angka kecil (mis. pesan belum dibaca). */
data class NavEntry(
    val icon: ImageVector,
    val label: String,
    val selected: Boolean = false,
    val badge: Int = 0,
    /** Kalau tidak kosong, mengetuk entri ini membuka submenu (dengan tombol kembali). */
    val children: List<NavEntry> = emptyList(),
    val onClick: () -> Unit
)

val NavButtonSize = 60.dp

/** Latar kaca yang sama dengan navbar lama: ikut warna sampul lagu, blur kalau mode Kaca. */
@Composable
private fun Modifier.navGlass(shape: Shape): Modifier {
    val colors = AppTheme.colors
    val accent = LocalCoverTheme.current.accent
    val top by animateColorAsState(lerp(colors.surfaceHigh, accent, 0.12f).copy(alpha = 0.95f), tween(600), label = "navTop")
    val bottom by animateColorAsState(lerp(colors.surface, accent, 0.08f).copy(alpha = 0.97f), tween(600), label = "navBottom")
    val glassy = AppTheme.uiStyle.isGlassy && backdropSupported && LocalBackdrop.current != null
    return this
        .then(if (glassy) Modifier.glassBackdrop(shape, if (AppTheme.uiStyle == UiStyle.LIQUID) 14.dp else 22.dp) else Modifier)
        .background(
            Brush.verticalGradient(
                listOf(
                    if (glassy) top.copy(alpha = 0.30f) else top,
                    if (glassy) bottom.copy(alpha = 0.40f) else bottom
                )
            ),
            shape
        )
        .cardSurface(shape)
        .clip(shape)
}

@Composable
private fun CountBadge(n: Int, modifier: Modifier = Modifier) {
    if (n <= 0) return
    Box(
        modifier = modifier.clip(CircleShape).background(Color(0xFFFF4D6D)).widthIn(min = 16.dp).padding(horizontal = 4.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(if (n > 9) "9+" else n.toString(), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black)
    }
}

/** Tombol bulat kaca bergambar tiga garis; berubah jadi silang saat menu terbuka. */
@Composable
fun NavMenuButton(open: Boolean, badge: Int, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    val accent = LocalCoverTheme.current.accent
    Box(
        modifier = modifier
            .size(NavButtonSize)
            .navGlass(CircleShape)
            .border(0.5.dp, accent.copy(alpha = 0.35f), CircleShape)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onToggle()
            },
        contentAlignment = Alignment.Center
    ) {
        val p by animateFloatAsState(if (open) 1f else 0f, spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMediumLow), label = "burger")
        Canvas(Modifier.size(26.dp).graphicsLayer { rotationZ = 90f * p }) {
            val w = size.width
            val stroke = 2.6.dp.toPx()
            val gap = 7.dp.toPx()
            val cy = size.height / 2f
            val x0 = w * 0.12f
            val x1 = w * 0.88f
            // atas & bawah menyatu ke tengah lalu berputar jadi silang, tengah memudar
            rotate(45f * p, Offset(w / 2f, cy)) {
                drawLine(Color.White, Offset(x0, cy - gap * (1f - p)), Offset(x1, cy - gap * (1f - p)), stroke, StrokeCap.Round)
            }
            rotate(-45f * p, Offset(w / 2f, cy)) {
                drawLine(Color.White, Offset(x0, cy + gap * (1f - p)), Offset(x1, cy + gap * (1f - p)), stroke, StrokeCap.Round)
            }
            drawLine(Color.White.copy(alpha = 1f - p), Offset(x0 + (w * 0.38f) * p, cy), Offset(x1 - (w * 0.38f) * p, cy), stroke, StrokeCap.Round)
        }
        CountBadge(badge, Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 6.dp))
    }
}

/** Menu ke atas (dipakai saat ada mini player): daftar vertikal di atas tombol. Bisa digulir supaya tidak ada yang terpotong. */
@Composable
fun NavMenuUp(visible: Boolean, entries: List<NavEntry>, onClose: () -> Unit, modifier: Modifier = Modifier) {
    var sub by remember { mutableStateOf<NavEntry?>(null) }
    LaunchedEffect(visible) { if (!visible) sub = null }
    AnimatedVisibility(
        visible = visible && entries.isNotEmpty(), modifier = modifier,
        enter = expandVertically(tween(260), expandFrom = Alignment.Bottom) + fadeIn(tween(200)),
        exit = shrinkVertically(tween(200), shrinkTowards = Alignment.Bottom) + fadeOut(tween(140))
    ) {
        Column(
            modifier = Modifier
                .padding(bottom = 10.dp)
                .width(248.dp)
                .navGlass(RoundedCornerShape(26.dp))
                .animateContentSize(tween(220))
                .padding(8.dp)
        ) {
            val s = sub
            if (s == null) {
                SlideList(entries, arrowFor = { it.children.isNotEmpty() }) { e ->
                    if (e.children.isNotEmpty()) sub = e else { onClose(); e.onClick() }
                }
            } else {
                val shown = listOf(NavEntry(Icons.AutoMirrored.Rounded.ArrowBack, s.label, selected = true, onClick = {})) + s.children
                SlideList(shown, arrowFor = { false }) { e ->
                    if (e === shown.first()) sub = null else { onClose(); e.onClick() }
                }
            }
        }
    }
}

private val RowH = 50.dp

/** Daftar menu dengan penanda yang meluncur: tekan lalu geser naik/turun, lepas untuk memilih (sama seperti navbar bawah). */
@Composable
private fun SlideList(entries: List<NavEntry>, arrowFor: (NavEntry) -> Boolean, onPick: (NavEntry) -> Unit) {
    val density = LocalDensity.current
    val rowPx = with(density) { RowH.toPx() }
    val accent = LocalCoverTheme.current.accent
    val haptic = LocalHapticFeedback.current
    var hover by remember { mutableIntStateOf(-1) }
    var dragging by remember { mutableStateOf(false) }
    val list by rememberUpdatedState(entries)
    val pick by rememberUpdatedState(onPick)
    val sel = entries.indexOfFirst { it.selected }.coerceAtLeast(0)
    val target = if (hover >= 0) hover else sel
    val y by animateFloatAsState(target * rowPx, spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow), label = "menuPill")
    val pillScale by animateFloatAsState(
        if (dragging) 1.05f else if (hover >= 0) 1.02f else 1f,
        spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium), label = "menuPillScale"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(RowH * entries.size)
            .pointerInput(entries.size) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    fun idx(v: Float) = (v / rowPx).toInt().coerceIn(0, list.size - 1)
                    var last = idx(down.position.y)
                    hover = last
                    dragging = false
                    val slop = viewConfiguration.touchSlop
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) {
                            list.getOrNull(last)?.let { pick(it) }
                            break
                        }
                        if (!dragging && abs(ch.position.y - down.position.y) > slop) dragging = true
                        if (dragging) {
                            ch.consume()
                            val i = idx(ch.position.y)
                            if (i != last) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                last = i
                                hover = i
                            }
                        }
                    }
                    hover = -1
                    dragging = false
                }
            }
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(0, y.roundToInt()) }
                .fillMaxWidth()
                .height(RowH)
                .graphicsLayer { scaleX = pillScale; scaleY = pillScale }
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.verticalGradient(listOf(accent.copy(alpha = if (hover >= 0) 0.40f else 0.28f), accent.copy(alpha = if (hover >= 0) 0.24f else 0.14f))))
                .border(0.5.dp, accent.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
        )
        Column {
            entries.forEachIndexed { i, e ->
                val active = (hover >= 0 && hover == i) || (hover < 0 && e.selected)
                MenuRow(e, active, arrowFor(e))
            }
        }
    }
}

@Composable
private fun MenuRow(e: NavEntry, active: Boolean, arrow: Boolean) {
    val tint by animateColorAsState(if (active) Color.White else Color.White.copy(alpha = 0.7f), tween(220), label = "rowTint")
    Row(
        modifier = Modifier.fillMaxWidth().height(RowH).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(e.icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            e.label, color = tint, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        if (e.badge > 0) {
            CountBadge(e.badge)
            Spacer(Modifier.width(6.dp))
        }
        if (arrow) Icon(Icons.Rounded.ChevronRight, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
    }
}

/** Menu ke samping (dipakai saat tidak ada lagu): deretan menu seragam yang melebar dari tombol ke kanan dan bisa digeser. */
@Composable
fun NavMenuSide(visible: Boolean, entries: List<NavEntry>, onClose: () -> Unit, modifier: Modifier = Modifier) {
    var sub by remember { mutableStateOf<NavEntry?>(null) }
    LaunchedEffect(visible) { if (!visible) sub = null }
    AnimatedVisibility(
        visible = visible, modifier = modifier,
        enter = expandHorizontally(tween(280), expandFrom = Alignment.Start) + fadeIn(tween(200)),
        exit = shrinkHorizontally(tween(220), shrinkTowards = Alignment.Start) + fadeOut(tween(140))
    ) {
        val s = sub
        val shown = if (s == null) entries else listOf(NavEntry(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali", onClick = {})) + s.children
        LazyRow(
            modifier = Modifier.height(NavButtonSize).navGlass(RoundedCornerShape(50)),
            contentPadding = PaddingValues(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(shown, key = { it.label }) { e ->
                SideItem(e) {
                    when {
                        s != null && e.label == "Kembali" -> sub = null
                        e.children.isNotEmpty() -> sub = e
                        else -> { onClose(); e.onClick() }
                    }
                }
            }
        }
    }
}

@Composable
private fun SideItem(e: NavEntry, onClick: () -> Unit) {
    val accent = LocalCoverTheme.current.accent
    Column(
        modifier = Modifier
            .width(68.dp)
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (e.selected) accent.copy(alpha = 0.25f) else Color.Transparent)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box {
            Icon(e.icon, e.label, tint = Color.White.copy(alpha = if (e.selected) 1f else 0.85f), modifier = Modifier.size(22.dp))
            CountBadge(e.badge, Modifier.align(Alignment.TopEnd).padding(start = 16.dp))
        }
        Text(e.label, color = Color.White.copy(alpha = if (e.selected) 1f else 0.75f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
