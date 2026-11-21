package com.example.engine

import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log

class ArcadeSoundPlayer {
    private var generator: ToneGenerator? = null
    var soundEnabled: Boolean = true

    private fun ensureGenerator(): ToneGenerator? {
        if (!soundEnabled) return null
        if (generator == null) {
            generator = try {
                ToneGenerator(AudioManager.STREAM_MUSIC, 55)
            } catch (e: Exception) {
                Log.w("ArcadeSoundPlayer", "Audio ToneGenerator unavailable: ${e.message}")
                null
            }
        }
        return generator
    }

    fun playMatch(combo: Int) {
        val tone = when {
            combo >= 4 -> ToneGenerator.TONE_PROP_BEEP2
            combo >= 2 -> ToneGenerator.TONE_PROP_ACK
            else -> ToneGenerator.TONE_PROP_BEEP
        }
        try {
            ensureGenerator()?.startTone(tone, 65)
        } catch (_: Exception) {}
    }

    fun playAbilityBlast() {
        try {
            ensureGenerator()?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 160)
        } catch (_: Exception) {}
    }

    fun playRouletteTick() {
        try {
            ensureGenerator()?.startTone(ToneGenerator.TONE_DTMF_1, 30)
        } catch (_: Exception) {}
    }

    fun playVictory() {
        try {
            ensureGenerator()?.startTone(ToneGenerator.TONE_CDMA_CONFIRM, 240)
        } catch (_: Exception) {}
    }

    fun release() {
        try {
            generator?.release()
        } catch (_: Exception) {}
        generator = null
    }
}
