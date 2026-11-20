package com.jimz.jimztune.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

/** Pilihan font (keluarga bawaan sistem, jadi tidak menambah ukuran aplikasi). Disimpan di perangkat. */
object AppFont {
    class Option(val id: String, val label: String, val family: androidx.compose.ui.text.font.FontFamily)

    val options = listOf(
        Option("default", "Bawaan", androidx.compose.ui.text.font.FontFamily.Default),
        Option("sans", "Sans", androidx.compose.ui.text.font.FontFamily.SansSerif),
        Option("serif", "Serif", androidx.compose.ui.text.font.FontFamily.Serif),
        Option("mono", "Mono", androidx.compose.ui.text.font.FontFamily.Monospace),
        Option("cursive", "Tulis", androidx.compose.ui.text.font.FontFamily.Cursive)
    )

    var id: String by mutableStateOf("default")
        private set

    fun init(context: android.content.Context) {
        id = context.getSharedPreferences("jimztune_settings", android.content.Context.MODE_PRIVATE).getString("font", "default") ?: "default"
    }

    fun set(context: android.content.Context, value: String) {
        id = value
        context.getSharedPreferences("jimztune_settings", android.content.Context.MODE_PRIVATE).edit().putString("font", value).apply()
    }

    val family: androidx.compose.ui.text.font.FontFamily get() = options.firstOrNull { it.id == id }?.family ?: androidx.compose.ui.text.font.FontFamily.Default
}

fun jimzTypography(f: androidx.compose.ui.text.font.FontFamily): Typography = JimzTypography.run {
    if (f == androidx.compose.ui.text.font.FontFamily.Default) this else copy(
        displayLarge = displayLarge.copy(fontFamily = f), displayMedium = displayMedium.copy(fontFamily = f),
        displaySmall = displaySmall.copy(fontFamily = f), headlineLarge = headlineLarge.copy(fontFamily = f),
        headlineMedium = headlineMedium.copy(fontFamily = f), headlineSmall = headlineSmall.copy(fontFamily = f),
        titleLarge = titleLarge.copy(fontFamily = f), titleMedium = titleMedium.copy(fontFamily = f),
        titleSmall = titleSmall.copy(fontFamily = f), bodyLarge = bodyLarge.copy(fontFamily = f),
        bodyMedium = bodyMedium.copy(fontFamily = f), bodySmall = bodySmall.copy(fontFamily = f),
        labelLarge = labelLarge.copy(fontFamily = f), labelMedium = labelMedium.copy(fontFamily = f),
        labelSmall = labelSmall.copy(fontFamily = f)
    )
}
