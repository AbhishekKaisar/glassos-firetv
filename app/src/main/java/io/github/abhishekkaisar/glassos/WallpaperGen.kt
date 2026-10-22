package io.github.abhishekkaisar.glassos

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

/** Procedural scenes, drawn in a 1920x1080 design space and scaled to the target size. */
object WallpaperGen {
    private const val W = 1920f
    private const val H = 1080f

    fun render(scene: String, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(w / W, h / H)
        when (scene) {
            "ocean" -> ocean(c)
            "aurora" -> aurora(c, bmp)
            "dark" -> dark(c)
            else -> desert(c)
        }
        val g = Paint().apply { shader = Grain.newShader(); alpha = 60 }
        c.drawRect(0f, 0f, W, H, g)
        return bmp
    }

    private fun paint() = Paint(Paint.ANTI_ALIAS_FLAG).apply { isDither = true }

    private fun Random.gauss(): Float = nextGaussian().toFloat()
    private fun Random.f(): Float = nextFloat()
    private fun Random.range(a: Float, b: Float): Float = a + nextFloat() * (b - a)

    private fun vGrad(c: Canvas, top: Float, bottom: Float, colors: IntArray, pos: FloatArray?, y0: Float = 0f, y1: Float = H) {
        val p = paint()
        p.shader = LinearGradient(0f, top, 0f, bottom, colors, pos, Shader.TileMode.CLAMP)
        c.drawRect(0f, y0, W, y1, p)
    }

    private fun glow(c: Canvas, cx: Float, cy: Float, r: Float, color: Int, a: Float, sy: Float = 1f) {
        val p = paint()
        p.shader = RadialGradient(0f, 0f, 1f, intArrayOf(alphaOf(color, a), alphaOf(color, a * 0.45f), alphaOf(color, 0f)),
            floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
        c.save()
        c.translate(cx, cy)
        c.scale(r, r * sy)
        c.drawCircle(0f, 0f, 1f, p)
        c.restore()
    }

    private val starTints = intArrayOf(Color.WHITE, 0xFFD6E2FF.toInt(), 0xFFFFF1DC.toInt(), 0xFFE8EEFF.toInt())

    private fun stars(c: Canvas, rnd: Random, count: Int, maxY: Float, bright: Int, fade: Float = 1f) {
        val p = paint()
        repeat(count) {
            val x = rnd.f() * W
            val y = maxY * rnd.f().pow(1.3f)
            val r = 0.5f + rnd.f().pow(6f) * 1.9f
            val horizonFade = 1f - (y / maxY).pow(3f) * 0.75f
            p.color = alphaOf(starTints[rnd.nextInt(starTints.size)], (0.22f + rnd.f() * 0.78f) * horizonFade * fade)
            c.drawCircle(x, y, r, p)
        }
        repeat(bright) {
            val x = rnd.f() * W
            val y = maxY * 0.85f * rnd.f().pow(1.2f)
            val tint = starTints[rnd.nextInt(starTints.size)]
            glow(c, x, y, rnd.range(8f, 18f), tint, 0.45f * fade)
            p.color = alphaOf(Color.WHITE, 0.95f * fade)
            c.drawCircle(x, y, rnd.range(1.4f, 2.3f), p)
            if (rnd.nextInt(3) == 0) {
                p.strokeWidth = 0.8f
                p.color = alphaOf(tint, 0.35f * fade)
                val l = rnd.range(9f, 16f)
                c.drawLine(x - l, y, x + l, y, p)
                c.drawLine(x, y - l, x, y + l, p)
            }
        }
    }

    private fun milkyWay(c: Canvas, rnd: Random, x0: Float, y0: Float, x1: Float, y1: Float, width: Float) {
        val dx = x1 - x0
        val dy = y1 - y0
        val len = hypot(dx, dy)
        val nx = -dy / len
        val ny = dx / len
        val cols = intArrayOf(0xFF9FB4FF.toInt(), 0xFFC9B8FF.toInt(), 0xFFFFE2C8.toInt(), 0xFF8FA8E8.toInt())
        fun at(t: Float, off: Float, out: FloatArray) {
            out[0] = x0 + dx * t + nx * off
            out[1] = y0 + dy * t + ny * off
        }
        val pt = FloatArray(2)
        repeat(240) {
            val t = rnd.f()
            at(t, rnd.gauss() * width * 0.75f, pt)
            glow(c, pt[0], pt[1], rnd.range(70f, 230f), cols[rnd.nextInt(cols.size)], rnd.range(0.025f, 0.06f))
        }
        repeat(120) {
            val t = rnd.f()
            at(t, rnd.gauss() * width * 0.3f, pt)
            glow(c, pt[0], pt[1], rnd.range(25f, 80f), 0xFFF2ECFF.toInt(), rnd.range(0.04f, 0.09f))
        }
        repeat(90) {
            val t = rnd.f()
            at(t, rnd.gauss() * width * 0.12f + width * 0.1f, pt)
            glow(c, pt[0], pt[1], rnd.range(18f, 60f), 0xFF060818.toInt(), rnd.range(0.18f, 0.35f), 0.6f)
        }
        val p = paint()
        repeat(2800) {
            val t = rnd.f()
            at(t, rnd.gauss() * width * 0.55f, pt)
            p.color = alphaOf(starTints[rnd.nextInt(starTints.size)], rnd.range(0.2f, 0.85f))
            c.drawCircle(pt[0], pt[1], 0.4f + rnd.f().pow(4f) * 1.1f, p)
        }
    }

    private fun moon(c: Canvas, cx: Float, cy: Float, r: Float) {
        glow(c, cx, cy, 560f, 0xFFA8B8FF.toInt(), 0.16f)
        glow(c, cx, cy, r * 4.5f, 0xFFDDE4FF.toInt(), 0.32f)
        val p = paint()
        p.shader = RadialGradient(cx - r * 0.3f, cy - r * 0.3f, r * 1.4f,
            intArrayOf(0xFFFFFDF4.toInt(), 0xFFEDE8DA.toInt(), 0xFFD9D3C4.toInt()), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, p)
        p.shader = null
        p.color = 0x16000000
        c.drawCircle(cx - r * 0.25f, cy - r * 0.15f, r * 0.32f, p)
        c.drawCircle(cx + r * 0.3f, cy + r * 0.25f, r * 0.22f, p)
        c.drawCircle(cx + r * 0.05f, cy + r * 0.45f, r * 0.16f, p)
    }

    // ---------- Scenes ----------

    private fun desert(c: Canvas) {
        val rnd = Random(42)
        vGrad(c, 0f, H * 0.70f,
            intArrayOf(0xFF02041A.toInt(), 0xFF060C33.toInt(), 0xFF101A52.toInt(), 0xFF232C6C.toInt(), 0xFF4B3D7C.toInt(), 0xFF9A5E78.toInt()),
            floatArrayOf(0f, 0.22f, 0.45f, 0.66f, 0.84f, 1f))
        milkyWay(c, rnd, -200f, 780f, 2150f, -200f, 150f)
        stars(c, rnd, 2300, H * 0.66f, 28)
        moon(c, 1460f, 290f, 28f)
        glow(c, W * 0.40f, H * 0.64f, W * 0.75f, 0xFFFF9A5A.toInt(), 0.32f, 0.30f)
        glow(c, W * 0.72f, H * 0.62f, W * 0.45f, 0xFFFF7A6A.toInt(), 0.18f, 0.28f)

        dune(c, rnd, H * 0.63f, 46f, 330f, 0xFFB86A4C.toInt(), 0xFF6E3328.toInt(), 0xFF8A6A9A.toInt(), 0.40f, 0.35f)
        dune(c, rnd, H * 0.725f, 90f, 520f, 0xFFD87A42.toInt(), 0xFF7E3520.toInt(), 0xFF9A6A8A.toInt(), 0.22f, 0.45f)
        dune(c, rnd, H * 0.85f, 130f, 700f, 0xFFF09250.toInt(), 0xFF8E3C1A.toInt(), 0xFFB0708A.toInt(), 0.08f, 0.55f)
        dune(c, rnd, H * 0.99f, 110f, 900f, 0xFFD2702F.toInt(), 0xFF5A200C.toInt(), 0, 0f, 0.55f)
    }

    private fun dune(c: Canvas, rnd: Random, baseY: Float, amp: Float, lambda: Float, top: Int, bottom: Int, haze: Int, hazeA: Float, shade: Float) {
        val step = 3f
        val n = (W / step).toInt() + 3
        val xs = FloatArray(n)
        val ys = FloatArray(n)
        val ph = rnd.f() * 10f
        val ph2 = rnd.f() * 10f
        var minY = H
        for (i in 0 until n) {
            val x = i * step
            // Asymmetric profile: long concave windward slope up to a sharp crest, then a short steep slip face.
            val warp = x + lambda * 0.28f * sin(x / (lambda * 1.3f) + ph2) + 30f * sin(x / 150f + ph)
            var u = warp / lambda + ph
            u -= kotlin.math.floor(u)
            val k = 0.68f
            val crest = if (u < k) (u / k).pow(1.7f) else (1f - (u - k) / (1f - k)).pow(1.35f)
            val swell = 0.55f + 0.45f * sin(x / (lambda * 1.7f) + ph2 * 0.7f)
            xs[i] = x
            ys[i] = baseY - amp * crest * swell - 0.3f * amp * sin(x / 1300f + ph)
            if (ys[i] < minY) minY = ys[i]
        }
        val path = Path()
        path.moveTo(0f, H)
        for (i in 0 until n) path.lineTo(xs[i], ys[i])
        path.lineTo(W, H)
        path.close()
        val depth = (baseY - minY) + amp * 1.6f + 60f
        val p = paint()
        p.shader = LinearGradient(0f, minY, 0f, minY + depth, intArrayOf(top, lerpColor(top, bottom, 0.5f), bottom), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        c.drawPath(path, p)

        // Slope shading: faces descending to the right fall into shadow, rising faces catch the light.
        // Drawn at quarter size, blurred, then used to fill the dune shape so it follows the surface smoothly.
        val q = 4f
        val mask = Bitmap.createBitmap((W / q).toInt(), (H / q).toInt(), Bitmap.Config.ARGB_8888)
        val mc = Canvas(mask)
        mc.scale(1f / q, 1f / q)
        val sp = paint()
        val dark = 0xFF2A0C04.toInt()
        val lit = 0xFFFFC48A.toInt()
        for (i in 1 until n - 1) {
            val slope = (ys[i + 1] - ys[i - 1]) / (2f * step)
            val y = ys[i]
            val len = depth * 0.9f
            if (slope > 0.02f) {
                val a = (slope * 1.6f).coerceAtMost(1f) * shade
                sp.shader = LinearGradient(0f, y, 0f, y + len, intArrayOf(alphaOf(dark, a), alphaOf(dark, a * 0.55f), alphaOf(dark, 0f)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            } else if (slope < -0.02f) {
                val a = ((-slope) * 1.2f).coerceAtMost(1f) * shade * 0.55f
                sp.shader = LinearGradient(0f, y, 0f, y + len * 0.6f, intArrayOf(alphaOf(lit, a), alphaOf(lit, 0f)), null, Shader.TileMode.CLAMP)
            } else continue
            mc.drawRect(xs[i] - step / 2f - 1f, y - 6f, xs[i] + step / 2f + 1f, y + len, sp)
        }
        Blur.boxBlur(mask, 2)
        val mp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val ms = android.graphics.BitmapShader(mask, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        ms.setLocalMatrix(Matrix().apply { setScale(q, q) })
        mp.shader = ms
        c.drawPath(path, mp)
        mask.recycle()

        // Rim light along the crest line.
        val rim = Path()
        rim.moveTo(xs[0], ys[0])
        for (k in 1 until n) rim.lineTo(xs[k], ys[k])
        val rp = paint()
        rp.style = Paint.Style.STROKE
        rp.strokeWidth = 1.4f
        rp.color = alphaOf(0xFFFFD0A0.toInt(), 0.30f)
        c.drawPath(rim, rp)

        if (hazeA > 0f) {
            val hp = paint()
            hp.shader = LinearGradient(0f, minY, 0f, minY + amp * 2.2f, intArrayOf(alphaOf(haze, hazeA), alphaOf(haze, 0f)), null, Shader.TileMode.CLAMP)
            c.drawPath(path, hp)
        }
    }

    private fun ocean(c: Canvas) {
        val rnd = Random(11)
        val hz = H * 0.64f
        vGrad(c, 0f, hz,
            intArrayOf(0xFF081236.toInt(), 0xFF22286A.toInt(), 0xFF5E3A80.toInt(), 0xFFB4527C.toInt(), 0xFFEE8A5E.toInt(), 0xFFFFC27A.toInt()),
            floatArrayOf(0f, 0.28f, 0.52f, 0.74f, 0.9f, 1f), 0f, hz)
        stars(c, rnd, 600, H * 0.34f, 6, 0.8f)
        val sunX = W * 0.64f
        glow(c, sunX, hz, 760f, 0xFFFF9C5A.toInt(), 0.50f, 0.65f)
        glow(c, sunX, hz, 240f, 0xFFFFD49A.toInt(), 0.65f)
        val p = paint()
        p.shader = RadialGradient(sunX, hz, 66f, intArrayOf(0xFFFFF4D6.toInt(), 0xFFFFD08A.toInt()), null, Shader.TileMode.CLAMP)
        c.drawCircle(sunX, hz + 10f, 66f, p)
        p.shader = null
        repeat(16) {
            val y = rnd.range(H * 0.30f, H * 0.58f)
            val col = if (rnd.nextBoolean()) 0xFFFFA4A0.toInt() else 0xFFFFC4A0.toInt()
            glow(c, rnd.range(0f, W), y, rnd.range(220f, 560f), col, rnd.range(0.10f, 0.22f), rnd.range(0.025f, 0.05f))
        }
        vGrad(c, hz, H,
            intArrayOf(0xFF8A5A86.toInt(), 0xFF3A2E66.toInt(), 0xFF141A44.toInt(), 0xFF060A20.toInt()),
            floatArrayOf(0f, 0.22f, 0.6f, 1f), hz, H)
        val lp = paint()
        repeat(700) {
            val d = rnd.f().pow(1.5f)
            val y = hz + 2f + d * (H - hz)
            val x = rnd.f() * W
            lp.color = alphaOf(0xFF9AB0FF.toInt(), rnd.range(0.03f, 0.08f) * (1f - d * 0.5f))
            val l = rnd.range(20f, 60f + d * 220f)
            c.drawRect(x, y, x + l, y + 1f + d * 2.5f, lp)
        }
        repeat(1100) {
            val d = rnd.f().pow(1.6f)
            val y = hz + 3f + d * (H - hz)
            val spread = 30f + d * 460f
            val x = sunX + rnd.gauss() * spread
            val l = rnd.range(10f, 40f + d * 170f)
            lp.color = alphaOf(0xFFFFD49A.toInt(), (1f - d) * 0.65f * rnd.range(0.3f, 1f))
            c.drawRect(x - l / 2, y, x + l / 2, y + 1.2f + d * 3.5f, lp)
        }
        lp.color = alphaOf(0xFFFFD0A0.toInt(), 0.45f)
        c.drawRect(0f, hz - 1f, W, hz + 1.5f, lp)
    }

    private fun aurora(c: Canvas, bmp: Bitmap) {
        val rnd = Random(23)
        val water = H * 0.84f
        vGrad(c, 0f, water,
            intArrayOf(0xFF01030A.toInt(), 0xFF020C1C.toInt(), 0xFF052234.toInt(), 0xFF0A3644.toInt()),
            floatArrayOf(0f, 0.35f, 0.75f, 1f), 0f, water)
        stars(c, rnd, 1700, H * 0.72f, 18)
        curtain(c, H * 0.36f, 260f, 0f, 0xFF3DFFB0.toInt(), 0xFF9B5CFF.toInt(), 1f)
        curtain(c, H * 0.30f, 200f, 2.1f, 0xFF4DE8FF.toInt(), 0xFFFF6AD5.toInt(), 0.6f)
        repeat(36) {
            val x = rnd.f() * W
            glow(c, x, H * 0.42f + rnd.gauss() * 60f, rnd.range(120f, 260f), 0xFF3DFFB0.toInt(), 0.06f, 0.6f)
        }
        mountains(c, rnd, H * 0.72f, 120f, 0xFF071822.toInt(), water)
        mountains(c, rnd, H * 0.79f, 70f, 0xFF02080D.toInt(), water)

        // Lake: mirror what is above the waterline.
        val sy = bmp.height / H
        val wy = (water * sy).toInt()
        val wh = bmp.height - wy
        if (wh > 0 && wy - wh >= 0) {
            val src = Bitmap.createBitmap(bmp, 0, wy - wh, bmp.width, wh)
            c.save()
            c.setMatrix(null)
            val m = Matrix()
            m.setScale(1f, -1f)
            m.postTranslate(0f, (wy + wh).toFloat())
            c.drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = 120 })
            c.restore()
            src.recycle()
        }
        val p = paint()
        p.shader = LinearGradient(0f, water, 0f, H, intArrayOf(0x55000610, 0xDD00040A.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, water, W, H, p)
        p.shader = null
        repeat(160) {
            val y = water + rnd.f().pow(1.4f) * (H - water)
            val x = rnd.f() * W
            p.color = alphaOf(0xFF8CFFD8.toInt(), rnd.range(0.03f, 0.09f))
            c.drawRect(x, y, x + rnd.range(30f, 180f), y + 1.2f, p)
        }
    }

    private fun curtain(c: Canvas, baseY: Float, height: Float, ph: Float, low: Int, high: Int, strength: Float) {
        val p = paint()
        var x = -10f
        while (x < W + 10f) {
            val by = baseY + 95f * sin(x / 270f + ph) + 32f * sin(x / 83f + ph * 2f) + 120f
            val hgt = height + 90f * sin(x / 190f + 0.5f + ph)
            val k = 0.35f + 0.65f * sin(x / 140f + 0.3f + ph).pow(2) * (0.6f + 0.4f * sin(x / 37f + ph))
            val a = (k * strength).coerceIn(0f, 1f)
            p.shader = LinearGradient(0f, by - hgt, 0f, by + 24f,
                intArrayOf(alphaOf(high, 0f), alphaOf(high, 0.10f * a), alphaOf(low, 0.22f * a), alphaOf(low, 0.42f * a), alphaOf(low, 0f)),
                floatArrayOf(0f, 0.25f, 0.7f, 0.92f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(x, by - hgt, x + 3.5f, by + 24f, p)
            x += 3f
        }
    }

    private fun mountains(c: Canvas, rnd: Random, baseY: Float, amp: Float, color: Int, floor: Float) {
        val path = Path()
        path.moveTo(0f, floor)
        var x = 0f
        var y = baseY
        val ph = rnd.f() * 6f
        while (x <= W + 20f) {
            val target = baseY - amp * (0.5f + 0.5f * sin(x / 260f + ph)) * (0.6f + 0.4f * sin(x / 610f + ph * 1.7f))
            y += (target - y) * 0.45f + rnd.gauss() * 7f
            path.lineTo(x, y)
            x += rnd.range(10f, 26f)
        }
        path.lineTo(W, floor)
        path.close()
        val p = paint()
        p.color = color
        c.drawPath(path, p)
    }

    private fun dark(c: Canvas) {
        vGrad(c, 0f, H, intArrayOf(0xFF0B0D18.toInt(), 0xFF07080F.toInt()), null)
        glow(c, W * 0.16f, H * 0.12f, 1050f, 0xFF3048B0.toInt(), 0.55f)
        glow(c, W * 0.88f, H * 0.26f, 820f, 0xFF5A2A9A.toInt(), 0.45f)
        glow(c, W * 0.62f, H * 1.05f, 1000f, 0xFF105A8A.toInt(), 0.40f)
        glow(c, W * 0.40f, H * 0.60f, 600f, 0xFF203060.toInt(), 0.25f)
    }
}
