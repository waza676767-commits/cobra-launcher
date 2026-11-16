package app.velo.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import app.velo.R
import app.velo.bt.BudsStyle
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/**
 * Product photos. Order: a photo you picked for that device → a built-in photo of the model →
 * a 3D render of its style (bean buds, stem buds, over-ear).
 */
object BudsPhotos {
    /** Bumped when a photo changes so screens reload it. */
    val version = MutableStateFlow(0)

    private val builtIn = listOf(
        Regex("redmi buds ?8", RegexOption.IGNORE_CASE) to R.drawable.photo_redmi_buds_8
    )

    fun builtInFor(name: String?): Int? = name?.let { n -> builtIn.firstOrNull { it.first.containsMatchIn(n) }?.second }

    private fun file(ctx: Context, address: String) =
        File(ctx.filesDir, "photos/${address.replace(":", "")}.png")

    fun custom(ctx: Context, address: String?): Bitmap? {
        address ?: return null
        val f = file(ctx, address)
        return if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
    }

    fun clear(ctx: Context, address: String) { file(ctx, address).delete(); version.value++ }

    /**
     * Saves a picked photo. Screenshots from a maker app (dark background, like Xiaomi Earbuds)
     * are trimmed to the earbuds and the background is made transparent so it blends in.
     */
    fun save(ctx: Context, address: String, uri: Uri): Boolean = runCatching {
        val src = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { d, info, _ ->
            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val s = info.size
            val scale = 1400f / maxOf(s.width, s.height)
            if (scale < 1f) d.setTargetSize((s.width * scale).toInt(), (s.height * scale).toInt())
        }.copy(Bitmap.Config.ARGB_8888, true)
        val out = cutOut(src)
        val f = file(ctx, address)
        f.parentFile?.mkdirs()
        f.outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
        version.value++
        true
    }.getOrDefault(false)

    private fun lum(c: Int) = maxOf((c shr 16) and 255, (c shr 8) and 255, c and 255)

    private fun cutOut(b: Bitmap): Bitmap {
        val w = b.width; val h = b.height
        val px = IntArray(w * h); b.getPixels(px, 0, w, 0, 0, w, h)
        // Is the background dark? Sample the border.
        var border = 0L; var n = 0
        for (x in 0 until w step 4) { border += lum(px[x]) + lum(px[(h - 1) * w + x]); n += 2 }
        val dark = border / n < 40
        if (!dark) return b // light/photo background: keep as is
        // Find the largest bright blob region: bounding box of non-background pixels in the middle band,
        // ignoring thin text lines by requiring several bright pixels per row.
        var x0 = w; var x1 = 0; var y0 = h; var y1 = 0
        for (y in 0 until h) {
            var cnt = 0; var rx0 = w; var rx1 = 0
            for (x in 0 until w) if (lum(px[y * w + x]) > 18) { cnt++; if (x < rx0) rx0 = x; if (x > rx1) rx1 = x }
            if (cnt > w / 12) { if (y < y0) y0 = y; if (y > y1) y1 = y; if (rx0 < x0) x0 = rx0; if (rx1 > x1) x1 = rx1 }
        }
        if (x1 <= x0 || y1 <= y0) return b
        val pad = (maxOf(x1 - x0, y1 - y0) * 0.06f).toInt()
        x0 = (x0 - pad).coerceAtLeast(0); y0 = (y0 - pad).coerceAtLeast(0)
        x1 = (x1 + pad).coerceAtMost(w - 1); y1 = (y1 + pad).coerceAtMost(h - 1)
        val cw = x1 - x0 + 1; val ch = y1 - y0 + 1
        val o = IntArray(cw * ch)
        for (y in 0 until ch) for (x in 0 until cw) {
            val c = px[(y + y0) * w + x + x0]
            val a = ((lum(c) - 6) * 255 / 18).coerceIn(0, 255)
            o[y * cw + x] = (a shl 24) or (c and 0xFFFFFF)
        }
        return Bitmap.createBitmap(o, cw, ch, Bitmap.Config.ARGB_8888)
    }
}

@Composable
fun BudsArt(
    style: BudsStyle, accent: Color, modifier: Modifier = Modifier, glowing: Boolean = true,
    address: String? = null, name: String? = null
) {
    val ctx = LocalContext.current
    val ver by BudsPhotos.version.collectAsState()
    val custom = remember(address, ver) { BudsPhotos.custom(ctx, address)?.asImageBitmap() }
    val builtIn = BudsPhotos.builtInFor(name)
    val render = when (style) {
        BudsStyle.BEANS -> R.drawable.render_beans
        BudsStyle.STEMS -> R.drawable.render_stems
        BudsStyle.OVER_EAR -> R.drawable.render_over
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        if (glowing) Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                Brush.radialGradient(
                    listOf(accent.copy(alpha = 0.12f), accent.copy(alpha = 0.03f), Color.Transparent),
                    center = Offset(size.width / 2, size.height * 0.5f), radius = size.minDimension * 0.55f
                )
            )
        }
        when {
            custom != null -> Image(custom, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            builtIn != null -> Image(painterResource(builtIn), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            else -> Image(painterResource(render), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
    }
}
