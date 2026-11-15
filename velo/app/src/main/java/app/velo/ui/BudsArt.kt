package app.velo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import app.velo.R
import app.velo.bt.BudsStyle

/**
 * 3D product render of the connected model's style (bean buds in an open case, stem buds, or
 * over-ear headphones). The renders are pre-made images (see tools/render_buds.py), so showing
 * them costs no 3D work on the phone.
 */
@Composable
fun BudsArt(style: BudsStyle, accent: Color, modifier: Modifier = Modifier, glowing: Boolean = true) {
    val res = when (style) {
        BudsStyle.BEANS -> R.drawable.render_beans
        BudsStyle.STEMS -> R.drawable.render_stems
        BudsStyle.OVER_EAR -> R.drawable.render_over
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        if (glowing) Canvas(Modifier.fillMaxSize()) {
            // soft accent light behind the product, like a studio backdrop
            drawCircle(
                Brush.radialGradient(
                    listOf(accent.copy(alpha = 0.16f), accent.copy(alpha = 0.04f), Color.Transparent),
                    center = Offset(size.width / 2, size.height * 0.48f), radius = size.minDimension * 0.55f
                )
            )
        }
        Image(painterResource(res), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
    }
}
