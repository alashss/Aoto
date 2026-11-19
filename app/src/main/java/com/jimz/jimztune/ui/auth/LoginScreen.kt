package com.jimz.jimztune.ui.auth

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.data.auth.GoogleConfig
import com.jimz.jimztune.R
import com.jimz.jimztune.ui.components.PillTextButton
import com.jimz.jimztune.ui.components.SegmentedTabs
import com.jimz.jimztune.ui.theme.JimzBackground
import com.jimz.jimztune.ui.theme.JimzBlue
import com.jimz.jimztune.ui.theme.NeonTitleStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Layar masuk / daftar: email + password (verifikasi kode OTP lewat email), atau Google (native, tanpa browser). Bisa dilewati kalau AuthManager.REQUIRE_LOGIN = false. */
@Composable
fun LoginScreen() {
    val auth = AppGraph.auth
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var tab by rememberSaveable { mutableIntStateOf(0) } // 0 = masuk, 1 = daftar
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    // null = form biasa; "signup" / "recovery" = sedang memasukkan kode OTP dari email.
    var codeMode by rememberSaveable { mutableStateOf<String?>(null) }
    var code by rememberSaveable { mutableStateOf("") }
    var cooldown by remember { mutableIntStateOf(0) }

    LaunchedEffect(cooldown) {
        if (cooldown > 0) {
            delay(1000)
            cooldown -= 1
        }
    }

    val busy = auth.busy
    val shownMessage = info ?: auth.message
    val shownIsError = if (info != null) isError else true

    BackHandler(enabled = auth.canSkip) { auth.skipLogin() }
    BackHandler(enabled = codeMode != null) {
        codeMode = null
        code = ""
        info = null
    }

    fun report(ok: Boolean, text: String) {
        isError = !ok
        info = text.ifBlank { null }
    }

    fun submit() {
        if (busy) return
        scope.launch {
            val r = if (tab == 0) auth.signIn(email, password) else auth.signUp(email, password)
            report(r.ok, r.message)
            if (r.needsCode) {
                codeMode = "signup"
                code = ""
                cooldown = 60
            }
        }
    }

    fun verify() {
        if (busy) return
        scope.launch {
            val r = if (codeMode == "recovery") auth.verifyRecoveryCode(email, code)
            else auth.verifySignupCode(email, code)
            if (!r.ok) report(false, r.message)
            // Kalau berhasil, sesi terbentuk dan layar login otomatis hilang.
        }
    }

    fun resend() {
        if (busy || cooldown > 0) return
        scope.launch {
            val r = if (codeMode == "recovery") auth.sendPasswordReset(email) else auth.resendSignupCode(email)
            report(r.ok, r.message)
            if (r.ok) cooldown = 60
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF141C3E), JimzBackground)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(12.dp))
            Image(
                painter = painterResource(R.drawable.aoto_logo),
                contentDescription = null,
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
            )
            Spacer(Modifier.height(14.dp))
            Text("Aoto", style = NeonTitleStyle)
            Spacer(Modifier.height(6.dp))
            Text(
                "Masuk supaya favorit, playlist, dan riwayatmu tersimpan di akun. Aman walau aplikasi dihapus.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))

            if (codeMode != null) {
                Text(
                    "Masukkan kode verifikasi",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Kode dikirim ke ${email.trim()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.65f),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(18.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { v -> code = v.filter { it.isDigit() }.take(10) },
                    label = { Text("Kode dari email") },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 22.sp, letterSpacing = 6.sp, textAlign = TextAlign.Center),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                    colors = jimzFieldColors(JimzBlue),
                    modifier = Modifier.fillMaxWidth()
                )
                if (shownMessage != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        shownMessage,
                        color = if (shownIsError) Color(0xFFFF8A9B) else Color(0xFF8DE3B0),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(16.dp))
                PillTextButton(
                    text = if (busy) "Memproses…" else "Verifikasi",
                    onClick = ::verify,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    enabled = !busy && code.length >= 6
                )
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = ::resend, enabled = !busy && cooldown == 0) {
                    Text(
                        if (cooldown > 0) "Kirim ulang kode (${cooldown}d)" else "Kirim ulang kode",
                        color = Color.White.copy(alpha = if (cooldown > 0) 0.4f else 0.75f)
                    )
                }
                TextButton(onClick = {
                    codeMode = null
                    code = ""
                    info = null
                }) { Text("Ganti email", color = Color.White.copy(alpha = 0.6f)) }
                Text(
                    "Tidak ada email? Cek folder spam. Kalau emailmu sudah pernah terdaftar, kode tidak dikirim: coba Masuk.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.4f),
                    textAlign = TextAlign.Center
                )
            } else {

            SegmentedTabs(
                labels = listOf("Masuk", "Daftar"),
                selected = tab,
                onSelect = {
                    tab = it
                    info = null
                    auth.message = null
                }
            )
            Spacer(Modifier.height(18.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                colors = jimzFieldColors(JimzBlue),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            val hint: (@Composable () -> Unit)? = if (tab == 1) ({ Text("Minimal 8 karakter") }) else null
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = "Tampilkan password",
                            tint = Color.White.copy(alpha = 0.6f)
                        )
                    }
                },
                supportingText = hint,
                colors = jimzFieldColors(JimzBlue),
                modifier = Modifier.fillMaxWidth()
            )

            if (shownMessage != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    shownMessage,
                    color = if (shownIsError) Color(0xFFFF8A9B) else Color(0xFF8DE3B0),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(16.dp))
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                PillTextButton(
                    text = if (busy) "Memproses…" else if (tab == 0) "Masuk" else "Buat akun",
                    onClick = ::submit,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    enabled = !busy
                )
            }

            if (tab == 0) {
                TextButton(
                    onClick = {
                        if (busy) return@TextButton
                        scope.launch {
                            val r = auth.sendPasswordReset(email)
                            report(r.ok, r.message)
                            if (r.needsCode) {
                                codeMode = "recovery"
                                code = ""
                                cooldown = 60
                            }
                        }
                    }
                ) { Text("Lupa password?", color = Color.White.copy(alpha = 0.75f)) }
            } else {
                Spacer(Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f).height(1.dp).background(Color.White.copy(alpha = 0.12f)))
                Text("  atau  ", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                Box(Modifier.weight(1f).height(1.dp).background(Color.White.copy(alpha = 0.12f)))
            }
            Spacer(Modifier.height(10.dp))

            GoogleButton(enabled = !busy) {
                if (!GoogleConfig.configured) {
                    report(false, "Login Google belum dikonfigurasi (GOOGLE_WEB_CLIENT_ID belum diisi, lihat README)")
                    return@GoogleButton
                }
                // Sheet pilih akun Google muncul di dalam aplikasi, tanpa browser.
                scope.launch {
                    val r = auth.signInWithGoogleNative(context)
                    if (!r.ok) report(false, r.message)
                }
            }
            }

            if (busy) {
                Spacer(Modifier.height(18.dp))
                CircularProgressIndicator(modifier = Modifier.size(26.dp), color = Color.White.copy(alpha = 0.8f), strokeWidth = 2.5.dp)
            }

            if (auth.canSkip) {
                Spacer(Modifier.height(18.dp))
                TextButton(onClick = { auth.skipLogin() }) {
                    Text("Nanti saja", color = Color.White.copy(alpha = 0.6f))
                }
                Text(
                    "Tanpa akun, data hanya tersimpan di HP ini dan hilang kalau aplikasi dihapus.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.4f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** Tombol Google sesuai pedoman merek: latar putih, garis tipis, logo "G" 4 warna asli, teks gelap. */
@Composable
private fun GoogleButton(enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (enabled) 1f else 0.55f))
            .border(1.dp, Color(0xFF747775), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_google),
            contentDescription = null,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.size(12.dp))
        Text("Lanjut dengan Google", color = Color(0xFF1F1F1F), fontWeight = FontWeight.Medium, fontSize = 15.sp)
    }
}
