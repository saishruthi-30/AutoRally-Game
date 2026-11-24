package com.autorally

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Sound
import kotlin.math.PI
import kotlin.math.sin

/** Sound effects synthesised in code, so the APK needs no audio files. */
class Sfx {
    var muted = false
    val coin = make("coin", 900f, 1400f, 110)
    val crash = make("crash", 0f, 0f, 260, noise = true)
    val boost = make("boost", 300f, 1100f, 350)
    val beep = make("beep", 440f, 440f, 130)
    val go = make("go", 880f, 880f, 380)
    val lap = make("lap", 600f, 900f, 200)
    val win = make("win", 500f, 1300f, 700)
    val buy = make("buy", 700f, 1600f, 320)

    fun play(s: Sound) { if (!muted) s.play(0.8f) }

    private fun make(name: String, f0: Float, f1: Float, ms: Int, noise: Boolean = false): Sound {
        val rate = 22050
        val n = rate * ms / 1000
        val b = ByteArray(44 + n * 2)
        fun str(o: Int, t: String) { for (i in t.indices) b[o + i] = t[i].code.toByte() }
        fun i32(o: Int, v: Int) { for (k in 0..3) b[o + k] = (v shr (8 * k)).toByte() }
        fun i16(o: Int, v: Int) { b[o] = v.toByte(); b[o + 1] = (v shr 8).toByte() }
        str(0, "RIFF"); i32(4, 36 + n * 2); str(8, "WAVE"); str(12, "fmt ")
        i32(16, 16); i16(20, 1); i16(22, 1); i32(24, rate); i32(28, rate * 2); i16(32, 2); i16(34, 16)
        str(36, "data"); i32(40, n * 2)
        val rnd = java.util.Random(1)
        var ph = 0.0
        for (i in 0 until n) {
            val p = i.toFloat() / n
            ph += 2.0 * PI * (f0 + (f1 - f0) * p) / rate
            val v = if (noise) rnd.nextFloat() * 2f - 1f else sin(ph).toFloat()
            i16(44 + i * 2, (v * (1f - p) * 0.5f * 32767f).toInt())
        }
        val fh = Gdx.files.local("sfx_$name.wav")
        fh.writeBytes(b, false)
        return Gdx.audio.newSound(fh)
    }

    fun dispose() {
        listOf(coin, crash, boost, beep, go, lap, win, buy).forEach { it.dispose() }
    }
}
