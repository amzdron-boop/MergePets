package com.lightgames.mergepets

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** Tiny synthesized sound effects (no audio assets needed). */
class SoundFx(ctx: Context) {

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val ids = HashMap<String, Int>()

    init {
        // Each note: frequency (Hz) to duration (sec)
        load(ctx, "spawn", listOf(420.0 to 0.05, 700.0 to 0.08))
        load(ctx, "merge", listOf(523.0 to 0.07, 659.0 to 0.07, 784.0 to 0.12))
        load(ctx, "move", listOf(330.0 to 0.04))
        load(ctx, "coin", listOf(1175.0 to 0.06, 1568.0 to 0.12))
        load(ctx, "error", listOf(180.0 to 0.12))
    }

    fun play(name: String, rate: Float = 1f) {
        val id = ids[name] ?: return
        pool.play(id, 0.7f, 0.7f, 1, 0, rate.coerceIn(0.5f, 2f))
    }

    fun release() = pool.release()

    private fun load(ctx: Context, name: String, notes: List<Pair<Double, Double>>) {
        try {
            val f = File(ctx.cacheDir, "sfx_$name.wav")
            f.writeBytes(wav(notes))
            ids[name] = pool.load(f.absolutePath, 1)
        } catch (_: Exception) {
            // Sound is optional; ignore any failure.
        }
    }

    private fun wav(notes: List<Pair<Double, Double>>): ByteArray {
        val rate = 22050
        val samples = ArrayList<Short>()
        for ((freq, dur) in notes) {
            val n = (rate * dur).toInt()
            for (i in 0 until n) {
                val t = i.toDouble() / rate
                val env = exp(-t * 14.0) * (if (i < 80) i / 80.0 else 1.0)
                val v = sin(2 * PI * freq * t) * 0.6 + sin(4 * PI * freq * t) * 0.15
                samples.add((v * env * 30000).toInt().coerceIn(-32768, 32767).toShort())
            }
        }
        val dataLen = samples.size * 2
        val out = ByteArrayOutputStream()
        fun int(v: Int) { for (b in 0 until 4) out.write((v shr (8 * b)) and 0xFF) }
        fun short(v: Int) { for (b in 0 until 2) out.write((v shr (8 * b)) and 0xFF) }
        out.write("RIFF".toByteArray()); int(36 + dataLen)
        out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray()); int(16); short(1); short(1)
        int(rate); int(rate * 2); short(2); short(16)
        out.write("data".toByteArray()); int(dataLen)
        for (s in samples) short(s.toInt())
        return out.toByteArray()
    }
}
