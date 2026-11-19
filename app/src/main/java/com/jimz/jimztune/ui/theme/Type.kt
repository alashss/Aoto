package com.jimz.jimztune.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val BaseTypography = Typography()

val JimzTypography = BaseTypography.copy(
    titleLarge = BaseTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleMedium = BaseTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = BaseTypography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = BaseTypography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
)

// Gaya nama aplikasi: tebal dan miring seperti tulisan di logo, dengan gradasi putih ke biru.
val BrandTextStyle = TextStyle(
    fontWeight = FontWeight.Black,
    fontStyle = FontStyle.Italic,
    fontSize = 26.sp,
    letterSpacing = (-0.5).sp,
    brush = Brush.linearGradient(listOf(JimzIce, JimzBlue))
)

// Judul layar: tebal dan bersih (tanpa miring) supaya tidak ramai.
val NeonTitleStyle = TextStyle(
    fontWeight = FontWeight.Bold,
    fontSize = 30.sp,
    lineHeight = 36.sp,
    letterSpacing = (-0.5).sp
)
