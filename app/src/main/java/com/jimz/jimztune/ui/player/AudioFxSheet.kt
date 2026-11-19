package com.jimz.jimztune.ui.player

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jimz.jimztune.player.fx.AudioFx
import com.jimz.jimztune.player.fx.EqPresets
import com.jimz.jimztune.player.fx.SoundStyle
import com.jimz.jimztune.player.fx.SoundStyles
import com.jimz.jimztune.ui.components.IconBadge
import com.jimz.jimztune.ui.components.JimzBottomSheet
import com.jimz.jimztune.ui.components.LabeledSlider
import com.jimz.jimztune.ui.components.OptionChip
import com.jimz.jimztune.ui.components.SegmentedTabs
import com.jimz.jimztune.ui.components.SoftCard
import com.jimz.jimztune.ui.components.SwitchRow
import com.jimz.jimztune.ui.theme.LocalCoverTheme
import kotlin.math.abs
import kotlin.math.roundToInt

/** Kapsul kecil "Suara" di layar pemutar; ketuk untuk membuka kontrol audio. */
@Composable
fun AudioFxButton() {
    var open by remember { mutableStateOf(false) }
    val style = AudioFx.state.style
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = if (style != "Normal") 0.22f else 0.10f))
            .clickable { open = true }
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
        Spacer(Modifier.width(6.dp))
        Text(
            if (style != "Normal") style else "Suara",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White
        )
    }
    if (open) AudioFxSheet(onDismiss = { open = false })
}

private val TABS = listOf("Gaya", "Tempo", "EQ", "Ruang", "Dasar")

/** Kontrol audio dalam lima tab: gaya, tempo & nada, equalizer, efek ruang, dan pengaturan dasar. */
@Composable
fun AudioFxSheet(onDismiss: () -> Unit) {
    val s = AudioFx.state
    var tab by remember { mutableIntStateOf(0) }
    JimzBottomSheet(onDismiss = onDismiss) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(Icons.Rounded.GraphicEq, size = 44.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Kontrol suara", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Gaya: ${s.style}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.09f))
                    .clickable { AudioFx.resetAll() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                Spacer(Modifier.width(6.dp))
                Text("Reset", style = MaterialTheme.typography.labelLarge, color = Color.White)
            }
        }
        SegmentedTabs(
            labels = TABS,
            selected = tab,
            onSelect = { tab = it },
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)
        )
        Column(
            Modifier
                .heightIn(min = 360.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 6.dp)
        ) {
            Crossfade(targetState = tab, label = "fxTab") { t ->
                Column {
                    when (t) {
                        0 -> StyleTab()
                        1 -> TempoTab()
                        2 -> EqTab()
                        3 -> SpaceTab()
                        else -> BasicTab()
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

// ---------------------------------------------------------------- tab Gaya

private fun styleIcon(name: String): ImageVector = when (name) {
    "Sped up" -> Icons.Rounded.FastForward
    "Slowed + reverb" -> Icons.Rounded.Waves
    "Nightcore" -> Icons.Rounded.Bolt
    "Lo-fi" -> Icons.Rounded.Coffee
    "8D" -> Icons.Rounded.Headphones
    "Concert" -> Icons.Rounded.Mic
    else -> Icons.Rounded.GraphicEq
}

@Composable
private fun StyleTab() {
    val s = AudioFx.state
    Text(
        "Sekali ketuk mengatur tempo, nada, reverb, dan EQ sekaligus.",
        style = MaterialTheme.typography.bodySmall,
        color = Color.White.copy(alpha = 0.6f),
        modifier = Modifier.padding(bottom = 12.dp)
    )
    SoundStyles.all.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { st -> StyleCard(st, s.style == st.name, Modifier.weight(1f)) }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    if (s.style == "Custom") {
        Text(
            "Pengaturan manual sedang aktif. Pilih salah satu gaya di atas untuk kembali ke preset.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.55f),
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun StyleCard(st: SoundStyle, selected: Boolean, modifier: Modifier) {
    val accent = LocalCoverTheme.current.accent
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(
                if (selected) Brush.linearGradient(listOf(accent.copy(alpha = 0.42f), accent.copy(alpha = 0.14f)))
                else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.07f), Color.White.copy(alpha = 0.04f)))
            )
            .border(1.dp, if (selected) accent.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.08f), shape)
            .clickable { AudioFx.applyStyle(st) }
            .padding(14.dp)
    ) {
        IconBadge(styleIcon(st.name), tint = if (selected) Color.White else accent, size = 36.dp)
        Spacer(Modifier.height(10.dp))
        Text(st.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
        Text(
            st.hint,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.62f),
            maxLines = 2,
            minLines = 2
        )
    }
}

// ---------------------------------------------------------------- tab Tempo

@Composable
private fun TempoTab() {
    val s = AudioFx.state
    SoftCard {
        BigReadout("Kecepatan", "%.2fx".format(s.speed))
        LabeledSlider("Atur", "%.2fx".format(s.speed), s.speed, 0.5f..2f, { v ->
            AudioFx.update { copy(speed = (v * 20).roundToInt() / 20f) }
        })
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(0.75f to "0,75x", 1f to "1x", 1.25f to "1,25x", 1.5f to "1,5x", 2f to "2x").forEach { (v, text) ->
                OptionChip(text, abs(s.speed - v) < 0.01f, {
                    AudioFx.update { copy(speed = v) }
                })
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    SoftCard {
        BigReadout("Nada", "%.2fx".format(s.effectivePitch))
        LabeledSlider(
            "Atur", "%.2fx".format(s.effectivePitch), s.effectivePitch, 0.5f..2f,
            { v -> AudioFx.update { copy(pitch = (v * 20).roundToInt() / 20f) } },
            enabled = !s.varispeed
        )
        SwitchRow("Varispeed", "Nada ikut kecepatan, seperti piringan hitam", s.varispeed, { on ->
            AudioFx.update { copy(varispeed = on) }
        })
    }
}

@Composable
private fun BigReadout(label: String, value: String) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label.uppercase(), fontSize = 11.sp, letterSpacing = 1.3.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.55f))
        Text(value, fontSize = 44.sp, lineHeight = 48.sp, fontWeight = FontWeight.Black, color = Color.White)
        Spacer(Modifier.height(6.dp))
    }
}

// ---------------------------------------------------------------- tab EQ

@Composable
private fun EqTab() {
    val s = AudioFx.state
    val accent = LocalCoverTheme.current.accent
    val gains = AudioFx.eqGainsOf(s)
    SoftCard {
        SwitchRow("Equalizer", if (s.eqOn) "Aktif: ${s.eqPreset}" else "Mati", s.eqOn, { on ->
            AudioFx.update { copy(eqOn = on) }
        })
    }
    Spacer(Modifier.height(12.dp))
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        EqPresets.all.keys.forEach { name ->
            OptionChip(name, s.eqOn && s.eqPreset == name, {
                AudioFx.update { copy(eqOn = true, eqPreset = name) }
            })
        }
    }
    Spacer(Modifier.height(12.dp))
    SoftCard {
        EqCurve(gains, accent, active = s.eqOn)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            EqPresets.LABELS.forEachIndexed { i, label ->
                EqBand(label, gains[i], accent, active = s.eqOn) { v ->
                    val next = gains.toMutableList().also { it[i] = v }
                    AudioFx.update { copy(eqOn = true, eqPreset = "Custom", eqGains = next) }
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    SoftCard {
        LabeledSlider("Bass boost", "${s.bass}%", s.bass.toFloat(), 0f..100f, { v ->
            AudioFx.update { copy(bass = v.roundToInt()) }
        })
    }
}

/** Garis lengkung hasil EQ: gambaran cepat bentuk suara yang sedang dipakai. */
@Composable
private fun EqCurve(gains: List<Float>, accent: Color, active: Boolean) {
    val tone = if (active) accent else Color.White.copy(alpha = 0.35f)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
    ) {
        val w = size.width
        val h = size.height
        val n = gains.size
        fun px(i: Int) = w * (0.5f + i) / n
        fun py(g: Float) = h * (1f - (g.coerceIn(-12f, 12f) + 12f) / 24f)
        drawLine(Color.White.copy(alpha = 0.12f), Offset(0f, h / 2f), Offset(w, h / 2f), strokeWidth = 1.dp.toPx())
        val line = Path().apply {
            moveTo(0f, py(gains.first()))
            lineTo(px(0), py(gains[0]))
            for (i in 0 until n - 1) {
                val mx = (px(i) + px(i + 1)) / 2f
                cubicTo(mx, py(gains[i]), mx, py(gains[i + 1]), px(i + 1), py(gains[i + 1]))
            }
            lineTo(w, py(gains.last()))
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(tone.copy(alpha = 0.28f), Color.Transparent)))
        drawPath(line, tone, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
    }
}

private fun yToDb(y: Float, h: Float): Float {
    val t = 1f - (y / h.coerceAtLeast(1f)).coerceIn(0f, 1f)
    return (-12f + t * 24f).roundToInt().toFloat()
}

/** Satu pita equalizer berbentuk batang tegak: seret atau ketuk untuk mengatur -12 sampai +12 dB. */
@Composable
private fun EqBand(label: String, value: Float, accent: Color, active: Boolean, onChange: (Float) -> Unit) {
    val current by rememberUpdatedState(onChange)
    var heightPx by remember { mutableFloatStateOf(1f) }
    val tone = if (active) accent else Color.White.copy(alpha = 0.4f)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(52.dp)) {
        Text("%+.0f".format(value), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.85f))
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .height(150.dp)
                .width(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White.copy(alpha = 0.07f))
                .onSizeChanged { heightPx = it.height.toFloat() }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { o -> current(yToDb(o.y, heightPx)) },
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            current(yToDb(change.position.y, heightPx))
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures { o -> current(yToDb(o.y, heightPx)) }
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val h = size.height
                val w = size.width
                val mid = h / 2f
                val y = h * (1f - (value.coerceIn(-12f, 12f) + 12f) / 24f)
                drawRect(
                    brush = Brush.verticalGradient(listOf(tone.copy(alpha = 0.55f), tone.copy(alpha = 0.2f))),
                    topLeft = Offset(0f, minOf(y, mid)),
                    size = Size(w, abs(y - mid))
                )
                drawLine(Color.White.copy(alpha = 0.22f), Offset(0f, mid), Offset(w, mid), strokeWidth = 1.dp.toPx())
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(w * 0.12f, y - 5.dp.toPx()),
                    size = Size(w * 0.76f, 10.dp.toPx()),
                    cornerRadius = CornerRadius(5.dp.toPx())
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label.replace(" ", "\u00A0"), fontSize = 10.sp, color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center, maxLines = 1)
    }
}

// ---------------------------------------------------------------- tab Ruang

@Composable
private fun SpaceTab() {
    val s = AudioFx.state
    SoftCard {
        LabeledSlider("Reverb", "${(s.reverb * 100).roundToInt()}%", s.reverb, 0f..1f, { v ->
            AudioFx.update { copy(reverb = (v * 20).roundToInt() / 20f) }
        })
        LabeledSlider("Luas suara (virtualizer)", "${s.virtualizer}%", s.virtualizer.toFloat(), 0f..100f, { v ->
            AudioFx.update { copy(virtualizer = v.roundToInt()) }
        })
    }
    Spacer(Modifier.height(12.dp))
    SoftCard {
        SwitchRow("8D", "Suara berputar di sekeliling kepala (paling terasa dengan headset)", s.pan8d, { on ->
            AudioFx.update { copy(pan8d = on) }
        })
        SwitchRow("Hangat (lo-fi)", "Meredam nada tinggi", s.warm, { on ->
            AudioFx.update { copy(warm = on) }
        })
    }
}

// ---------------------------------------------------------------- tab Dasar

@Composable
private fun BasicTab() {
    val s = AudioFx.state
    SoftCard {
        SwitchRow("Lewati hening", "Memotong jeda senyap di dalam lagu", s.skipSilence, { on ->
            AudioFx.update(markCustom = false) { copy(skipSilence = on) }
        })
        SwitchRow(
            "Normalisasi volume",
            "Menyamakan keras suara antar lagu (butuh data kenyaringan dari lagu yang diputar)",
            s.normalize,
            { on -> AudioFx.update(markCustom = false) { copy(normalize = on) } }
        )
    }
    Spacer(Modifier.height(12.dp))
    SoftCard {
        BigReadout("Crossfade", if (s.crossfadeSec == 0) "Mati" else "${s.crossfadeSec} dtk")
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(0, 2, 4, 6, 8, 12).forEach { sec ->
                OptionChip(if (sec == 0) "Mati" else "$sec dtk", s.crossfadeSec == sec, {
                    AudioFx.update(markCustom = false) { copy(crossfadeSec = sec) }
                })
            }
        }
        Text(
            "Biarkan Mati untuk album gapless (lagu bersambung tanpa jeda).",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.55f),
            modifier = Modifier.padding(top = 10.dp)
        )
    }
}
