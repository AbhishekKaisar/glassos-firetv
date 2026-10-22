package io.github.abhishekkaisar.glassos

import android.graphics.Bitmap
import android.graphics.RectF
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Adapts the glass and text over the Top Shelf art to its brightness, like tvOS Liquid Glass. Each slide's
 * blurred copy is measured once where the pill, the chevron and the subtitle sit (mean sRGB luma plus its
 * spread); the hero blends the values of the shown and incoming slide with the crossfade's own progress, so
 * the glass changes in step with the art and never pops. [pill], [chevron] and [subtitle] hold the blended
 * background value (0 black .. 1 white, mean plus half the spread).
 */
object Tone {
    /** Regions as fractions of the screen. */
    val PILL = RectF(0.82f, 0.04f, 0.975f, 0.10f)
    val CHEVRON = RectF(0.48f, 0.68f, 0.52f, 0.71f)
    val SUBTITLE = RectF(0.03f, 0.18f, 0.30f, 0.32f)

    @Volatile var pill = 0.2f
    @Volatile var chevron = 0.2f
    @Volatile var subtitle = 0.2f

    /** Mean sRGB luma plus half its standard deviation in [r] of [b] (0..1). */
    fun measure(b: Bitmap, r: RectF): Float = try {
        val x0 = (r.left * b.width).toInt().coerceIn(0, b.width - 1)
        val x1 = (r.right * b.width).toInt().coerceIn(x0 + 1, b.width)
        val y0 = (r.top * b.height).toInt().coerceIn(0, b.height - 1)
        val y1 = (r.bottom * b.height).toInt().coerceIn(y0 + 1, b.height)
        val w = x1 - x0
        val px = IntArray(w * (y1 - y0))
        b.getPixels(px, 0, w, x0, y0, w, y1 - y0)
        var sum = 0.0
        var sq = 0.0
        for (c in px) {
            val l = (0.2126 * ((c shr 16) and 255) + 0.7152 * ((c shr 8) and 255) + 0.0722 * (c and 255)) / 255.0
            sum += l; sq += l * l
        }
        val mean = sum / px.size
        val sd = sqrt((sq / px.size - mean * mean).coerceAtLeast(0.0))
        (mean + 0.5 * sd).toFloat().coerceIn(0f, 1f)
    } catch (e: Throwable) { 0.2f }

    /** 0 on dark art, rising smoothly to 1 on bright art. */
    fun brightness(bg: Float): Float {
        val t = ((bg - 0.30f) / (0.80f - 0.30f)).coerceIn(0f, 1f)
        return t * t * (3 - 2 * t)
    }

    /** Pill glass for a background [bg]: white tint, dark layer (black alpha), and the text shadow amount. */
    class Glass(val tint: Float, val dark: Float, val shadow: Float, val t: Float)

    private fun lin(c: Float): Float = if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()

    /** Contrast of white text over the pill's effective colour (sRGB compositing, as the GPU does it). */
    fun contrast(bg: Float, tint: Float, dark: Float): Float {
        val eff = bg * (1 - dark) * (1 - tint) + tint * 0.97f
        return 1.05f / (lin(eff.coerceIn(0f, 1f)) + 0.05f)
    }

    fun pillGlass(bg: Float): Glass {
        val t = brightness(bg)
        val tint = 0.20f + (0.06f - 0.20f) * t
        var dark = 0.32f * t
        // Then darken just enough for 4.5:1 with white text, up to 0.55 on near-white art.
        var guard = 0
        while (contrast(bg, tint, dark) < 4.5f && dark < 0.55f && guard++ < 40) dark += 0.01f
        val shadow = ((t - 0.7f) / 0.3f).coerceIn(0f, 1f)
        return Glass(tint, dark, shadow, t)
    }

    /** Called by the hero each frame with the shown slide [a], the incoming one [b] and the crossfade progress. */
    fun blend(a: Loaded?, b: Loaded?, f: Float) {
        fun mix(x: Float?, y: Float?): Float? = when {
            x != null && y != null -> x + (y - x) * f
            else -> x ?: y
        }
        mix(a?.pillBg, b?.pillBg)?.let { pill = it }
        mix(a?.chevronBg, b?.chevronBg)?.let { chevron = it }
        mix(a?.subBg, b?.subBg)?.let { subtitle = it }
    }
}
