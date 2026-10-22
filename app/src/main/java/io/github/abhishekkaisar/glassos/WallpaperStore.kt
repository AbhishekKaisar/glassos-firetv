package io.github.abhishekkaisar.glassos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Environment
import java.io.File
import java.io.FileOutputStream

class Wallpaper(val sharp: Bitmap, val blurred: Bitmap)

object WallpaperStore {
    private const val GEN = 9
    val scenes = listOf(
        "scene:desert" to "Night desert",
        "scene:ocean" to "Dusk ocean",
        "scene:aurora" to "Aurora",
        "scene:dark" to "Dark"
    )

    fun userDir(): File = File(Environment.getExternalStorageDirectory(), "Pictures/Glass")

    fun userImages(): List<File> {
        val exts = setOf("jpg", "jpeg", "png", "webp")
        return try {
            userDir().listFiles()?.filter { it.isFile && it.extension.lowercase() in exts }?.sortedBy { it.name.lowercase() } ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    val heroModes = listOf(
        HeroRepo.PHOTOS to "My photos"
    )

    fun label(id: String): String =
        heroModes.firstOrNull { it.first == id }?.second ?:
        scenes.firstOrNull { it.first == id }?.second ?: File(id.removePrefix("file:")).nameWithoutExtension

    fun load(ctx: Context, id: String, w: Int, h: Int): Wallpaper {
        val sharp = if (id.startsWith("file:")) decodeCrop(File(id.removePrefix("file:")), w, h) ?: scene(ctx, "desert", w, h)
        else scene(ctx, id.removePrefix("scene:"), w, h)
        val blurFile = File(ctx.filesDir, "blur_${key(id)}_${GEN}_${w}x$h.png")
        val cached = if (blurFile.exists() && !id.startsWith("file:")) BitmapFactory.decodeFile(blurFile.path) else null
        val blurred = cached ?: Blur.makeBackdrop(sharp).also { b ->
            if (!id.startsWith("file:")) save(b, blurFile, Bitmap.CompressFormat.PNG)
        }
        return Wallpaper(sharp, blurred)
    }

    private fun key(id: String) = id.replace(Regex("[^A-Za-z0-9]"), "_").takeLast(60)

    private fun scene(ctx: Context, name: String, w: Int, h: Int): Bitmap {
        val f = File(ctx.filesDir, "wall_${name}_${GEN}_${w}x$h.png")
        if (f.exists()) {
            BitmapFactory.decodeFile(f.path)?.let { return it }
        }
        val b = WallpaperGen.render(name, w, h)
        save(b, f, Bitmap.CompressFormat.PNG)
        return b
    }

    private fun save(b: Bitmap, f: File, fmt: Bitmap.CompressFormat) {
        try {
            val tmp = File(f.path + ".tmp")
            FileOutputStream(tmp).use { b.compress(fmt, 100, it) }
            tmp.renameTo(f)
        } catch (e: Exception) { }
    }

    /** Thumbnail for the settings picker. */
    fun thumb(ctx: Context, id: String, w: Int, h: Int): Bitmap? {
        if (id.startsWith("file:")) return decodeCrop(File(id.removePrefix("file:")), w, h)
        val name = id.removePrefix("scene:")
        val tf = File(ctx.filesDir, "thumb_${name}_${GEN}_${w}x$h.png")
        if (tf.exists()) BitmapFactory.decodeFile(tf.path)?.let { return it }
        val b = WallpaperGen.render(name, w * 2, h * 2)
        val t = Bitmap.createScaledBitmap(b, w, h, true)
        b.recycle()
        save(t, tf, Bitmap.CompressFormat.PNG)
        return t
    }

    fun decodeCrop(f: File, w: Int, h: Int): Bitmap? {
        return try {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, o)
            if (o.outWidth <= 0 || o.outHeight <= 0) return null
            var sample = 1
            while (o.outWidth / (sample * 2) >= w && o.outHeight / (sample * 2) >= h) sample *= 2
            val src = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
            val scale = maxOf(w / src.width.toFloat(), h / src.height.toFloat())
            val cw = (w / scale).toInt()
            val ch = (h / scale).toInt()
            val sx = (src.width - cw) / 2
            val sy = (src.height - ch) / 2
            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            Canvas(out).drawBitmap(src, Rect(sx, sy, sx + cw, sy + ch), Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
            src.recycle()
            // Opaque: HWUI then draws it without blending (the shown slide covers the whole screen).
            out.setHasAlpha(false)
            out
        } catch (e: Throwable) { null }
    }
}
