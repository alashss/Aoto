package com.jimz.jimztune.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jimz.jimztune.ui.theme.LocalCoverTheme

/**
 * Lapisan di atas cover lagu yang sedang diunduh: cover meredup, lalu bulatan berputar mengelilingi angka persen.
 * [progress] < 0 = masih mengantre / belum tahu ukurannya (bulatan berputar tanpa angka).
 * Taruh di dalam Box berukuran cover (sudah di-clip).
 */
@Composable
fun DownloadRingOverlay(progress: Float, ring: Dp = 34.dp, modifier: Modifier = Modifier) {
    val accent = LocalCoverTheme.current.accent
    val smooth by animateFloatAsState(progress.coerceIn(0f, 1f), tween(300), label = "dlRing")
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center
    ) {
        if (progress < 0f) {
            CircularProgressIndicator(
                modifier = Modifier.size(ring),
                color = accent,
                trackColor = Color.White.copy(alpha = 0.2f),
                strokeWidth = 3.dp
            )
        } else {
            CircularProgressIndicator(
                progress = { smooth },
                modifier = Modifier.size(ring),
                color = accent,
                trackColor = Color.White.copy(alpha = 0.2f),
                strokeWidth = 3.dp
            )
            Text(
                "${(smooth * 100).toInt()}%",
                color = Color.White,
                fontSize = if (ring >= 30.dp) 9.sp else 8.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}
