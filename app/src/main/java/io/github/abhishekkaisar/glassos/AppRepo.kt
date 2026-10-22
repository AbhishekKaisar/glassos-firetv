package io.github.abhishekkaisar.glassos

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics

class AppEntry(val pkg: String, val label: String, val art: Bitmap?, val isBanner: Boolean, val color: Int)

object AppRepo {
    /**
     * Popular streaming apps in the order the default top row uses them. Each entry lists the package names the
     * same service uses on Fire TV and on Android TV.
     */
    val POPULAR: List<List<String>> = listOf(
        listOf("com.amazon.firetv.youtube", "com.google.android.youtube.tv"),
        listOf("com.netflix.ninja"),
        listOf("com.amazon.firebat", "com.amazon.avod", "com.amazon.amazonvideo.livingroom"),
        listOf("com.disney.disneyplus"),
        listOf("com.apple.atve.amazon.appletv", "com.apple.atve.androidtv.appletv"),
        listOf("com.spotify.tv.android"),
        listOf("com.wbd.stream", "com.hbo.hbonow"),
        listOf("com.hulu.livingroomplus", "com.hulu.plus"),
        listOf("com.cbs.ott", "com.cbs.app"),
        listOf("com.peacocktv.peacockandroid"),
        listOf("com.plexapp.android"),
        listOf("tv.twitch.android.app", "tv.twitch.android.viewer"),
        listOf("com.crunchyroll.crunchyroid")
    )

    /** The first six popular apps that are installed, in [POPULAR] order, then other apps alphabetically. */
    fun defaultDock(apps: List<AppEntry>, max: Int = HomeScreen.COLS): List<String> {
        val installed = apps.map { it.pkg }.toSet()
        val out = ArrayList<String>()
        for (alts in POPULAR) alts.firstOrNull { it in installed }?.let { out.add(it) }
        for (a in apps) if (a.pkg !in out) out.add(a.pkg)
        return out.take(max)
    }

    fun load(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        val map = LinkedHashMap<String, ResolveInfo>()
        for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val list = try { pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(cat), 0) } catch (e: Exception) { emptyList<ResolveInfo>() }
            for (ri in list) {
                val pkg = ri.activityInfo.packageName
                if (pkg == ctx.packageName || map.containsKey(pkg)) continue
                map[pkg] = ri
            }
        }
        return map.values.map { entry(ctx, it) }.sortedBy { it.label.lowercase() }
    }

    private fun entry(ctx: Context, ri: ResolveInfo): AppEntry {
        val pm = ctx.packageManager
        val ai = ri.activityInfo
        val label = (ri.loadLabel(pm)?.toString() ?: ai.packageName).trim()
        var d: Drawable? = null
        var banner = false
        try {
            val res = pm.getResourcesForApplication(ai.applicationInfo)
            val bannerId = if (ai.banner != 0) ai.banner else ai.applicationInfo.banner
            if (bannerId != 0) {
                d = try { res.getDrawableForDensity(bannerId, DisplayMetrics.DENSITY_XHIGH, null) } catch (e: Exception) { null }
                banner = d != null
            }
            if (d == null) {
                val iconId = ri.iconResource
                if (iconId != 0) d = try { res.getDrawableForDensity(iconId, DisplayMetrics.DENSITY_XXXHIGH, null) } catch (e: Exception) { null }
            }
        } catch (e: Exception) { }
        if (d == null) d = try { ri.loadIcon(pm) } catch (e: Exception) { null }
        val art = d?.let { if (banner) render(it, 400, 225) else iconArt(it) }
        val color = if (art != null && !banner) dominant(art) else 0xFF2A3350.toInt()
        return AppEntry(ai.packageName, label, art, banner, color)
    }

    /** Average of the opaque, reasonably saturated pixels, used as the backdrop of icon-only tiles. */
    private fun dominant(b: Bitmap): Int {
        val small = Bitmap.createScaledBitmap(b, 24, 24, true)
        var r = 0L; var g = 0L; var bl = 0L; var n = 0L
        var r2 = 0L; var g2 = 0L; var b2 = 0L; var n2 = 0L
        val hsv = FloatArray(3)
        for (y in 0 until 24) for (x in 0 until 24) {
            val c = small.getPixel(x, y)
            if (android.graphics.Color.alpha(c) < 200) continue
            android.graphics.Color.colorToHSV(c, hsv)
            r2 += android.graphics.Color.red(c); g2 += android.graphics.Color.green(c); b2 += android.graphics.Color.blue(c); n2++
            if (hsv[1] < 0.25f || hsv[2] < 0.15f) continue
            r += android.graphics.Color.red(c); g += android.graphics.Color.green(c); bl += android.graphics.Color.blue(c); n++
        }
        if (small !== b) small.recycle()
        if (n < 8) { r = r2; g = g2; bl = b2; n = n2 }
        if (n == 0L) return 0xFF2A3350.toInt()
        val c = android.graphics.Color.rgb((r / n).toInt(), (g / n).toInt(), (bl / n).toInt())
        android.graphics.Color.colorToHSV(c, hsv)
        hsv[2] = hsv[2].coerceIn(0.28f, 0.62f)
        return android.graphics.Color.HSVToColor(hsv)
    }

    private fun iconArt(d: Drawable): Bitmap {
        val s = 192
        if (d is BitmapDrawable && d.bitmap != null && d.bitmap.width == s && d.bitmap.height == s) return d.bitmap
        return render(d, s, s)
    }

    private fun render(d: Drawable, w: Int, h: Int): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val iw = d.intrinsicWidth
        val ih = d.intrinsicHeight
        if (iw > 0 && ih > 0 && (iw * h != ih * w)) {
            // Fit inside, keeping the aspect ratio.
            val scale = minOf(w / iw.toFloat(), h / ih.toFloat())
            val dw = (iw * scale).toInt()
            val dh = (ih * scale).toInt()
            d.setBounds((w - dw) / 2, (h - dh) / 2, (w + dw) / 2, (h + dh) / 2)
        } else d.setBounds(0, 0, w, h)
        d.draw(c)
        return b
    }
}
