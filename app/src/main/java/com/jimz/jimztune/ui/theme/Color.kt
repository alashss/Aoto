package com.jimz.jimztune.ui.theme

import androidx.compose.ui.graphics.Color

// Warna dasar mengikuti tema pilihan user (lihat AppTheme). Dibuat sebagai getter supaya semua pemakai lama
// (JimzBackground, JimzBlue, dst) otomatis ikut berubah saat tema diganti.
val JimzBackground: Color get() = AppTheme.colors.background
val JimzSurface: Color get() = AppTheme.colors.surface
val JimzSurfaceHigh: Color get() = AppTheme.colors.surfaceHigh
val JimzBlue: Color get() = AppTheme.colors.accent
val JimzBlueSoft: Color get() = AppTheme.colors.accentSoft
val JimzIce = Color(0xFFF2F5FF)
val JimzIceDim = Color(0xFF9EA6C4)
val JimzRose = Color(0xFFE0607E)
