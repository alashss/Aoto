package com.jimz.jimztune

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.jimz.jimztune.navigation.JimzTuneApp
import com.jimz.jimztune.ui.theme.JimzTuneTheme
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jimz.jimztune.player.PlayerViewModel
import com.jimz.jimztune.ui.gate.AppGate
import com.jimz.jimztune.ui.auth.LoginScreen
import com.jimz.jimztune.ui.auth.NewPasswordDialog
import android.content.Intent
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppGraph.init(this)
        // Coil: dukung GIF / WebP bergerak (foto profil & banner). Dipasang sekali untuk seluruh aplikasi.
        coil.Coil.setImageLoader(
            coil.ImageLoader.Builder(applicationContext)
                .components {
                    if (Build.VERSION.SDK_INT >= 28) add(coil.decode.ImageDecoderDecoder.Factory())
                    else add(coil.decode.GifDecoder.Factory())
                }
                .build()
        )
        // Paling awal: catat crash apa pun supaya penyebabnya bisa dibaca di layar berikutnya.
        CrashLogger.install(this)
        com.jimz.jimztune.data.stream.StreamResolver.warmUp()
        installSplashScreen()
        super.onCreate(savedInstanceState)
        com.jimz.jimztune.util.ScreenGuard.apply(this)
        com.jimz.jimztune.util.PrivacyPrefs.applyRefresh(this)
        com.jimz.jimztune.ui.theme.AppFont.init(this)
        com.jimz.jimztune.util.FailedSongs.init(this)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        // Android 13+: izin notifikasi dibutuhkan agar kontrol pemutar muncul di bilah notifikasi.
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Aplikasi dibuka dari tautan jimztune://auth (login Google / konfirmasi email) saat belum berjalan.
        if (savedInstanceState == null) handleAuthIntent(intent)

        setContent {
            JimzTuneTheme {
                var crash by remember { mutableStateOf(CrashLogger.read(this@MainActivity)) }
                val report = crash
                if (report != null) {
                    CrashReportScreen(report) {
                        CrashLogger.clear(this@MainActivity)
                        crash = null
                    }
                } else {
                    var showLoader by remember { mutableStateOf(true) }
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(1650)
                        showLoader = false
                    }
                    androidx.compose.foundation.layout.Box {
                    // PlayerViewModel milik activity, sama persis dengan yang dipakai JimzTuneApp.
                    val player: PlayerViewModel = viewModel()
                    val auth = AppGraph.auth
                    AppGate(onBlocked = player::stop, onInterrupt = player::stop) {
                        if (auth.loginVisible) LoginScreen() else JimzTuneApp()
                        if (auth.recoveryMode) NewPasswordDialog()
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showLoader,
                        enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(0)),
                        exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(500)) +
                            androidx.compose.animation.scaleOut(androidx.compose.animation.core.tween(500), targetScale = 1.08f)
                    ) {
                        com.jimz.jimztune.ui.splash.AotoLoader()
                    }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthIntent(intent)
    }

    private fun handleAuthIntent(intent: Intent?) {
        val data = intent?.data ?: return
        // Tautan bagikan (playlist / lagu): tampilkan isinya, bukan alur login.
        if (AppConfig.SHARE_ENABLED) com.jimz.jimztune.data.share.ShareLinks.parse(data)?.let { code ->
            intent?.data = null
            com.jimz.jimztune.data.share.ShareLinks.pending = code
            return
        }
        if (data.scheme != "jimztune") return
        intent?.data = null // jangan diproses dua kali (mis. saat layar diputar)
        lifecycleScope.launch { AppGraph.auth.handleCallback(data) }
    }

    override fun onStart() {
        super.onStart()
        // Aktifkan koneksi realtime + penanda "online" selama aplikasi terlihat.
        AppGraph.setAppVisible(true)
    }

    override fun onStop() {
        super.onStop()
        AppGraph.setAppVisible(false)
    }

    override fun onResume() {
        super.onResume()
        com.jimz.jimztune.ui.theme.AppTheme.refreshNight()
        // Status ban / suspend / owner dicek tiap aplikasi dibuka, tanpa menunggu jeda sinkronisasi.
        if (AppGraph.auth.isLoggedIn) lifecycleScope.launch { AppGraph.account.refresh() }
        AppGraph.cloud.syncIfDue()
        // Cek kill switch / update / pengumuman tiap aplikasi dibuka (dibatasi sekali per 3 menit).
        lifecycleScope.launch { AppGraph.remote.refresh(force = true) }
    }
}

/** Ditampilkan sekali kalau aplikasi sebelumnya crash, berisi penyebabnya. */
@Composable
private fun CrashReportScreen(report: String, onContinue: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(20.dp)
    ) {
        Text("Aplikasi sempat berhenti", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            "Salin teks di bawah (atau screenshot) lalu kirim ke Claude supaya penyebabnya bisa diperbaiki.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Row {
            Button(onClick = { clipboard.setText(AnnotatedString(report)) }) { Text("Salin") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onContinue) { Text("Lanjut ke aplikasi") }
        }
        Spacer(Modifier.height(12.dp))
        SelectionContainer(modifier = Modifier.weight(1f)) {
            Text(
                report.take(6000),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                modifier = Modifier.verticalScroll(rememberScrollState())
            )
        }
    }
}
