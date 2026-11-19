package com.jimz.jimztune.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.jimz.jimztune.util.BackgroundPlayback

/** Muncul sekali, saat lagu pertama diputar, kalau aplikasi masih dibatasi hemat baterai. */
@Composable
fun BatteryPrompt(isPlaying: Boolean) {
    val context = LocalContext.current
    var show by remember { mutableStateOf(false) }

    LaunchedEffect(isPlaying) {
        if (isPlaying && !BackgroundPlayback.promptSeen(context) && !BackgroundPlayback.isExempt(context)) show = true
    }
    if (!show) return

    JimzDialog(
        onDismiss = {
            BackgroundPlayback.markPromptSeen(context)
            show = false
        },
        icon = Icons.Rounded.BatteryChargingFull,
        title = "Biar lagu tidak berhenti sendiri",
        message = "Beberapa HP menghentikan aplikasi musik saat layar mati, jadi lagu berikutnya tidak jalan sampai aplikasi dibuka. " +
            "Izinkan Aoto berjalan tanpa pembatasan baterai.",
        confirmLabel = "Izinkan",
        dismissLabel = "Nanti",
        onConfirm = {
            BackgroundPlayback.markPromptSeen(context)
            BackgroundPlayback.request(context)
            show = false
        }
    )
}
