package com.jimz.jimztune.widget

/**
 * Tema widget lirik. [colors] = null berarti warna latar mengikuti sampul lagu; selain itu gradasi tetap
 * (diagonal dari kiri atas ke kanan bawah). [light] = teks gelap di atas latar terang. [stroke] = warna garis tepi (0 = tanpa).
 * Dipakai bersama oleh penggambar widget (WidgetArt) dan pratinjau di Setelan, jadi keduanya selalu sama.
 */
class WidgetTheme(
    val id: Int,
    val name: String,
    val colors: IntArray?,
    val light: Boolean = false,
    val stroke: Int = 0
) {
    val main: Int get() = if (light) 0xFF14172B.toInt() else 0xFFFFFFFF.toInt()
    val soft: Int get() = if (light) 0xAA14172B.toInt() else 0x99FFFFFF.toInt()
    val track: Int get() = if (light) 0x2914172B else 0x33FFFFFF
}

object WidgetThemes {
    val ALL: List<WidgetTheme> = listOf(
        WidgetTheme(0, "Warna sampul", null),
        WidgetTheme(1, "Gelap", intArrayOf(0xFF1A1D2B.toInt(), 0xFF0B0D16.toInt())),
        WidgetTheme(2, "Terang", intArrayOf(0xFFF6F8FE.toInt(), 0xFFDDE3F3.toInt()), light = true),
        WidgetTheme(3, "Samudra", intArrayOf(0xFF0E3A5B.toInt(), 0xFF1F7A8C.toInt(), 0xFF0A2540.toInt())),
        WidgetTheme(4, "Senja", intArrayOf(0xFFFF7A3D.toInt(), 0xFFE0386B.toInt(), 0xFF5B1A6B.toInt())),
        WidgetTheme(5, "Aurora", intArrayOf(0xFF12B58F.toInt(), 0xFF3A5BD9.toInt(), 0xFF5B2A86.toInt())),
        WidgetTheme(6, "Hutan", intArrayOf(0xFF2E7D4F.toInt(), 0xFF145A4A.toInt(), 0xFF0A2A26.toInt())),
        WidgetTheme(7, "Galaksi", intArrayOf(0xFF6D3BD2.toInt(), 0xFF2B1B6B.toInt(), 0xFF0B0820.toInt())),
        WidgetTheme(8, "Sakura", intArrayOf(0xFFFFE6EE.toInt(), 0xFFFFC2D6.toInt()), light = true),
        WidgetTheme(9, "Mentega", intArrayOf(0xFFFFF4C9.toInt(), 0xFFFFDC8A.toInt()), light = true),
        WidgetTheme(10, "Neon", intArrayOf(0xFF0A0A12.toInt(), 0xFF14101F.toInt()), stroke = 0xFF21E6C1.toInt()),
        WidgetTheme(11, "Kaca", intArrayOf(0xCC1B2030.toInt(), 0xCC0E111C.toInt()), stroke = 0x33FFFFFF)
    )

    fun get(id: Int): WidgetTheme = ALL.getOrElse(id) { ALL[0] }
}
