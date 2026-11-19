package com.jimz.jimztune.ui.auth

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.ui.components.JimzDialog
import com.jimz.jimztune.ui.theme.JimzBlue
import kotlinx.coroutines.launch

/** Muncul setelah user memasukkan kode OTP "lupa password". */
@Composable
fun NewPasswordDialog() {
    val auth = AppGraph.auth
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    JimzDialog(
        onDismiss = { auth.recoveryMode = false },
        icon = Icons.Rounded.Lock,
        title = "Buat password baru",
        message = "Kode benar, kamu sudah masuk. Isi password baru untuk akunmu.",
        confirmLabel = if (auth.busy) "Menyimpan…" else "Simpan",
        dismissLabel = "Nanti",
        onConfirm = {
            if (!auth.busy) scope.launch {
                val r = auth.updatePassword(password)
                if (!r.ok) error = r.message
            }
        }
    ) {
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password baru") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            colors = jimzFieldColors(JimzBlue),
            modifier = Modifier.fillMaxWidth()
        )
        if (error != null) {
            Text(error!!, color = Color(0xFFFF8A9B), modifier = Modifier.padding(top = 8.dp))
        }
    }
}
