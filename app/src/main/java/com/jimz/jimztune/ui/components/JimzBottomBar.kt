package com.jimz.jimztune.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jimz.jimztune.navigation.Dest
import com.jimz.jimztune.ui.theme.LocalCoverTheme
import com.jimz.jimztune.ui.theme.cardSurface

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

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(BarHeight)
            // Alas solid dulu supaya isi layar di belakang bar tidak tembus, lalu gaya kartu pilihan (Tampilan & tema) di atasnya.
            .background(Brush.verticalGradient(listOf(barTopColor, barBottomColor)), shape)
            .cardSurface(shape)
            .clip(shape)
    ) {
        val itemWidth = maxWidth / items.size
        val pillWidth = itemWidth - 14.dp
        // Satu indikator saja (dulu ada glow blur tambahan di belakangnya, jadi kelihatan seperti dua oval).
        val pillX by animateDpAsState(
            targetValue = itemWidth * selected + (itemWidth - pillWidth) / 2,
            animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow),
            label = "pillX"
        )

        // Pill indikator utama
        Box(
            modifier = Modifier
                .offset(x = pillX, y = 8.dp)
                .size(pillWidth, BarHeight - 16.dp)
                .clip(RoundedCornerShape(com.jimz.jimztune.ui.theme.AppTheme.round(24.dp)))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            accent.copy(alpha = 0.28f),
                            accent.copy(alpha = 0.14f)
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
                    accent = accent,
                    onClick = {
                        if (index != selected) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(index)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun RowScope.BarItem(
    dest: Dest,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

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
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
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
