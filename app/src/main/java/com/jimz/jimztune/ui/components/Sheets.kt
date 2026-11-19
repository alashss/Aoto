package com.jimz.jimztune.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.jimz.jimztune.ui.theme.LocalCoverTheme

private val Ink = Color(0xFF0B1030)
private val DangerColor = Color(0xFFFF6B7A)

/**
 * Tombol kapsul bertuliskan saja (tanpa ikon). Utama = gradasi aksen, sekunder = kaca transparan.
 * Dipakai di semua dialog dan sheet supaya gayanya seragam.
 */
@Composable
fun PillTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    accent: Color = LocalCoverTheme.current.accent,
    enabled: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "pillTextScale"
    )
    val bg = if (primary && enabled) {
        Modifier.background(Brush.horizontalGradient(listOf(accent, lerp(accent, Color.White, 0.38f))))
    } else {
        Modifier
            .background(Color.White.copy(alpha = if (enabled) 0.09f else 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
    }
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .height(46.dp)
            .clip(CircleShape)
            .then(bg)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = when {
                primary && enabled -> Ink
                enabled -> Color.White
                else -> Color.White.copy(alpha = 0.35f)
            },
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

/** Tombol ikon bulat bergaya kaca (edit, hapus, kembali, ulangi). */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    Box(
        modifier = modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.09f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/**
 * Dialog bergaya Aoto: kartu gradasi gelap, ikon di lingkaran aksen, dan tombol kapsul.
 * Menggantikan AlertDialog bawaan Material yang terlihat polos.
 * Kirim confirmLabel = null kalau isinya (lewat [content]) sudah punya tombol sendiri.
 */
@Composable
fun JimzDialog(
    onDismiss: () -> Unit,
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    confirmLabel: String? = null,
    onConfirm: () -> Unit = {},
    dismissLabel: String? = "Batal",
    destructive: Boolean = false,
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
    val accent = if (destructive) DangerColor else LocalCoverTheme.current.accent
    val shape = RoundedCornerShape(28.dp)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Brush.verticalGradient(listOf(Color(0xFF1C2350), Color(0xFF0F1433))))
                .border(
                    1.dp,
                    Brush.verticalGradient(listOf(accent.copy(alpha = 0.45f), Color.White.copy(alpha = 0.06f))),
                    shape
                )
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconBadge(icon, tint = accent, size = 56.dp)
            Spacer(Modifier.height(16.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            if (message != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.72f),
                    textAlign = TextAlign.Center,
                    lineHeight = 21.sp
                )
            }
            if (content != null) {
                Spacer(Modifier.height(16.dp))
                content()
            }
            if (confirmLabel != null || dismissLabel != null) {
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (dismissLabel != null) {
                        PillTextButton(dismissLabel, onDismiss, Modifier.weight(1f), primary = false)
                    }
                    if (confirmLabel != null) {
                        PillTextButton(confirmLabel, onConfirm, Modifier.weight(1f), primary = true, accent = accent)
                    }
                }
            }
        }
    }
}

/** Bottom sheet bergaya Aoto. Sudah menangani keyboard dan bar navigasi, jadi isian tidak tertutup. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JimzBottomSheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = lerp(com.jimz.jimztune.ui.theme.JimzSurface, com.jimz.jimztune.ui.theme.JimzSurfaceHigh, 0.55f),
        contentColor = Color.White,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        shape = RoundedCornerShape(topStart = com.jimz.jimztune.ui.theme.AppTheme.round(28.dp), topEnd = com.jimz.jimztune.ui.theme.AppTheme.round(28.dp)),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 16.dp),
            content = content
        )
    }
}

/** Satu baris aksi di dalam sheet: ikon di kotak aksen, judul, dan keterangan kecil. */
@Composable
fun SheetAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    destructive: Boolean = false,
    trailing: (@Composable () -> Unit)? = null
) {
    val tint = if (destructive) DangerColor else LocalCoverTheme.current.accent
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(icon, tint = tint)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (destructive) DangerColor else Color.White
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.55f)
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}
