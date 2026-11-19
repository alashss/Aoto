package com.jimz.jimztune.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/** Warna kolom isian yang serasi dengan tema gelap Aoto. */
@Composable
fun jimzFieldColors(accent: Color) = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = accent,
    unfocusedBorderColor = Color.White.copy(alpha = 0.18f),
    focusedLabelColor = accent,
    unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
    cursorColor = accent,
    focusedContainerColor = Color.White.copy(alpha = 0.05f),
    unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
    focusedSupportingTextColor = Color.White.copy(alpha = 0.6f),
    unfocusedSupportingTextColor = Color.White.copy(alpha = 0.5f)
)

/** Foto profil bulat. Tanpa foto: lingkaran berwarna dengan huruf pertama nama. */
@Composable
fun ProfileAvatar(url: Any?, name: String, size: Dp, accent: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size)
            )
        } else {
            Text(
                name.trim().take(1).uppercase().ifEmpty { "?" },
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.42f).sp,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = (size.value * 0.42f).sp)
            )
        }
    }
}
