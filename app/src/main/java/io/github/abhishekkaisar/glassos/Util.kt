package io.github.abhishekkaisar.glassos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import kotlin.math.ceil
import kotlin.math.roundToInt

fun Context.dp(v: Float): Float = v * resources.displayMetrics.density
fun Context.dpi(v: Float): Int = (v * resources.displayMetrics.density).roundToInt()
fun View.dp(v: Float): Float = context.dp(v)
fun View.dpi(v: Float): Int = context.dpi(v)

fun alphaOf(color: Int, a: Float): Int =
    Color.argb((a.coerceIn(0f, 1f) * 255).roundToInt(), Color.red(color), Color.green(color), Color.blue(color))

fun lerpColor(a: Int, b: Int, t: Float): Int {
    val u = t.coerceIn(0f, 1f)
    return Color.argb(
        (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * u).roundToInt(),
        (Color.red(a) + (Color.red(b) - Color.red(a)) * u).roundToInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * u).roundToInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * u).roundToInt()
    )
}

object Fonts {
    val thin: Typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
    val light: Typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    val regular: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    val medium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
}

/** Matrix mapping a view's local coordinates to window coordinates, including scale/translation/rotation of every ancestor. */
fun viewToWindow(v: View, out: Matrix) {
    out.reset()
    var cur: View? = v
    while (cur != null) {
        val vm = cur.matrix
        if (!vm.isIdentity) out.postConcat(vm)
        out.postTranslate(cur.left.toFloat(), cur.top.toFloat())
        val p = cur.parent
        if (p is View) {
            out.postTranslate(-p.scrollX.toFloat(), -p.scrollY.toFloat())
            cur = p
        } else cur = null
    }
}

fun invalidateDeep(v: View) {
    v.invalidate()
    if (v is ViewGroup) for (i in 0 until v.childCount) invalidateDeep(v.getChildAt(i))
}

/** Pre-blurred soft shapes (glows and shadows), rendered once at quarter size and drawn scaled. */
object GlowCache {
    private const val SCALE = 4
    private val map = HashMap<String, Bitmap>()

    /**
     * [hollow]: the shape drawn over the glow is opaque (the shelf's backdrop), so the middle, which it hides
     * completely, is skipped: the glow is drawn as four bands around it. Same pixels, about 40% less fill.
     */
    fun draw(c: Canvas, w: Float, h: Float, radius: Float, blur: Float, color: Int, alpha: Float, dy: Float, paint: Paint, hollow: Boolean = false) {
        if (alpha <= 0.003f || w <= 0f || h <= 0f) return
        val key = "${w.toInt()}x${h.toInt()}r${radius.toInt()}b${blur.toInt()}c$color"
        val pad = ceil(blur * 2.2f)
        val bmp = map.getOrPut(key) {
            val bw = ((w + pad * 2) / SCALE).toInt().coerceAtLeast(2)
            val bh = ((h + pad * 2) / SCALE).toInt().coerceAtLeast(2)
            val b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ALPHA_8)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = Color.WHITE
            p.maskFilter = BlurMaskFilter((blur / SCALE).coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
            val s = 1f / SCALE
            Canvas(b).drawRoundRect(RectF(pad * s, pad * s, (pad + w) * s, (pad + h) * s), radius * s, radius * s, p)
            b
        }
        paint.color = color
        paint.alpha = (alpha * Color.alpha(color)).roundToInt().coerceIn(0, 255)
        paint.isFilterBitmap = true
        tmp.set(-pad, -pad + dy, w + pad, h + pad + dy)
        // The hidden middle: the shape's own rect less its corner radius (plus a pixel for antialiasing).
        val hx0 = radius + 1f
        val hx1 = w - radius - 1f
        val hy0 = maxOf(radius + 1f, tmp.top)
        val hy1 = h - radius - 1f
        if (!hollow || hx1 <= hx0 || hy1 <= hy0) { c.drawBitmap(bmp, null, tmp, paint); return }
        band(c, bmp, tmp, tmp.left, tmp.top, tmp.right, hy0, paint)
        band(c, bmp, tmp, tmp.left, hy1, tmp.right, tmp.bottom, paint)
        band(c, bmp, tmp, tmp.left, hy0, hx0, hy1, paint)
        band(c, bmp, tmp, hx1, hy0, tmp.right, hy1, paint)
    }

    /** Draws the part of [bmp] (stretched over [full]) inside the given rect: an axis-aligned clip, a scissor in HWUI. */
    private fun band(c: Canvas, bmp: Bitmap, full: RectF, l: Float, t: Float, r: Float, b: Float, paint: Paint) {
        if (r <= l || b <= t) return
        c.save()
        c.clipRect(l, t, r, b)
        c.drawBitmap(bmp, null, full, paint)
        c.restore()
    }

    private val tmp = RectF()

}

/** Fine grain tile used on glass and wallpapers. */
object Grain {
    val bitmap: Bitmap by lazy {
        val n = 128
        val px = IntArray(n * n)
        val r = java.util.Random(7)
        for (i in px.indices) {
            val v = r.nextInt(256)
            px[i] = Color.argb(r.nextInt(48), v, v, v)
        }
        Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888)
    }

    fun newShader(): BitmapShader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
}

/** Watcher and overlay timing log: logcat, plus files/watch.log on debuggable builds (logcat on Fire OS drops lines). */
object WLog {
    private var file: java.io.File? = null
    fun init(ctx: android.content.Context) {
        if (ctx.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) file = java.io.File(ctx.filesDir, "watch.log")
    }
    @Synchronized fun i(msg: String) {
        android.util.Log.i("GlassWatch", msg)
        val f = file ?: return
        try {
            if (f.length() > 200_000) f.delete()
            f.appendText("${System.currentTimeMillis()} $msg\n")
        } catch (e: Throwable) { }
    }
}
