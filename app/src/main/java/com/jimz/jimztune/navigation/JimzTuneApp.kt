package com.jimz.jimztune.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import com.jimz.jimztune.ui.components.LocalQueueActions
import com.jimz.jimztune.ui.components.FeatureOffScreen
import com.jimz.jimztune.ui.components.QueueActions
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.ui.components.BatteryPrompt
import com.jimz.jimztune.data.model.SearchItem
import com.jimz.jimztune.data.model.SearchType
import com.jimz.jimztune.player.PlayerViewModel
import com.jimz.jimztune.ui.components.LocalOpenDetail
import com.jimz.jimztune.ui.detail.AlbumScreen
import com.jimz.jimztune.ui.detail.ArtistScreen
import com.jimz.jimztune.ui.components.BarHeight
import com.jimz.jimztune.ui.components.JimzBottomBar
import com.jimz.jimztune.ui.theme.backdropSource
import com.jimz.jimztune.ui.theme.isGlassy
import com.jimz.jimztune.ui.components.LocalOverlayPadding
import com.jimz.jimztune.ui.home.HomeScreen
import com.jimz.jimztune.ui.library.LibraryScreen
import com.jimz.jimztune.ui.player.MiniPlayer
import com.jimz.jimztune.ui.player.PlayerScreen
import com.jimz.jimztune.ui.search.SearchScreen
import com.jimz.jimztune.ui.settings.SettingsScreen
import com.jimz.jimztune.ui.theme.JimzBackground
import com.jimz.jimztune.ui.theme.appBackground
import com.jimz.jimztune.ui.theme.LocalCoverTheme
import com.jimz.jimztune.ui.theme.rememberCoverTheme
import com.jimz.jimztune.ui.donate.DonateDialog
import com.jimz.jimztune.ui.owner.OwnerPanelScreen
import com.jimz.jimztune.ui.announcements.AnnouncementsScreen
import com.jimz.jimztune.data.stats.RankKind
import com.jimz.jimztune.data.stats.RankPeriod
import com.jimz.jimztune.ui.ranking.RankingScreen
import com.jimz.jimztune.ui.profile.EditProfileScreen
import com.jimz.jimztune.ui.profile.ProfileScreen
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import com.jimz.jimztune.data.social.SocialNav
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.requiredHeight
import com.jimz.jimztune.data.social.SocialPage
import com.jimz.jimztune.ui.components.NavButtonSize
import com.jimz.jimztune.ui.components.NavEntry
import com.jimz.jimztune.ui.components.NavMenuButton
import com.jimz.jimztune.ui.components.NavMenuSide
import com.jimz.jimztune.ui.components.NavMenuUp

private fun tabIndex(route: String?): Int = Dest.all.indexOfFirst { it.route == route }

@Composable
fun JimzTuneApp() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    val player: PlayerViewModel = viewModel()
    var expanded by rememberSaveable { mutableStateOf(false) }
    var showDonate by rememberSaveable { mutableStateOf(false) }
    var showAnnouncements by rememberSaveable { mutableStateOf(false) }
    var showProfile by rememberSaveable { mutableStateOf(false) }
    var showRanking by rememberSaveable { mutableStateOf(false) }
    var rankingKind by rememberSaveable { mutableStateOf(RankKind.LISTEN.key) }
    var rankingPeriod by rememberSaveable { mutableStateOf(RankPeriod.ALL.key) }
    var showEditProfile by rememberSaveable { mutableStateOf(false) }
    var onboardingNow by rememberSaveable { mutableStateOf(false) }
    // Akun yang baru dibuat langsung diarahkan mengisi nama, username, bio, dan foto (sekali saja per akun).
    run {
        val ctx = androidx.compose.ui.platform.LocalContext.current
        val uid = AppGraph.auth.session?.userId
        val created = AppGraph.profile.profile?.createdAt
        androidx.compose.runtime.LaunchedEffect(uid, created) {
            if (uid == null || created == null) return@LaunchedEffect
            val prefs = ctx.getSharedPreferences("jimztune_profile", android.content.Context.MODE_PRIVATE)
            if (prefs.getBoolean("onboarded_$uid", false)) return@LaunchedEffect
            prefs.edit().putBoolean("onboarded_$uid", true).apply()
            val fresh = try {
                java.time.Duration.between(java.time.OffsetDateTime.parse(created).toInstant(), java.time.Instant.now()).toHours() < 24
            } catch (_: Exception) { false }
            if (fresh) { onboardingNow = true; showEditProfile = true }
        }
    }
    var showOwner by rememberSaveable { mutableStateOf(false) }
    val playerOpen = expanded && player.ui.current != null

    // Halaman artis/album ditumpuk di atas tab (bisa berlapis: album → artis → album …).
    // Navbar dan mini player tetap terlihat di atasnya.
    val detailState = remember { mutableStateOf(listOf<SearchItem>()) }
    val detail = detailState.value
    val openDetail = remember {
        { item: SearchItem ->
            val stack = detailState.value
            if (item.type != SearchType.SONG && stack.lastOrNull()?.id != item.id) detailState.value = stack + item
            // Dibuka dari pemutar penuh (menu ⋮ → Lihat artis): tutup pemutar supaya halamannya terlihat.
            expanded = false
        }
    }
    val queueActions = remember { QueueActions(playNext = player::playNext, addToQueue = player::addToQueue) }
    com.jimz.jimztune.ui.share.SharedPlaylistHost(player)

    // Antrean yang berasal dari sebuah playlist ikut berubah (real time) saat isi playlist itu berubah.
    val queueSourceId = player.queueSourceId
    LaunchedEffect(queueSourceId) {
        if (queueSourceId != null) {
            snapshotFlow { AppGraph.library.playlist(queueSourceId)?.songs }
                .distinctUntilChanged()
                .collect { songs ->
                    if (songs == null) player.detachQueueSource() else player.syncWithPlaylist(songs)
                }
        }
    }
    // Urutan penting: yang didaftarkan lebih akhir didahulukan. Pemutar penuh harus menang atas halaman detail.
    BackHandler(enabled = detail.isNotEmpty() && !playerOpen && !showAnnouncements && !showProfile && !showOwner) {
        detailState.value = detailState.value.dropLast(1)
    }
    BackHandler(enabled = playerOpen) { expanded = false }
    BackHandler(enabled = showRanking && !playerOpen) { showRanking = false }
    BackHandler(enabled = showProfile && !showEditProfile && !playerOpen) { showProfile = false }

    // Pengumuman realtime yang meminta musik dihentikan: tutup pemutar penuh supaya pengumuman langsung terlihat.
    val interruptId = AppGraph.remote.pendingAnnouncement()?.takeIf { it.interrupt }?.id
    LaunchedEffect(interruptId) { if (interruptId != null) expanded = false }

    // Akun bukan owner lagi (atau keluar akun): tutup panel owner.
    LaunchedEffect(AppGraph.account.isOwner) { if (!AppGraph.account.isOwner) showOwner = false }

    fun goTo(dest: Dest) {
        detailState.value = emptyList()
        nav.navigate(dest.route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // Tinggi area yang tertutup navbar kaca (+ mini player kalau ada), supaya daftar bisa digulir melewatinya.
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Saat potongan lagu catatan diputar, kartu mini pemutar disembunyikan.
    val hasMini = player.ui.current != null && !com.jimz.jimztune.ui.social.NoteAudio.active
    // Warna latar mengikuti cover lagu yang sedang diputar (biru bawaan kalau belum ada lagu).
    // Hanya mengikuti sampul kalau latar aplikasi dipilih "Ikuti lagu"; selain itu warna tema tetap.
    val cover = rememberCoverTheme(
        if (com.jimz.jimztune.ui.theme.AppTheme.bgFx == com.jimz.jimztune.ui.theme.BgFx.COVER) player.ui.current?.thumbnailUrl else null
    )
    // Dianimasikan supaya daftar tidak melompat saat mini player muncul/hilang.
    val miniSpace by animateDpAsState(
        targetValue = if (hasMini) 84.dp else 0.dp,
        animationSpec = tween(350, easing = FastOutSlowInEasing),
        label = "miniSpace"
    )
    val overlay = BarHeight + 12.dp + navInset + miniSpace + 24.dp

    // Scroll ke bawah: navbar melipat (turun & memudar), mini player ikut turun dan menempel di dasar.
    // Scroll ke atas: navbar muncul lagi.
    var scrollHidden by remember { mutableStateOf(false) }
    // Tanpa lagu tidak ada mini player, jadi navbar tidak pernah turun.
    val barHidden = scrollHidden && hasMini
    val scrollConn = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
            ): androidx.compose.ui.geometry.Offset {
                if (available.y < -14f) scrollHidden = true else if (available.y > 14f) scrollHidden = false
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }
    LaunchedEffect(currentRoute) { scrollHidden = false }
    val miniDrop by animateDpAsState(
        targetValue = if (barHidden) BarHeight + 4.dp else 0.dp,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "miniDrop"
    )
    val barExtra by animateDpAsState(
        targetValue = if (barHidden) 90.dp else 0.dp,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "barExtra"
    )
    val barAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (barHidden) 0f else 1f,
        animationSpec = tween(240),
        label = "barAlpha"
    )

    // Warna teks bawaan ditentukan di akar, agar komponen di luar Scaffold tidak jadi hitam.
    val backdrop = com.jimz.jimztune.ui.theme.rememberBackdropHolder()
    // Merekam seluruh layar tiap frame itu mahal: dimatikan saat pemutar penuh menutupi layar atau gayanya bukan kaca.
    androidx.compose.runtime.SideEffect {
        backdrop.active = com.jimz.jimztune.ui.theme.AppTheme.liveBlur && !com.jimz.jimztune.ui.theme.AppTheme.saver &&
            com.jimz.jimztune.ui.theme.AppTheme.uiStyle.isGlassy && !playerOpen
    }
    CompositionLocalProvider(
        LocalContentColor provides MaterialTheme.colorScheme.onBackground,
        LocalCoverTheme provides cover,
        com.jimz.jimztune.ui.theme.LocalBackdrop provides backdrop
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .appBackground(cover)
        ) {
            CompositionLocalProvider(
                LocalOverlayPadding provides overlay,
                LocalOpenDetail provides openDetail,
                LocalQueueActions provides queueActions
            ) {
              Box(modifier = Modifier.fillMaxSize().nestedScroll(scrollConn).backdropSource(backdrop).appBackground(cover)) {
                NavHost(
                    navController = nav,
                    startDestination = Dest.Home.route,
                    modifier = Modifier.fillMaxSize(),
                    enterTransition = {
                        val dir = if (tabIndex(targetState.destination.route) > tabIndex(initialState.destination.route)) 1 else -1
                        // Layar baru masuk sedikit terlambat setelah yang lama memudar, jadi tidak bertumpuk.
                        slideInHorizontally(tween(380, easing = FastOutSlowInEasing)) { (it * 0.08f).toInt() * dir } +
                            fadeIn(tween(300, delayMillis = 90, easing = FastOutSlowInEasing))
                    },
                    exitTransition = {
                        val dir = if (tabIndex(targetState.destination.route) > tabIndex(initialState.destination.route)) 1 else -1
                        slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { -(it * 0.05f).toInt() * dir } +
                            fadeOut(tween(140))
                    }
                ) {
                    composable(Dest.Home.route) {
                        HomeScreen(
                            recent = player.recent,
                            currentId = player.ui.current?.id,
                            onPlay = player::play,
                            onOpenSearch = { goTo(Dest.Search) },
                            onOpenProfile = { showProfile = true },
                            onOpenAnnouncements = { showAnnouncements = true },
                            onOpenRanking = { kind, period ->
                                rankingKind = kind.key
                                rankingPeriod = period.key
                                showRanking = true
                            }
                        )
                    }
                    composable(Dest.Search.route) {
                        if (AppGraph.remote.featureOn("search")) {
                            SearchScreen(currentId = player.ui.current?.id, onPlay = player::play)
                        } else {
                            FeatureOffScreen("Pencarian sedang dimatikan", "Fitur ini dimatikan sementara oleh pemilik aplikasi. Coba lagi nanti ya.")
                        }
                    }
                    composable(Dest.Library.route) {
                        LibraryScreen(
                            recent = player.recent,
                            currentId = player.ui.current?.id,
                            onPlay = player::play,
                            onClearHistory = player::clearHistory
                        )
                    }
                    composable(Dest.Settings.route) {
                        SettingsScreen(
                            onDonate = { showDonate = true },
                            onOpenAnnouncements = { showAnnouncements = true },
                            onOpenProfile = { showProfile = true },
                            onOpenOwner = { showOwner = true }
                        )
                    }
                }

                AnimatedContent(
                    targetState = detail.lastOrNull(),
                    transitionSpec = {
                        if (targetState != null) {
                            (slideInHorizontally(tween(340, easing = FastOutSlowInEasing)) { it / 3 } + fadeIn(tween(260))) togetherWith
                                fadeOut(tween(160))
                        } else {
                            fadeIn(tween(1)) togetherWith
                                (slideOutHorizontally(tween(280, easing = FastOutSlowInEasing)) { it / 3 } + fadeOut(tween(220)))
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    label = "detailPage"
                ) { ref ->
                    val back = { detailState.value = detailState.value.dropLast(1) }
                    if (ref != null) {
                        when (ref.type) {
                            SearchType.ARTIST -> ArtistScreen(
                                ref = ref,
                                currentId = player.ui.current?.id,
                                onPlay = player::play,
                                onOpen = openDetail,
                                onBack = back
                            )
                            SearchType.ALBUM, SearchType.PLAYLIST -> AlbumScreen(
                                ref = ref,
                                currentId = player.ui.current?.id,
                                onPlay = player::play,
                                onOpen = openDetail,
                                onBack = back
                            )
                            else -> Unit
                        }
                    }
                }
              }
            }

            // Pudar lembut di dasar layar agar navbar kaca tetap jelas di atas konten.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(overlay)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, JimzBackground.copy(alpha = if (com.jimz.jimztune.ui.theme.AppTheme.uiStyle.isGlassy && com.jimz.jimztune.ui.theme.backdropSupported) 0.30f else 0.92f))))
            )

            // Normal: mini player di atas navbar penuh (bisa digeser). Scroll turun: navbar melipat jadi tombol
            // bulat tiga garis di kiri, mini player ikut turun dan melebar ke samping tombol itu.
            // Menu tombol terbuka ke ATAS kalau ada mini player, ke SAMPING kalau belum ada lagu.
            var menuOpen by remember { mutableStateOf(false) }
            var chatPopup by remember { mutableStateOf(false) }
            LaunchedEffect(barHidden, playerOpen, currentRoute) { menuOpen = false; chatPopup = false }
            val loggedIn = AppGraph.auth.isLoggedIn
            val friendsOn = loggedIn && AppGraph.remote.featureOn("friends")
            val chatOn = loggedIn && AppGraph.remote.featureOn("chat")
            val chatChildren = buildList {
                if (friendsOn) add(NavEntry(Icons.Rounded.Forum, "Pesan", badge = SocialNav.badges.dmUnread) { SocialNav.open(SocialPage.Inbox) })
                if (friendsOn) add(NavEntry(Icons.Rounded.PersonAdd, "Teman", badge = SocialNav.badges.requests) { SocialNav.open(SocialPage.Friends) })
                if (chatOn) add(NavEntry(Icons.Rounded.Groups, "Chat global") { SocialNav.open(SocialPage.GlobalChat) })
            }
            val entries = buildList {
                Dest.all.forEach { d -> add(NavEntry(d.icon, d.label, selected = currentRoute == d.route) { goTo(d) }) }
                if (chatChildren.isNotEmpty()) add(NavEntry(Icons.Rounded.Forum, "Chat", badge = SocialNav.badges.total, children = chatChildren) {})
                if (loggedIn && AppGraph.remote.featureOn("profile")) add(NavEntry(Icons.Rounded.Person, "Profil") { showProfile = true })
                if (AppGraph.account.isOwner) add(NavEntry(Icons.Rounded.AdminPanelSettings, "Panel Owner") { showOwner = true })
            }
            val barItems = if (chatChildren.isNotEmpty()) Dest.all + Dest.Chat else Dest.all
            val circleW by animateDpAsState(
                targetValue = if (barHidden) NavButtonSize + 10.dp else 0.dp,
                animationSpec = tween(320, easing = FastOutSlowInEasing),
                label = "circleW"
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp)
                    .fillMaxWidth()
                    .offset { androidx.compose.ui.unit.IntOffset(0, miniDrop.roundToPx()) }
            ) {
                // Menu bertingkat dari tombol tiga garis (hanya saat ada mini player) dan menu Chat dari navbar.
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                    NavMenuUp(visible = menuOpen && hasMini, entries = entries, onClose = { menuOpen = false })
                }
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.CenterEnd) {
                    NavMenuUp(visible = chatPopup && !barHidden, entries = chatChildren, onClose = { chatPopup = false })
                }
                AnimatedVisibility(
                    visible = hasMini || barHidden,
                    enter = slideInVertically(tween(380, easing = FastOutSlowInEasing)) { it / 2 } + fadeIn(tween(300)),
                    exit = slideOutVertically(tween(260)) { it / 2 } + fadeOut(tween(180))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.width(circleW).requiredHeight(NavButtonSize).clipToBounds().graphicsLayer {
                                val t = 1f - barAlpha
                                alpha = t
                                scaleX = 0.7f + 0.3f * t
                                scaleY = 0.7f + 0.3f * t
                            },
                            contentAlignment = Alignment.CenterStart
                        ) {
                            NavMenuButton(
                                open = menuOpen, badge = SocialNav.badges.total,
                                onToggle = { menuOpen = !menuOpen }, modifier = Modifier.requiredSize(NavButtonSize)
                            )
                        }
                        if (hasMini) {
                            MiniPlayer(
                                state = player.ui,
                                onToggle = player::togglePlay,
                                onNext = player::next,
                                onOpen = { expanded = true },
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Box(Modifier.weight(1f)) {
                                NavMenuSide(visible = menuOpen, entries = entries, onClose = { menuOpen = false })
                            }
                        }
                    }
                }
                JimzBottomBar(
                    items = barItems,
                    selectedIndex = tabIndex(currentRoute),
                    onSelect = { i ->
                        if (barItems[i] == Dest.Chat) chatPopup = true
                        else { chatPopup = false; goTo(Dest.all[i]) }
                    },
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .offset { androidx.compose.ui.unit.IntOffset(0, barExtra.roundToPx()) }
                        .graphicsLayer { alpha = barAlpha }
                )
            }

            AnimatedVisibility(
                visible = playerOpen,
                // Naik dari mini player (membesar) dan turun kembali (mengecil), seperti pemutar bawaan sistem.
                enter = slideInVertically(tween(420, easing = FastOutSlowInEasing)) { it / 2 } + fadeIn(tween(260)) +
                    androidx.compose.animation.scaleIn(tween(420, easing = FastOutSlowInEasing), initialScale = 0.86f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)),
                exit = slideOutVertically(tween(340, easing = FastOutSlowInEasing)) { it / 2 } + fadeOut(tween(260)) +
                    androidx.compose.animation.scaleOut(tween(340, easing = FastOutSlowInEasing), targetScale = 0.86f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f))
            ) {
                CompositionLocalProvider(
                    LocalOpenDetail provides openDetail,
                    LocalQueueActions provides queueActions
                ) {
                PlayerScreen(
                    queueSourceName = AppGraph.library.playlist(player.queueSourceId)?.name,
                    onRemoveFromQueue = player::removeFromQueue,
                    onMoveInQueue = player::moveInQueue,
                    state = player.ui,
                    onClose = { expanded = false },
                    onToggle = player::togglePlay,
                    onNext = player::next,
                    onPrevious = player::previous,
                    onSeek = player::seekTo,
                    onShuffle = player::toggleShuffle,
                    onRepeat = player::toggleRepeat,
                    onSkipTo = player::skipTo,
                    lyrics = player.lyrics,
                    onRetryLyrics = player::retryLyrics,
                    onNextLyricsSource = player::nextLyricsSource,
                    onPickLyricsSource = player::pickLyricsSource,
                    translation = player.translation,
                    onLyricsVisible = player::setLyricsVisible,
                    onTranslationSettingsChanged = player::refreshTranslation
                )
                }
            }

            // Halaman pengumuman: geser masuk dari kanan, menutupi navbar seperti halaman penuh.
            AnimatedVisibility(
                visible = showAnnouncements,
                enter = slideInHorizontally(tween(360, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(240)),
                exit = slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(200))
            ) {
                AnnouncementsScreen(onBack = { showAnnouncements = false })
            }

            // Halaman peringkat (mendengar / donasi): geser masuk dari kanan, menutupi navbar.
            AnimatedVisibility(
                visible = showRanking && AppGraph.remote.featureOn("ranking"),
                enter = slideInHorizontally(tween(360, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(240)),
                exit = slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(200))
            ) {
                RankingScreen(
                    initialKind = RankKind.fromKey(rankingKind),
                    initialPeriod = RankPeriod.fromKey(rankingPeriod),
                    onBack = { showRanking = false }
                )
            }

            // Halaman profil dan edit profil: geser masuk dari kanan, menutupi navbar.
            AnimatedVisibility(
                visible = showProfile && AppGraph.auth.isLoggedIn && AppGraph.remote.featureOn("profile"),
                enter = slideInHorizontally(tween(360, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(240)),
                exit = slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(200))
            ) {
                ProfileScreen(onBack = { showProfile = false }, onEdit = { showEditProfile = true })
            }
            AnimatedVisibility(
                visible = showEditProfile && AppGraph.auth.isLoggedIn,
                enter = slideInHorizontally(tween(360, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(240)),
                exit = slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(200))
            ) {
                EditProfileScreen(onDone = { showEditProfile = false; onboardingNow = false }, onboarding = onboardingNow)
            }

            // Panel Owner: halaman penuh, hanya untuk akun yang terdaftar di tabel `owners` server.
            AnimatedVisibility(
                visible = showOwner && AppGraph.account.isOwner,
                enter = slideInHorizontally(tween(360, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(240)),
                exit = slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(200))
            ) {
                Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) { OwnerPanelScreen(onBack = { showOwner = false }) }
            }

            com.jimz.jimztune.ui.social.SocialHost()
            com.jimz.jimztune.ui.player.LockModeHost(player)
            com.jimz.jimztune.ui.player.LyricsModeHost(player)

            BatteryPrompt(isPlaying = player.ui.isPlaying)
            if (showDonate && AppGraph.remote.featureOn("donate")) DonateDialog { showDonate = false }

            // Popup donasi realtime: hanya muncul di Beranda dan TIDAK mengganggu musik yang sedang diputar.
            val onHome = currentRoute == Dest.Home.route && detail.isEmpty() && !playerOpen &&
                !showAnnouncements && !showProfile && !showEditProfile && !showOwner && !showRanking
            if (onHome && !showDonate && AppGraph.remote.featureOn("donate") && AppGraph.remote.pendingDonatePopup()) {
                DonateDialog { AppGraph.remote.markDonatePopupSeen() }
            }
        }
    }
}
