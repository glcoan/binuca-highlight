package com.binuca.highlight.feedback

import android.content.Context
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.binuca.highlight.R
import com.binuca.highlight.capture.ClipDuration
import com.binuca.highlight.settings.AppSettings
import java.util.ArrayDeque

class FeedbackController(private val context: Context) : AutoCloseable {
    private val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 75)
    private val voiceQueue = ArrayDeque<ClipDuration>()
    private var player: MediaPlayer? = null
    private var voiceBusy = false

    fun success(duration: ClipDuration, settings: AppSettings) {
        if (settings.vibrationEnabled) vibrate()
        if (settings.soundEnabled) tone.startTone(ToneGenerator.TONE_PROP_ACK, 120)
        if (settings.voiceEnabled) {
            synchronized(voiceQueue) {
                voiceQueue.addLast(duration)
                if (!voiceBusy) {
                    voiceBusy = true
                    android.os.Handler(context.mainLooper).postDelayed(::playNextVoice, 150)
                }
            }
        }
    }

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        vibrator.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun playNextVoice() {
        val duration = synchronized(voiceQueue) {
            if (voiceQueue.isEmpty()) {
                voiceBusy = false
                null
            } else {
                voiceQueue.removeFirst()
            }
        } ?: return
        val resourceId = when (duration) {
            ClipDuration.TEN_SECONDS -> R.raw.clip_voice_10
            ClipDuration.TWENTY_SECONDS -> R.raw.clip_voice_20
            ClipDuration.THIRTY_SECONDS -> R.raw.clip_voice_30
        }
        player = MediaPlayer.create(context, resourceId)?.apply {
            setOnCompletionListener {
                it.release()
                player = null
                playNextVoice()
            }
            setOnErrorListener { mediaPlayer, _, _ ->
                mediaPlayer.release()
                player = null
                playNextVoice()
                true
            }
            start()
        }
    }

    override fun close() {
        tone.release()
        player?.release()
        player = null
        synchronized(voiceQueue) {
            voiceQueue.clear()
            voiceBusy = false
        }
    }
}
