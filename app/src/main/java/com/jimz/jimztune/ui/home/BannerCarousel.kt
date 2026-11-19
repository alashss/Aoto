package com.jimz.jimztune.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import com.jimz.jimztune.ui.theme.cardSurface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jimz.jimztune.data.remote.Banner
import com.jimz.jimztune.util.openUrl
import kotlinx.coroutines.delay

/**
 * Banner di bagian paling atas Beranda. Posisinya tetap (tidak ikut tergulir), isinya bisa digeser ke samping
 * kalau ada lebih dari satu banner, dan berganti sendiri tiap beberapa detik. Ketuk = buka tautan banner (kalau ada).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BannerCarousel(
    banners: List<Banner>,
    modifier: Modifier = Modifier,
    /** Halaman tambahan setelah banner (mis. kartu peringkat). */
    extraPages: List<@Composable () -> Unit> = emptyList()
) {
    val pageCount = banners.size + extraPages.size
    if (pageCount == 0) return
    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val shape = RoundedCornerShape(22.dp)

    // Geser otomatis tiap 5 detik. Efek dimulai ulang hanya saat halaman SUDAH BERHENTI (settledPage), bukan saat
    // currentPage berganti di tengah animasi; kalau tidak, animasinya ikut terbatalkan dan carousel macet di antara dua kartu.
    // Setelah user menggeser sendiri, jeda 5 detik dihitung ulang dari halaman tempat ia berhenti.
    LaunchedEffect(pageCount, pagerState.settledPage) {
        if (pageCount > 1) {
            delay(5_000)
            if (!pagerState.isScrollInProgress) {
                pagerState.animateScrollToPage((pagerState.settledPage + 1) % pageCount)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .aspectRatio(2.2f)
            .clip(shape)
            .cardSurface(shape)
    ) {
        HorizontalPager(state = pagerState, modifier = Modifier.matchParentSize()) { page ->
            if (page >= banners.size) {
                extraPages[page - banners.size]()
                return@HorizontalPager
            }
            val banner = banners[page]
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (banner.linkUrl != null) Modifier.clickable { context.openUrl(banner.linkUrl) } else Modifier
                    )
            ) {
                AsyncImage(
                    model = banner.imageUrl,
                    contentDescription = banner.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (!banner.title.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))))
                            .padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 12.dp)
                    ) {
                        Text(
                            banner.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        if (pageCount > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                repeat(pageCount) { i ->
                    val active = i == pagerState.currentPage
                    Box(
                        modifier = Modifier
                            .size(if (active) 7.dp else 5.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = if (active) 0.95f else 0.45f))
                    )
                }
            }
        }
    }
}
