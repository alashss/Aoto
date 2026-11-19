package com.jimz.jimztune.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/** Membuka tautan di browser. Tidak crash kalau tidak ada aplikasi yang bisa membukanya. */
fun Context.openUrl(url: String) {
    // KEAMANAN: tautan datang dari server (banner, pengumuman, donasi) dan data pengguna lain. Hanya http(s) yang boleh dibuka;
    // skema lain (intent:, file:, content:, tel:, sms:, dll) bisa dipakai untuk memicu aksi tak terduga di HP.
    val uri = try { Uri.parse(url.trim()) } catch (_: Exception) { null }
    val scheme = uri?.scheme?.lowercase()
    if (uri == null || (scheme != "https" && scheme != "http") || uri.host.isNullOrBlank()) {
        Toast.makeText(this, "Tautan tidak aman, tidak dibuka", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, "Tidak ada aplikasi untuk membuka tautan ini", Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {
        Toast.makeText(this, "Tautan tidak bisa dibuka", Toast.LENGTH_SHORT).show()
    }
}
