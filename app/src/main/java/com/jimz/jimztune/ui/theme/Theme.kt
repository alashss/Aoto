package com.jimz.jimztune.ui.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private fun jimzShapes(): Shapes = Shapes(
    extraSmall = RoundedCornerShape(AppTheme.round(8.dp)),
    small = RoundedCornerShape(AppTheme.round(12.dp)),
    medium = RoundedCornerShape(AppTheme.round(16.dp)),
    large = RoundedCornerShape(AppTheme.round(24.dp)),
    extraLarge = RoundedCornerShape(AppTheme.round(28.dp))
)

@Composable
fun JimzTuneTheme(content: @Composable () -> Unit) {
    // Dibaca di sini supaya seluruh aplikasi ikut menggambar ulang saat tema diganti.
    val scheme = darkColorScheme(
        primary = JimzBlue,
        // Teks di atas warna primer harus gelap, kalau terang kontrasnya buruk (tombol biru dengan teks putih).
        onPrimary = JimzBackground,
        primaryContainer = JimzSurfaceHigh,
        onPrimaryContainer = JimzIce,
        secondary = JimzBlueSoft,
        onSecondary = JimzBackground,
        secondaryContainer = JimzSurfaceHigh,
        onSecondaryContainer = JimzIce,
        tertiary = JimzRose,
        background = JimzBackground,
        onBackground = JimzIce,
        surface = JimzSurface,
        onSurface = JimzIce,
        surfaceVariant = JimzSurfaceHigh,
        onSurfaceVariant = JimzIceDim,
        surfaceContainer = JimzSurface,
        surfaceContainerHigh = JimzSurfaceHigh,
        outline = JimzBlueSoft
    )
    MaterialTheme(
        colorScheme = scheme,
        typography = JimzTypography,
        shapes = jimzShapes()
    ) {
        // Surface menyediakan warna teks bawaan (putih kebiruan) untuk semua layar, termasuk layar
        // di luar aplikasi utama seperti "sedang istirahat" dan laporan crash. Tanpanya teks jadi hitam.
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = JimzBackground,
            contentColor = JimzIce,
            content = content
        )
    }
}
