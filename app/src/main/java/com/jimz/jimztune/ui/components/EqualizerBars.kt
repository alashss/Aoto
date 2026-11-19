package com.jimz.jimztune.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.jimz.jimztune.ui.theme.JimzBlue
import com.jimz.jimztune.ui.theme.JimzBlueSoft

/** Batang equalizer yang bergerak pelan, mengulang motif equalizer di logo. */
@Composable
fun EqualizerBars(modifier: Modifier = Modifier, barCount: Int = 7) {
    val transition = rememberInfiniteTransition(label = "eq")
    val fractions = (0 until barCount).map { i ->
        transition.animateFloat(
            initialValue = 0.2f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(450 + i * 110, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar$i"
        )
    }
    Row(
        modifier = modifier.height(32.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        fractions.forEach { f ->
            val value by f
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight(value)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Brush.verticalGradient(listOf(JimzBlueSoft, JimzBlue)))
            )
        }
    }
}
