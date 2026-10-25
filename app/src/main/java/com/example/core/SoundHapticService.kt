package com.example.core

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/**
 * Provides low-latency synthesized sound effects (success chime & error buzz)
 * and haptic feedback (vibration on wrong answer & light tap on keypad).
 */
class SoundHapticService(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun playSuccess(enabled: Boolean) {
        if (!enabled) return
        scope.launch {
            runCatching {
                // Bright two-note ascending chime (C6 -> G6)
                playToneSequence(
                    notes = listOf(1046.5 to 55, 1567.98 to 95),
                    amplitude = 0.35
                )
            }
        }
    }

    fun playError(enabled: Boolean) {
        if (!enabled) return
        scope.launch {
            runCatching {
                // Low two-note descending error tone (E3 -> C3)
                playToneSequence(
                    notes = listOf(164.81 to 85, 130.81 to 130),
                    amplitude = 0.45
                )
            }
        }
    }

    fun vibrateError(enabled: Boolean) {
        if (!enabled) return
        runCatching {
            val v = vibrator ?: return
            if (!v.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 75, 45, 95), -1)
                )
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(160L)
            }
        }
    }

    fun vibrateTap(enabled: Boolean) {
        if (!enabled) return
        runCatching {
            val v = vibrator ?: return
            if (!v.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(12L, 60))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(10L)
            }
        }
    }

    private fun playToneSequence(notes: List<Pair<Double, Int>>, amplitude: Double) {
        val sampleRate = 22050
        val totalMs = notes.sumOf { it.second }
        val totalSamples = (sampleRate * totalMs) / 1000
        val buffer = ShortArray(totalSamples)

        var offset = 0
        for ((freq, durationMs) in notes) {
            val count = (sampleRate * durationMs) / 1000
            for (i in 0 until count) {
                if (offset + i >= totalSamples) break
                val env = if (i < count / 8) {
                    i.toDouble() / (count / 8.0)
                } else {
                    1.0 - (i.toDouble() / count.toDouble()) * 0.7
                }
                val sample = sin(2.0 * PI * i * freq / sampleRate) * amplitude * env
                buffer[offset + i] = (sample * Short.MAX_VALUE).toInt().toShort()
            }
            offset += count
        }

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(buffer.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.write(buffer, 0, buffer.size)
        track.play()
        Thread.sleep(totalMs.toLong() + 40L)
        track.stop()
        track.release()
    }
}
