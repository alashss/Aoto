package com.jimz.jimztune.ui.profile

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.ui.auth.ProfileAvatar
import com.jimz.jimztune.ui.auth.jimzFieldColors
import com.jimz.jimztune.ui.components.GlassIconButton
import com.jimz.jimztune.ui.components.PillTextButton
import com.jimz.jimztune.ui.theme.JimzBackground
import com.jimz.jimztune.util.ImageUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private val Palette = listOf(
    "#6C93FF", "#A78BFA", "#F472B6", "#E0607E", "#FB923C",
    "#FACC15", "#4ADE80", "#2DD4BF", "#38BDF8", "#94A3B8"
)

private val UsernameRegex = Regex("^[a-z0-9_.]{3,20}$")

@Composable
fun EditProfileScreen(onDone: () -> Unit) {
    val store = AppGraph.profile
    val p = store.profile
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var displayName by remember { mutableStateOf(p?.displayName.orEmpty()) }
    var username by remember { mutableStateOf(p?.username.orEmpty()) }
    var bio by remember { mutableStateOf(p?.bio.orEmpty()) }
    var statusEmoji by remember { mutableStateOf(p?.statusEmoji.orEmpty()) }
    var statusText by remember { mutableStateOf(p?.statusText.orEmpty()) }
    var accentHex by remember { mutableStateOf(p?.accentHex ?: Palette[0]) }
    var avatarUri by remember { mutableStateOf<Uri?>(null) }
    var bannerUri by remember { mutableStateOf<Uri?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val accent = try { Color(android.graphics.Color.parseColor(accentHex)) } catch (_: Exception) { Color(0xFF6C93FF) }

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { if (it != null) avatarUri = it }
    val bannerPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { if (it != null) bannerUri = it }

    BackHandler(enabled = !saving) { onDone() }

    fun save() {
        if (saving) return
        val name = displayName.trim()
        val user = username.trim().lowercase()
        when {
            name.isEmpty() -> { error = "Nama tampilan tidak boleh kosong"; return }
            user.isNotEmpty() && !UsernameRegex.matches(user) -> {
                error = "Username: huruf kecil, angka, _ dan . saja (3–20 karakter)"; return
            }
        }
        saving = true
        error = null
        scope.launch {
            var avatarUrl = p?.avatarUrl
            var bannerUrl = p?.bannerUrl
            avatarUri?.let { uri ->
                val pick = withContext(Dispatchers.IO) { ImageUtil.prepare(context, uri, 512, 1f, 5 * 1024 * 1024) }
                if (pick is ImageUtil.Picked.TooBig) { error = "GIF foto profil terlalu besar (maks ${pick.limitMb} MB)"; saving = false; return@launch }
                val url = (pick as? ImageUtil.Picked.Ready)?.let { store.uploadImage("avatars", it.bytes, it.contentType, it.ext) }
                if (url == null) { error = "Gagal mengunggah foto profil"; saving = false; return@launch }
                avatarUrl = url
            }
            bannerUri?.let { uri ->
                val pick = withContext(Dispatchers.IO) { ImageUtil.prepare(context, uri, 1200, 3f, 8 * 1024 * 1024) }
                if (pick is ImageUtil.Picked.TooBig) { error = "GIF banner terlalu besar (maks ${pick.limitMb} MB)"; saving = false; return@launch }
                val url = (pick as? ImageUtil.Picked.Ready)?.let { store.uploadImage("banners", it.bytes, it.contentType, it.ext) }
                if (url == null) { error = "Gagal mengunggah banner"; saving = false; return@launch }
                bannerUrl = url
            }
            val changes = JSONObject()
                .put("display_name", name)
                .put("username", if (user.isEmpty()) JSONObject.NULL else user)
                .put("bio", bio.trim())
                .put("status_emoji", statusEmoji.trim())
                .put("status_text", statusText.trim())
                .put("accent_color", accentHex)
                .put("avatar_url", avatarUrl ?: JSONObject.NULL)
                .put("banner_url", bannerUrl ?: JSONObject.NULL)
            val r = store.save(changes)
            saving = false
            if (r.ok) onDone() else error = r.message
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(lerp(JimzBackground, accent, 0.2f), JimzBackground)))
            .imePadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali", { if (!saving) onDone() })
            Text(
                "Edit profil",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.weight(1f).padding(start = 12.dp)
            )
            PillTextButton(
                text = if (saving) "Menyimpan…" else "Simpan",
                onClick = ::save,
                accent = accent,
                enabled = !saving
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            // ---- Banner + foto: ketuk untuk mengganti ----
            Box(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .background(Brush.linearGradient(listOf(accent, lerp(accent, Color.Black, 0.55f))))
                        .clickable(enabled = !saving) {
                            bannerPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val bannerModel: Any? = bannerUri ?: p?.bannerUrl
                    if (bannerModel != null) {
                        AsyncImage(model = bannerModel, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                    Box(
                        modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)).padding(10.dp)
                    ) { Icon(Icons.Rounded.CameraAlt, contentDescription = "Ganti banner", tint = Color.White) }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = 20.dp, y = 44.dp)
                        .border(5.dp, JimzBackground, CircleShape)
                        .clip(CircleShape)
                        .clickable(enabled = !saving) {
                            avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    ProfileAvatar(url = avatarUri ?: p?.avatarUrl, name = displayName, size = 88.dp, accent = accent)
                    Box(
                        modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)).padding(8.dp)
                    ) { Icon(Icons.Rounded.CameraAlt, contentDescription = "Ganti foto", tint = Color.White, modifier = Modifier.size(20.dp)) }
                }
            }
            Spacer(Modifier.height(60.dp))

            Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it.take(32) },
                    label = { Text("Nama tampilan") },
                    singleLine = true,
                    colors = jimzFieldColors(accent),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it.take(20).lowercase().filter { c -> c.isLetterOrDigit() || c == '_' || c == '.' } },
                    label = { Text("Username") },
                    prefix = { Text("@") },
                    singleLine = true,
                    supportingText = { Text("Huruf kecil, angka, _ dan . (3–20 karakter)") },
                    colors = jimzFieldColors(accent),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = statusEmoji,
                        onValueChange = { statusEmoji = it.take(4) },
                        label = { Text("Emoji") },
                        singleLine = true,
                        colors = jimzFieldColors(accent),
                        modifier = Modifier.width(92.dp)
                    )
                    OutlinedTextField(
                        value = statusText,
                        onValueChange = { statusText = it.take(60) },
                        label = { Text("Status") },
                        singleLine = true,
                        placeholder = { Text("Lagi dengerin…") },
                        colors = jimzFieldColors(accent),
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it.take(160) },
                    label = { Text("Tentang saya") },
                    minLines = 3,
                    maxLines = 5,
                    supportingText = { Text("${bio.length}/160") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = jimzFieldColors(accent),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    "WARNA PROFIL",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.2.sp,
                    color = accent,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Palette.chunked(5).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { hex ->
                                val c = Color(android.graphics.Color.parseColor(hex))
                                val selected = hex.equals(accentHex, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(c)
                                        .border(if (selected) 3.dp else 0.dp, Color.White, CircleShape)
                                        .clickable { accentHex = hex },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = Color(0xFF0B1030))
                                }
                            }
                        }
                    }
                }

                if (error != null) {
                    Text(error!!, color = Color(0xFFFF8A9B), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
