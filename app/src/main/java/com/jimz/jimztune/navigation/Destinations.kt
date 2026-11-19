package com.jimz.jimztune.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Dest(val route: String, val label: String, val icon: ImageVector) {
    data object Home : Dest("home", "Beranda", Icons.Rounded.Home)
    data object Search : Dest("search", "Cari", Icons.Rounded.Search)
    data object Library : Dest("library", "Koleksi", Icons.Rounded.LibraryMusic)
    data object Settings : Dest("settings", "Setelan", Icons.Rounded.Settings)

    companion object {
        // Harus lazy: kalau dibuat langsung, Dest.Home yang tersentuh lebih dulu (mis. startDestination di NavHost)
        // membuat isi daftar ini null dan aplikasi crash saat dibuka.
        val all: List<Dest> by lazy { listOf(Home, Search, Library, Settings) }
    }
}
