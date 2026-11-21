package com.jimz.jimztune.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.data.library.Fetched
import com.jimz.jimztune.data.library.ImportException
import com.jimz.jimztune.data.library.ImportedTrack
import com.jimz.jimztune.data.library.PlaylistImporter
import com.jimz.jimztune.data.model.SearchItem
import com.jimz.jimztune.ui.auth.jimzFieldColors
import com.jimz.jimztune.ui.components.JimzBottomSheet
import com.jimz.jimztune.ui.components.PillTextButton
import com.jimz.jimztune.ui.theme.LocalCoverTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private enum class ImportMode { LINK, TEXT }

private sealed interface ImportStep {
    object Input : ImportStep
    class Working(val label: String) : ImportStep
    class Review(
        val source: String,
        val name: String,
        val matched: List<SearchItem>,
        val missing: List<ImportedTrack>,
        val note: String?
    ) : ImportStep
}

/**
 * Impor playlist dari tautan Spotify / YouTube Music / Deezer / Apple Music (atau daftar yang ditempel):
 * baca daftar lagu, cocokkan tiap lagu ke YouTube Music, tampilkan ringkasan, lalu buat playlist.
 */
@Composable
fun ImportPlaylistSheet(onDismiss: () -> Unit, onCreated: (playlistId: String) -> Unit) {
    val scope = rememberCoroutineScope()
    val accent = LocalCoverTheme.current.accent
    val clipboard = LocalClipboardManager.current

    var mode by remember { mutableStateOf(ImportMode.LINK) }
    var link by remember { mutableStateOf("") }
    var pasted by remember { mutableStateOf("") }
    var textName by remember { mutableStateOf("") }
    var step by remember { mutableStateOf<ImportStep>(ImportStep.Input) }
    var error by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var found by remember { mutableIntStateOf(0) }
    var job by remember { mutableStateOf<Job?>(null) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val csvPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val text = ctx.contentResolver.openInputStream(uri)!!.use { ins ->
                    com.jimz.jimztune.data.backup.BackupManager.readCapped(ins, 5 * 1024 * 1024).toString(Charsets.UTF_8)
                }
                val tracks = PlaylistImporter.parseCsv(text)
                if (tracks.isEmpty()) {
                    error = "Tidak ada lagu yang terbaca di berkas CSV itu."
                } else {
                    error = null
                    pasted = tracks.joinToString("\n") { if (it.artist.isBlank()) it.title else "${it.artist} - ${it.title}" }
                    if (textName.isBlank()) {
                        val n = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.')?.trim().orEmpty()
                        if (n.isNotEmpty() && n.length <= 60) textName = n
                    }
                }
            } catch (_: Exception) {
                error = "Berkas CSV tidak bisa dibaca."
            }
        }
    }

    // Impor ratusan lagu bisa makan beberapa menit: layar dijaga tetap menyala selama lembar ini terbuka.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            job?.cancel()
        }
    }

    fun start() {
        error = null
        total = 0
        done = 0
        found = 0
        job?.cancel()
        job = scope.launch {
            try {
                step = ImportStep.Working("Membaca playlist…")
                val fetched: Fetched = if (mode == ImportMode.LINK) {
                    PlaylistImporter.fetch(link) { label -> step = ImportStep.Working(label) }
                } else {
                    val tracks = PlaylistImporter.parseText(pasted)
                    if (tracks.isEmpty()) throw ImportException("Daftar lagu masih kosong")
                    Fetched("Daftar tempelan", textName.trim().ifEmpty { "Playlist impor" }, tracks)
                }
                if (fetched.ready.isNotEmpty()) {
                    step = ImportStep.Review(fetched.source, fetched.name, fetched.ready, emptyList(), fetched.note)
                    return@launch
                }
                total = fetched.tracks.size
                done = 0
                found = 0
                step = ImportStep.Working("Mencocokkan lagu ke YouTube Music…")
                val res = PlaylistImporter.match(
                    fetched.tracks,
                    onPhase = { label -> step = ImportStep.Working(label) }
                ) { d, f ->
                    done = d
                    found = f
                }
                if (res.matched.isEmpty()) throw ImportException("Tidak ada lagu yang cocok ditemukan.")
                step = ImportStep.Review(fetched.source, fetched.name, res.matched, res.missing, fetched.note)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = com.jimz.jimztune.util.UserError.report(
                    "Impor",
                    if (e is ImportException) (e.message ?: "Gagal mengimpor playlist") else "Gagal mengimpor playlist. Coba lagi nanti.",
                    if (e is ImportException) e.detail else "${e.javaClass.simpleName}: ${e.message}"
                )
                step = ImportStep.Input
            }
        }
    }

    JimzBottomSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            when (val s = step) {
                is ImportStep.Input -> {
                    Text("Impor playlist", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(
                        "Pindahkan playlist dari Spotify, YouTube Music, Deezer, atau Apple Music. Semua lagunya diimpor, berapa pun jumlahnya, dan dicarikan padanannya di YouTube Music.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillTextButton("Tautan", onClick = { mode = ImportMode.LINK }, primary = mode == ImportMode.LINK)
                        PillTextButton("Tempel daftar", onClick = { mode = ImportMode.TEXT }, primary = mode == ImportMode.TEXT)
                    }
                    Spacer(Modifier.height(14.dp))
                    if (mode == ImportMode.LINK) {
                        OutlinedTextField(
                            value = link,
                            onValueChange = { link = it },
                            label = { Text("Tautan playlist") },
                            placeholder = { Text("https://open.spotify.com/playlist/…", color = Color.White.copy(alpha = 0.35f)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            colors = jimzFieldColors(accent),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        PillTextButton(
                            "Tempel dari papan klip",
                            onClick = { clipboard.getText()?.text?.let { link = it.trim() } },
                            primary = false
                        )
                    } else {
                        OutlinedTextField(
                            value = textName,
                            onValueChange = { textName = it.take(60) },
                            label = { Text("Nama playlist") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                            colors = jimzFieldColors(accent),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = pasted,
                            onValueChange = { pasted = it },
                            label = { Text("Daftar lagu") },
                            placeholder = { Text("Satu lagu per baris, mis.\nTulus - Hati-Hati di Jalan", color = Color.White.copy(alpha = 0.35f)) },
                            minLines = 5,
                            maxLines = 8,
                            colors = jimzFieldColors(accent),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        PillTextButton(
                            "Pilih berkas CSV",
                            onClick = { csvPicker.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "*/*")) },
                            primary = false,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    error?.let {
                        Text(it, color = Color(0xFFFF8A8A), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    PillTextButton(
                        "Lanjut",
                        onClick = ::start,
                        enabled = if (mode == ImportMode.LINK) link.isNotBlank() else pasted.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                is ImportStep.Working -> {
                    Text("Mengimpor…", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(Modifier.height(18.dp))
                    if (total > 0) {
                        LinearProgressIndicator(
                            progress = { done.toFloat() / total },
                            color = accent,
                            trackColor = Color.White.copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            if (done >= total) "${s.label}  •  $found cocok" else "Mencocokkan $done dari $total lagu  •  $found cocok",
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(color = accent, strokeWidth = 2.5.dp, modifier = Modifier.padding(end = 12.dp).size(22.dp))
                            Text(s.label, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (total > 150) {
                        Text(
                            "Playlist besar bisa makan beberapa menit. Biarkan lembar ini tetap terbuka (layar dijaga menyala) sampai selesai.",
                            color = Color.White.copy(alpha = 0.55f),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    PillTextButton(
                        "Batal",
                        onClick = {
                            job?.cancel()
                            step = ImportStep.Input
                        },
                        primary = false
                    )
                }

                is ImportStep.Review -> ReviewStep(
                    s = s,
                    accent = accent,
                    onBack = { step = ImportStep.Input },
                    onCreate = { name ->
                        val p = AppGraph.library.importPlaylist(name, s.matched)
                        onCreated(p.id)
                    }
                )
            }
        }
    }
}

@Composable
private fun ReviewStep(s: ImportStep.Review, accent: Color, onBack: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember(s) { mutableStateOf(s.name) }
    val totalCount = s.matched.size + s.missing.size
    Text("Hasil impor", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
    Text(
        if (s.missing.isEmpty() && totalCount == s.matched.size && s.source == "YouTube Music") {
            "${s.matched.size} lagu dari ${s.source}"
        } else {
            "${s.matched.size} dari $totalCount lagu dari ${s.source} berhasil dicocokkan"
        },
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.85f),
        modifier = Modifier.padding(top = 4.dp)
    )
    s.note?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = accent, modifier = Modifier.padding(top = 8.dp))
    }
    Spacer(Modifier.height(14.dp))
    OutlinedTextField(
        value = name,
        onValueChange = { name = it.take(60) },
        label = { Text("Nama playlist") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        colors = jimzFieldColors(accent),
        modifier = Modifier.fillMaxWidth()
    )
    if (s.missing.isNotEmpty()) {
        Spacer(Modifier.height(14.dp))
        Text(
            "Tidak ditemukan (${s.missing.size})",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.65f)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 220.dp)
                .verticalScroll(rememberScrollState())
                .padding(top = 6.dp)
        ) {
            s.missing.forEach {
                Text(
                    listOf(it.title, it.artist).filter { x -> x.isNotBlank() }.joinToString(" — "),
                    color = Color.White.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
    Spacer(Modifier.height(18.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PillTextButton("Kembali", onClick = onBack, primary = false)
        PillTextButton("Buat playlist (${s.matched.size})", onClick = { onCreate(name) }, modifier = Modifier.weight(1f))
    }
}
