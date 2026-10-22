package io.github.abhishekkaisar.glassos

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint

/** One blurred backdrop layer: the bitmap, where it sits on screen (bitmap to screen) and its opacity. */
class BackLayer {
    var bmp: Bitmap? = null
    val m = Matrix()
    var alpha = 1f
}

/**
 * The pre-blurred backdrop shared by every glass surface. Layer [a] is the current slide, layer [b] the
 * slide fading in over it (alpha 0..1). The hero updates both once per frame while it animates and calls
 * [frame]; a new bitmap calls the change listeners too.
 */
object Backdrop {
    val a = BackLayer()
    val b = BackLayer()
    val blurred: Bitmap? get() = a.bmp
    var screenW = 1920
    var screenH = 1080
    private val listeners = ArrayList<() -> Unit>()
    private val frameListeners = ArrayList<() -> Unit>()

    fun addListener(l: () -> Unit) { listeners.add(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }
    fun addFrameListener(l: () -> Unit) { frameListeners.add(l) }
    fun removeFrameListener(l: () -> Unit) { frameListeners.remove(l) }

    fun changed() { listeners.toList().forEach { it() } }
    fun frame() { frameListeners.toList().forEach { it() } }

    /** Bitmap-to-screen matrix for a blurred copy drawn under a sharp transform [k] (sharp is screen sized). */
    fun place(layer: BackLayer, bmp: Bitmap?, k: Matrix?, alpha: Float) {
        layer.bmp = bmp
        layer.alpha = alpha
        if (bmp == null) return
        layer.m.setScale(screenW / bmp.width.toFloat(), screenH / bmp.height.toFloat())
        if (k != null) layer.m.postConcat(k)
    }
}

object Blur {
    /** Downscale 8x in halving steps, box-blur (radius 3) three times (close to a Gaussian), then lift saturation a touch. */
    fun makeBackdrop(src: Bitmap): Bitmap {
        var cur = src
        repeat(3) {
            val next = Bitmap.createScaledBitmap(cur, (cur.width / 2).coerceAtLeast(1), (cur.height / 2).coerceAtLeast(1), true)
            if (cur !== src) cur.recycle()
            cur = next
        }
        val w = cur.width
        val h = cur.height
        val a = IntArray(w * h)
        val b = IntArray(w * h)
        cur.getPixels(a, 0, w, 0, 0, w, h)
        val r = 3
        repeat(3) {
            pass(a, b, w, h, r, true)
            pass(b, a, w, h, r, false)
        }
        cur.setPixels(a, 0, w, 0, 0, w, h)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cm = ColorMatrix().apply { setSaturation(1.35f) }
        val bright = ColorMatrix(floatArrayOf(
            1.04f, 0f, 0f, 0f, 6f,
            0f, 1.04f, 0f, 0f, 6f,
            0f, 0f, 1.04f, 0f, 8f,
            0f, 0f, 0f, 1f, 0f
        ))
        cm.postConcat(bright)
        Canvas(out).drawBitmap(cur, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(cm); isFilterBitmap = true })
        cur.recycle()
        return out
    }

    /** In-place box blur (three passes approximate a Gaussian). */
    fun boxBlur(bmp: Bitmap, r: Int, passes: Int = 3) {
        val w = bmp.width
        val h = bmp.height
        val a = IntArray(w * h)
        val b = IntArray(w * h)
        bmp.getPixels(a, 0, w, 0, 0, w, h)
        repeat(passes) {
            pass(a, b, w, h, r, true)
            pass(b, a, w, h, r, false)
        }
        bmp.setPixels(a, 0, w, 0, 0, w, h)
    }

    private fun pass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val len = if (horizontal) w else h
        val lines = if (horizontal) h else w
        val div = 2 * r + 1
        for (line in 0 until lines) {
            val base = if (horizontal) line * w else line
            val stride = if (horizontal) 1 else w
            var sa = 0; var sr = 0; var sg = 0; var sb = 0
            for (i in -r..r) {
                val c = src[base + i.coerceIn(0, len - 1) * stride]
                sa += c ushr 24; sr += (c shr 16) and 255; sg += (c shr 8) and 255; sb += c and 255
            }
            for (i in 0 until len) {
                dst[base + i * stride] = ((sa / div) shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
                val o = src[base + (i - r).coerceIn(0, len - 1) * stride]
                val n = src[base + (i + r + 1).coerceIn(0, len - 1) * stride]
                sa += (n ushr 24) - (o ushr 24)
                sr += ((n shr 16) and 255) - ((o shr 16) and 255)
                sg += ((n shr 8) and 255) - ((o shr 8) and 255)
                sb += (n and 255) - (o and 255)
            }
        }
    }
}
