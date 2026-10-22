package io.github.abhishekkaisar.glassos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import java.io.File

/** The profile photo and the default avatar. */
object Profile {
    fun photoFile(ctx: Context) = File(ctx.filesDir, "avatar.png")

    /** Saves [src] (any size) as a 320 px centre-cropped square. Returns false when it cannot be read. */
    fun savePhoto(ctx: Context, src: File): Boolean = try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(src.path, o)
        var sample = 1
        while (minOf(o.outWidth, o.outHeight) / (sample * 2) >= 320) sample *= 2
        val b = BitmapFactory.decodeFile(src.path, BitmapFactory.Options().apply { inSampleSize = sample })
        if (b == null) false else {
            val side = minOf(b.width, b.height)
            val sq = Bitmap.createBitmap(b, (b.width - side) / 2, (b.height - side) / 2, side, side)
            val out = Bitmap.createScaledBitmap(sq, 320, 320, true)
            val tmp = File(photoFile(ctx).path + ".tmp")
            tmp.outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
            tmp.renameTo(photoFile(ctx))
        }
    } catch (e: Throwable) { false }

    fun clearPhoto(ctx: Context) { photoFile(ctx).delete() }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    /**
     * The default avatar: a frosted glass disc with a soft person silhouette, drawn with paths (no letter, no
     * image). Centre ([cx], [cy]), radius [r].
     */
    fun drawSilhouette(c: Canvas, cx: Float, cy: Float, r: Float) {
        fill.shader = LinearGradient(cx - r, cy - r, cx + r, cy + r, 0xFF8E9AAF.toInt(), 0xFF4A5468.toInt(), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, fill)
        // A light sheen from the top left, as on the other glass.
        fill.shader = RadialGradient(cx - r * 0.4f, cy - r * 0.55f, r * 1.2f, 0x55FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, fill)
        fill.shader = null
        fill.color = 0xE6FFFFFF.toInt()
        c.save()
        path.reset()
        path.addCircle(cx, cy, r, Path.Direction.CW)
        c.clipPath(path)
        // Head.
        c.drawCircle(cx, cy - r * 0.18f, r * 0.30f, fill)
        // Shoulders: a wide rounded body cut by the disc.
        c.drawOval(cx - r * 0.62f, cy + r * 0.24f, cx + r * 0.62f, cy + r * 1.30f, fill)
        c.restore()
    }

    /** The silhouette as a bitmap, for places that draw avatars as images. */
    fun silhouette(size: Int): Bitmap {
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        drawSilhouette(Canvas(b), size / 2f, size / 2f, size / 2f)
        return b
    }
}
