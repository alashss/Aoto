package com.jimz.jimztune.ui.theme

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import com.jimz.jimztune.AppGraph
import org.json.JSONObject

/** Mode tema. Light sengaja tidak ada: banyak layar memakai teks putih tetap, jadi tema terang belum terbaca. */
enum class ThemeMode(val label: String, val hint: String) {
    DARK("Gelap", "Warna tema apa adanya"),
    BLACK("Hitam", "Latar hitam pekat, hemat baterai di layar AMOLED"),
    DYNAMIC("Dinamis", "Mengikuti warna wallpaper (Android 12 ke atas)")
}

enum class ThemeGroup(val label: String) {
    KLASIK("Klasik"),
    ALAM("Alam"),
    MALAM("Malam & neon"),
    HANGAT("Hangat & netral")
}

/**
 * Preset tema lengkap: latar, permukaan kartu, permukaan tinggi, aksen, dan aksen lembut.
 * Semuanya gelap dan sudah dipilih supaya teks putih dan tombol beraksen tetap terbaca.
 */
enum class ThemePreset(
    val label: String,
    val group: ThemeGroup,
    private val bgHex: Long,
    private val surfaceHex: Long,
    private val highHex: Long,
    private val accentHex: Long,
    private val softHex: Long
) {
    AOTO("Aoto", ThemeGroup.KLASIK, 0xFF090C1A, 0xFF11162B, 0xFF1A2040, 0xFF6C93FF, 0xFFA9C0FF),
    UNGU("Ungu malam", ThemeGroup.KLASIK, 0xFF0E0817, 0xFF1A1028, 0xFF281A3D, 0xFFB388FF, 0xFFD7C0FF),
    MAWAR("Mawar", ThemeGroup.KLASIK, 0xFF160810, 0xFF261019, 0xFF3A1827, 0xFFFF6B9D, 0xFFFFB3CE),
    SENJA("Senja", ThemeGroup.KLASIK, 0xFF150C06, 0xFF241610, 0xFF38231A, 0xFFFF9A4D, 0xFFFFCBA0),
    INDIGO("Indigo", ThemeGroup.KLASIK, 0xFF0A0A1F, 0xFF12123A, 0xFF1C1C55, 0xFF818CF8, 0xFFC0C6FC),
    SAKURA("Sakura", ThemeGroup.KLASIK, 0xFF170B12, 0xFF28141F, 0xFF3D2030, 0xFFF9A8D4, 0xFFFBCFE8),

    ZAMRUD("Zamrud", ThemeGroup.ALAM, 0xFF06120C, 0xFF0D1F16, 0xFF15301F, 0xFF4ADE80, 0xFFA7F3C4),
    LAGUNA("Laguna", ThemeGroup.ALAM, 0xFF061314, 0xFF0C2224, 0xFF123335, 0xFF2DD4BF, 0xFF9AF0E2),
    SAMUDRA("Samudra", ThemeGroup.ALAM, 0xFF04101C, 0xFF08192B, 0xFF0F2640, 0xFF38BDF8, 0xFF9BDCFB),
    LIME("Lime", ThemeGroup.ALAM, 0xFF0C1204, 0xFF172109, 0xFF233310, 0xFFA3E635, 0xFFD4F59A),

    CYBER("Cyberpunk", ThemeGroup.MALAM, 0xFF0B0715, 0xFF150E26, 0xFF221640, 0xFFFF2BD6, 0xFF22E5FF),
    AURORA("Aurora", ThemeGroup.MALAM, 0xFF051217, 0xFF0A1F26, 0xFF12323A, 0xFF5EEAD4, 0xFFA78BFA),
    SUNSET("Sunset", ThemeGroup.MALAM, 0xFF140A12, 0xFF25121F, 0xFF3B1D2E, 0xFFFB7185, 0xFFFDBA74),
    LAVA("Lava", ThemeGroup.MALAM, 0xFF150505, 0xFF260C0C, 0xFF3B1414, 0xFFFF5A4F, 0xFFFFA29B),

    EMAS("Emas", ThemeGroup.HANGAT, 0xFF12100A, 0xFF211D10, 0xFF352E18, 0xFFFBBF24, 0xFFFDE08A),
    MOCHA("Mocha", ThemeGroup.HANGAT, 0xFF130E0B, 0xFF211812, 0xFF33261C, 0xFFD6A67A, 0xFFEBCFB3),
    BATU("Batu", ThemeGroup.HANGAT, 0xFF0D1117, 0xFF161B22, 0xFF21262D, 0xFF7D8DA6, 0xFFB6C2D6),
    MONO("Monokrom", ThemeGroup.HANGAT, 0xFF0A0A0A, 0xFF141414, 0xFF202020, 0xFFE5E5E5, 0xFFA3A3A3);

    val bg: Color get() = Color(bgHex)
    val surface: Color get() = Color(surfaceHex)
    val high: Color get() = Color(highHex)
    val accent: Color get() = Color(accentHex)
    val soft: Color get() = Color(softHex)
}

/**
 * Gaya kartu. Dipakai oleh semua kartu lewat Modifier.cardSurface (lihat ThemeModifiers.kt).
 * Nama lama GLASS dan FLAT dipertahankan supaya pilihan yang sudah tersimpan tetap terbaca.
 */
enum class UiStyle(val label: String, val hint: String) {
    GLASS("Kaca", "Kaca bening: kilap, pantulan cahaya, dan tepi bercahaya"),
    LIQUID("Liquid Glass", "Kaca cair ala iOS: sangat bening, tepi berpendar warna, lekuk cahaya tebal"),
    FROSTED("Kaca es", "Kaca lebih terang dengan kilau di tepi atas"),
    FLAT("Flat", "Warna solid tanpa bingkai"),
    SOFT("Timbul", "Solid dengan bayangan lembut"),
    OUTLINE("Garis", "Transparan dengan garis aksen"),
    NEON("Neon", "Garis aksen bercahaya"),
    TINT("Warna", "Berlatar gradasi aksen"),
    MINIMAL("Minimal", "Nyaris tanpa kartu")
}

/** Gaya yang memakai blur latar sungguhan di bar navigasi. */
val UiStyle.isGlassy: Boolean get() = this == UiStyle.GLASS || this == UiStyle.LIQUID

/** Kebulatan sudut kartu dan sheet. */
enum class Corner(val label: String, val scale: Float) {
    TAJAM("Tajam", 0.45f),
    NORMAL("Normal", 1f),
    BULAT("Bulat", 1.35f),
    PIL("Sangat bulat", 1.7f)
}

/** Latar utama aplikasi. */
enum class BgFx(val label: String, val hint: String) {
    COVER("Ikuti lagu", "Warna latar dan aksen berubah mengikuti sampul lagu yang diputar"),
    GRADIENT("Gradasi tema", "Gradasi dari warna aksen tema, tetap sama di semua lagu"),
    DUO("Dua warna", "Gradasi diagonal aksen dan aksen lembut"),
    AURORA("Aurora", "Cahaya lembut yang bergerak pelan (sedikit lebih boros baterai)"),
    SOLID("Polos", "Satu warna latar saja")
}

/** Latar layar pemutar. */
enum class PlayerBg(val label: String) { COVER("Sampul"), SOLID("Polos"), VIVID("Cerah") }

/** Latar mini-player. */
enum class MiniBg(val label: String) { COVER("Sampul"), SOLID("Polos"), ACCENT("Aksen") }

class ThemeColors(
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val accent: Color,
    val accentSoft: Color
)

/** Pilihan tema, disimpan di perangkat dan ikut tersimpan di akun (supaya tidak hilang saat ganti HP). */
object AppTheme {
    private lateinit var prefs: SharedPreferences
    private lateinit var appContext: Context

    var mode by mutableStateOf(ThemeMode.DARK); private set
    var preset by mutableStateOf(ThemePreset.AOTO); private set
    var customOn by mutableStateOf(false); private set
    var customHue by mutableStateOf(224f); private set
    var customSat by mutableStateOf(0.7f); private set
    var uiStyle by mutableStateOf(UiStyle.GLASS); private set
    var corner by mutableStateOf(Corner.NORMAL); private set
    var bgFx by mutableStateOf(BgFx.COVER); private set
    var playerBg by mutableStateOf(PlayerBg.COVER); private set
    var miniBg by mutableStateOf(MiniBg.COVER); private set
    /** Blur latar sungguhan di bar bawah (bisa dimatikan kalau HP terasa berat). */
    var liveBlur by mutableStateOf(true); private set

    /** Tema ikut jam: malam hari (22.00 sampai 05.00) latar jadi hitam pekat dan aksen sedikit meredup. */
    var autoNight by mutableStateOf(false); private set

    /** Mode hemat baterai: tanpa blur latar, tanpa aurora, refresh rate bawaan. */
    var saver by mutableStateOf(false); private set

    /** Malam atau bukan (dihitung ulang tiap [refreshNight] dipanggil, mis. saat app dibuka). */
    var isNight by mutableStateOf(false); private set
    var colors by mutableStateOf(compute()); private set

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        appContext = context.applicationContext
        prefs = appContext.getSharedPreferences("jimztune_theme", Context.MODE_PRIVATE)
        mode = pick("mode", ThemeMode.DARK)
        preset = loadPreset()
        customOn = prefs.getBoolean("custom_on", false)
        customHue = prefs.getFloat("custom_hue", 224f).coerceIn(0f, 360f)
        customSat = prefs.getFloat("custom_sat", 0.7f).coerceIn(0.2f, 1f)
        uiStyle = pick("style", UiStyle.GLASS)
        corner = pick("corner", Corner.NORMAL)
        bgFx = pick("bg_fx", BgFx.COVER)
        playerBg = pick("player_bg", PlayerBg.COVER)
        miniBg = pick("mini_bg", MiniBg.COVER)
        liveBlur = prefs.getBoolean("live_blur", true)
        autoNight = prefs.getBoolean("auto_night", false)
        saver = prefs.getBoolean("saver", false)
        isNight = nightNow()
        colors = compute()
    }

    private fun nightNow(): Boolean {
        val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return h >= 22 || h < 5
    }

    /** Dipanggil saat app dibuka/dilanjutkan supaya tema malam ikut jam. */
    fun refreshNight() {
        if (!::prefs.isInitialized) return
        val n = nightNow()
        if (n != isNight) {
            isNight = n
            if (autoNight) colors = compute()
        }
    }

    fun updateAutoNight(v: Boolean) {
        autoNight = v
        prefs.edit().putBoolean("auto_night", v).apply()
        isNight = nightNow()
        colors = compute()
    }

    fun updateSaver(v: Boolean) {
        saver = v
        prefs.edit().putBoolean("saver", v).apply()
    }

    fun updateLiveBlur(v: Boolean) { liveBlur = v; prefs.edit().putBoolean("live_blur", v).apply() }

    /** Skala sudut untuk ukuran dasar [base]. */
    fun round(base: Dp): Dp = base * corner.scale

    fun updateMode(v: ThemeMode) { mode = v; save("mode", v.name) }
    fun updatePreset(v: ThemePreset) { preset = v; customOn = false; prefs.edit().putBoolean("custom_on", false).apply(); save("preset", v.name) }
    fun updateCustom(on: Boolean, hue: Float = customHue, sat: Float = customSat) {
        customOn = on
        customHue = hue.coerceIn(0f, 360f)
        customSat = sat.coerceIn(0.2f, 1f)
        prefs.edit().putBoolean("custom_on", customOn).putFloat("custom_hue", customHue).putFloat("custom_sat", customSat).apply()
        colors = compute()
        notifyChanged()
    }
    fun updateStyle(v: UiStyle) { uiStyle = v; save("style", v.name) }
    fun updateCorner(v: Corner) { corner = v; save("corner", v.name) }
    fun updateBgFx(v: BgFx) { bgFx = v; save("bg_fx", v.name) }
    fun updatePlayerBg(v: PlayerBg) { playerBg = v; save("player_bg", v.name) }
    fun updateMiniBg(v: MiniBg) { miniBg = v; save("mini_bg", v.name) }

    /** Kembali ke tampilan bawaan Aoto. */
    fun resetAll() {
        mode = ThemeMode.DARK; preset = ThemePreset.AOTO; customOn = false; customHue = 224f; customSat = 0.7f
        uiStyle = UiStyle.GLASS; corner = Corner.NORMAL; bgFx = BgFx.COVER; playerBg = PlayerBg.COVER; miniBg = MiniBg.COVER
        persistAll()
        colors = compute()
        notifyChanged()
    }

    // ---------------------------------------------------------------- cloud

    fun exportJson(): JSONObject = JSONObject()
        .put("v", 2)
        .put("mode", mode.name).put("preset", preset.name)
        .put("custom_on", customOn).put("custom_hue", customHue.toDouble()).put("custom_sat", customSat.toDouble())
        .put("style", uiStyle.name).put("corner", corner.name).put("bg_fx", bgFx.name)
        .put("player_bg", playerBg.name).put("mini_bg", miniBg.name)

    /** Menerapkan tema dari akun. Nilai tak dikenal diabaikan (aman terhadap data rusak / versi baru). Tidak memicu unggahan balik. */
    fun applyCloud(o: JSONObject?) {
        if (o == null || !::prefs.isInitialized) return
        runCatching { ThemeMode.valueOf(o.optString("mode")) }.getOrNull()?.let { mode = it }
        runCatching { ThemePreset.valueOf(o.optString("preset")) }.getOrNull()?.let { preset = it }
        if (o.has("custom_on")) customOn = o.optBoolean("custom_on", false)
        if (o.has("custom_hue")) customHue = o.optDouble("custom_hue", 224.0).toFloat().coerceIn(0f, 360f)
        if (o.has("custom_sat")) customSat = o.optDouble("custom_sat", 0.7).toFloat().coerceIn(0.2f, 1f)
        runCatching { UiStyle.valueOf(o.optString("style")) }.getOrNull()?.let { uiStyle = it }
        runCatching { Corner.valueOf(o.optString("corner")) }.getOrNull()?.let { corner = it }
        runCatching { BgFx.valueOf(o.optString("bg_fx")) }.getOrNull()?.let { bgFx = it }
        runCatching { PlayerBg.valueOf(o.optString("player_bg")) }.getOrNull()?.let { playerBg = it }
        runCatching { MiniBg.valueOf(o.optString("mini_bg")) }.getOrNull()?.let { miniBg = it }
        persistAll()
        colors = compute()
    }

    // ---------------------------------------------------------------- internal

    private fun persistAll() {
        prefs.edit()
            .putString("mode", mode.name).putString("preset", preset.name)
            .putBoolean("custom_on", customOn).putFloat("custom_hue", customHue).putFloat("custom_sat", customSat)
            .putString("style", uiStyle.name).putString("corner", corner.name).putString("bg_fx", bgFx.name)
            .putString("player_bg", playerBg.name).putString("mini_bg", miniBg.name)
            .apply()
    }

    private fun save(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
        colors = compute()
        notifyChanged()
    }

    private fun notifyChanged() {
        runCatching { AppGraph.cloud.markDirty() }
    }

    private fun loadPreset(): ThemePreset {
        runCatching { ThemePreset.valueOf(prefs.getString("preset", null) ?: "") }.getOrNull()?.let { return it }
        // Migrasi dari pilihan palet versi lama.
        return when (prefs.getString("palette", null)) {
            "UNGU" -> ThemePreset.UNGU
            "HIJAU" -> ThemePreset.ZAMRUD
            "MAWAR" -> ThemePreset.MAWAR
            "ORANYE" -> ThemePreset.SENJA
            "TEAL" -> ThemePreset.LAGUNA
            else -> ThemePreset.AOTO
        }
    }

    private inline fun <reified T : Enum<T>> pick(key: String, default: T): T =
        runCatching { enumValueOf<T>(prefs.getString(key, null) ?: "") }.getOrDefault(default)

    private fun compute(): ThemeColors {
        if (!::appContext.isInitialized) return fromPreset(ThemePreset.AOTO, black = false)
        if (mode == ThemeMode.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val s = dynamicDarkColorScheme(appContext)
                return ThemeColors(s.surface, s.surfaceContainer, s.surfaceContainerHigh, s.primary, s.secondary)
            } catch (_: Throwable) {
            }
        }
        val base = if (customOn) fromHue(customHue, customSat) else fromPreset(preset, black = false)
        if (mode == ThemeMode.BLACK) return blackOf(base)
        // Tema ikut jam: malam hari hitam pekat dengan aksen sedikit lebih lembut (nyaman untuk mata dan hemat di AMOLED).
        if (autoNight && isNight) {
            val b = blackOf(base)
            return ThemeColors(b.background, b.surface, b.surfaceHigh, lerp(b.accent, Color.Black, 0.12f), b.accentSoft)
        }
        return base
    }

    private fun fromPreset(p: ThemePreset, black: Boolean): ThemeColors {
        val c = ThemeColors(p.bg, p.surface, p.high, p.accent, p.soft)
        return if (black) blackOf(c) else c
    }

    /** Hitam pekat, tetapi permukaan kartu masih membawa sedikit rona tema (tidak jadi abu-abu mati). */
    private fun blackOf(c: ThemeColors): ThemeColors = ThemeColors(
        background = Color.Black,
        surface = lerp(c.surface, Color.Black, 0.55f),
        surfaceHigh = lerp(c.surfaceHigh, Color.Black, 0.45f),
        accent = c.accent,
        accentSoft = c.accentSoft
    )

    private fun fromHue(h: Float, sat: Float): ThemeColors = ThemeColors(
        background = Color.hsv(h, 0.55f * sat + 0.08f, 0.09f),
        surface = Color.hsv(h, 0.50f * sat + 0.08f, 0.16f),
        surfaceHigh = Color.hsv(h, 0.48f * sat + 0.08f, 0.24f),
        accent = Color.hsv(h, (0.40f + 0.45f * sat).coerceAtMost(0.95f), 1f),
        accentSoft = Color.hsv(h, 0.12f + 0.28f * sat, 1f)
    )
}
