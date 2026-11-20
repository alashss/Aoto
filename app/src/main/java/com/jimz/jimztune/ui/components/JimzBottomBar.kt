package com.jimz.jimztune.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jimz.jimztune.navigation.Dest
import com.jimz.jimztune.ui.theme.LocalCoverTheme
import com.jimz.jimztune.ui.theme.cardSurface
import com.jimz.jimztune.ui.theme.glassBackdrop

val BarHeight = 64.dp

/**
 * Navbar melayang bergaya kaca: transparan, tepi tipis berkilau, dan indikator yang meluncur halus.
 * Warna aksen navbar mengikuti cover lagu yang sedang diputar — sinkron dengan latar belakang app.
 */
@Composable
fun JimzBottomBar(
    items: List<Dest>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val selected = selectedIndex.coerceAtLeast(0)
    val shape = RoundedCornerShape(com.jimz.jimztune.ui.theme.AppTheme.round(32.dp))
    val themeColors = com.jimz.jimztune.ui.theme.AppTheme.colors
    val cover = LocalCoverTheme.current
    val accent = cover.accent

    // Warna latar bar ikut bergeser sedikit ke warna cover — tidak terlalu mencolok, cukup terasa nyambung
    val barTopColor by animateColorAsState(
        targetValue = lerp(themeColors.surfaceHigh, accent, 0.12f).copy(alpha = 0.95f),
        animationSpec = tween(600),
        label = "barTop"
    )
    val barBottomColor by animateColorAsState(
        targetValue = lerp(themeColors.surface, accent, 0.08f).copy(alpha = 0.97f),
        animationSpec = tween(600),
        label = "barBottom"
    )
    // Border atas berkilau pakai warna aksen cover
    val borderTopColor by animateColorAsState(
        targetValue = lerp(Color.White.copy(alpha = 0.18f), accent.copy(alpha = 0.55f), 0.35f),
        animationSpec = tween(600),
        label = "borderTop"
    )

    val glassy = com.jimz.jimztune.ui.theme.AppTheme.uiStyle == com.jimz.jimztune.ui.theme.UiStyle.GLASS &&
        com.jimz.jimztune.ui.theme.backdropSupported && com.jimz.jimztune.ui.theme.LocalBackdrop.current != null

    // Gestur: tekan lalu geser kiri/kanan. Indikator mengikuti jari (membesar sedikit), tab berpindah saat jari melewatinya,
    // dan saat dilepas indikator menempel ke tab terpilih dengan pegas.
    var pressing by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var fingerX by remember { mutableStateOf<Float?>(null) }
    var downIndex by remember { mutableStateOf(-1) }
    val currentSelected by rememberUpdatedState(selected)
    val currentOnSelect by rememberUpdatedState(onSelect)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(BarHeight)
            // Mode Kaca: blur dari konten di belakang bar. Gaya lain: alas solid supaya isi layar tidak tembus.
            .then(if (glassy) Modifier.glassBackdrop(shape) else Modifier)
            .background(
                Brush.verticalGradient(
                    listOf(
                        if (glassy) barTopColor.copy(alpha = 0.30f) else barTopColor,
                        if (glassy) barBottomColor.copy(alpha = 0.40f) else barBottomColor
                    )
                ),
                shape
            )
            .cardSurface(shape)
            .clip(shape)
            .pointerInput(items.size) {
                val count = items.size
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val w = size.width.toFloat()
                    fun indexAt(x: Float) = (x / w * count).toInt().coerceIn(0, count - 1)
                    pressing = true
                    downIndex = indexAt(down.position.x)
                    dragging = false
                    val slop = viewConfiguration.touchSlop
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) {
                            if (!dragging && downIndex != currentSelected) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                currentOnSelect(downIndex)
                            }
                            break
                        }
                        if (!dragging && abs(ch.position.x - down.position.x) > slop) dragging = true
                        if (dragging) {
                            ch.consume()
                            fingerX = ch.position.x
                            val idx = indexAt(ch.position.x)
                            if (idx != currentSelected) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                currentOnSelect(idx)
                            }
                        }
                    }
                    pressing = false
                    dragging = false
                    fingerX = null
                    downIndex = -1
                }
            }
    ) {
        val itemWidth = maxWidth / items.size
        val pillWidth = itemWidth - 14.dp
        val density = LocalDensity.current
        val totalPx = with(density) { maxWidth.toPx() }
        val itemPx = totalPx / items.size
        val pillPx = with(density) { pillWidth.toPx() }
        val restX = itemPx * selected + (itemPx - pillPx) / 2f
        val targetX = fingerX?.let { (it - pillPx / 2f).coerceIn(0f, totalPx - pillPx) } ?: restX
        val pillX by animateFloatAsState(
            targetValue = targetX,
            animationSpec = if (dragging) spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessHigh)
            else spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow),
            label = "pillX"
        )
        val pillScale by animateFloatAsState(
            targetValue = if (dragging) 1.12f else if (pressing) 1.05f else 1f,
            animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
            label = "pillScale"
        )
        val topPx = with(density) { 8.dp.toPx() }

        // Pill indikator utama
        Box(
            modifier = Modifier
                .offset { IntOffset(pillX.roundToInt(), topPx.roundToInt()) }
                .size(pillWidth, BarHeight - 16.dp)
                .graphicsLayer {
                    scaleX = pillScale
                    scaleY = pillScale
                }
                .clip(RoundedCornerShape(com.jimz.jimztune.ui.theme.AppTheme.round(24.dp)))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            accent.copy(alpha = if (pressing) 0.38f else 0.28f),
                            accent.copy(alpha = if (pressing) 0.22f else 0.14f)
                        )
                    )
                )
                .border(
                    0.5.dp,
                    accent.copy(alpha = 0.30f),
                    RoundedCornerShape(com.jimz.jimztune.ui.theme.AppTheme.round(24.dp))
                )
        )

        Row(modifier = Modifier.fillMaxSize()) {
            items.forEachIndexed { index, dest ->
                BarItem(
                    dest = dest,
                    selected = index == selected,
                    pressed = pressing && !dragging && downIndex == index,
                    accent = accent,
                    onClick = { if (index != selected) onSelect(index) }
                )
            }
        }
    }
}

@Composable
private fun RowScope.BarItem(
    dest: Dest,
    selected: Boolean,
    pressed: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    val tap = onClick
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "itemScale"
    )
    // Warna teks/ikon tab aktif mengikuti warna aksen cover
    val tint by animateColorAsState(
        targetValue = if (selected) Color.White else Color.White.copy(alpha = 0.52f),
        animationSpec = tween(280),
        label = "tint"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.14f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
        label = "iconScale"
    )

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .semantics {
                role = Role.Tab
                onClick { tap(); true }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = dest.icon,
            contentDescription = dest.label,
            tint = tint,
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                }
        )
        Text(
            text = dest.label,
            color = tint,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
