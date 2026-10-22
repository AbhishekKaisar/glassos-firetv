package io.github.abhishekkaisar.glassos

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** A soft synthesised focus tick, played through SoundPool so it ignores the system click setting. */
class Sound(ctx: Context) {
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .build()
    @Volatile private var loaded = false
    private var id = 0
    private var last = 0L

    init {
        val f = File(ctx.cacheDir, "tick_v1.wav")
        if (!f.exists()) writeTick(f)
        pool.setOnLoadCompleteListener { _, _, status -> loaded = status == 0 }
        id = pool.load(f.path, 1)
    }

    fun tick() {
        val now = System.currentTimeMillis()
        if (!loaded || now - last < 40) return
        last = now
        pool.play(id, 0.32f, 0.32f, 1, 0, 1f)
    }

    fun release() = pool.release()

    private fun writeTick(f: File) {
        val rate = 44100
        val n = (rate * 0.045).toInt()
        val data = ByteBuffer.allocate(n * 2).order(ByteOrder.LITTLE_ENDIAN)
        val rnd = java.util.Random(1)
        for (i in 0 until n) {
            val t = i / rate.toDouble()
            val env = exp(-t * 120.0) * (1 - exp(-t * 4000.0))
            val v = (sin(2 * PI * 1650 * t) * 0.55 + sin(2 * PI * 3300 * t) * 0.18 + (rnd.nextDouble() - 0.5) * 0.25 * exp(-t * 900.0)) * env
            data.putShort((v.coerceIn(-1.0, 1.0) * 26000).toInt().toShort())
        }
        val hdr = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        hdr.put("RIFF".toByteArray()); hdr.putInt(36 + n * 2); hdr.put("WAVE".toByteArray())
        hdr.put("fmt ".toByteArray()); hdr.putInt(16); hdr.putShort(1); hdr.putShort(1)
        hdr.putInt(rate); hdr.putInt(rate * 2); hdr.putShort(2); hdr.putShort(16)
        hdr.put("data".toByteArray()); hdr.putInt(n * 2)
        FileOutputStream(f).use { it.write(hdr.array()); it.write(data.array()) }
    }
}
