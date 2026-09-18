package com.binuca.highlight.feedback

import android.content.Context
import android.media.MediaPlayer
import android.media.AudioAttributes
import android.util.Log
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.binuca.highlight.R
import com.binuca.highlight.capture.ClipDuration
import com.binuca.highlight.settings.AppSettings
import java.util.ArrayDeque

class FeedbackController(private val context: Context) : AutoCloseable {
    private val queue = ArrayDeque<List<Int>>()
    private val players = mutableSetOf<MediaPlayer>()
    private var closed = false
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    fun success(duration: ClipDuration, settings: AppSettings) {
        if (closed) return
        if (settings.vibrationEnabled) runCatching { vibrate() }
        val resources = buildList {
            if (settings.soundEnabled) add(R.raw.cash_register_purchase)
            if (settings.voiceEnabled) add(when (duration) {
                ClipDuration.TEN_SECONDS -> R.raw.clip_voice_10
                ClipDuration.TWENTY_SECONDS -> R.raw.clip_voice_20
                ClipDuration.THIRTY_SECONDS -> R.raw.clip_voice_30
            })
        }
        if (resources.isEmpty()) return
        queue.addLast(resources)
        if (players.isEmpty()) playNext()
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

    // Called on the main thread. Each clip's enabled sounds start together;
    // subsequent confirmations wait until both players have finished.
    private fun playNext() {
        while (!closed && players.isEmpty() && queue.isNotEmpty()) {
            val prepared = queue.removeFirst().mapNotNull { resource ->
                val player = MediaPlayer()
                try {
                    player.setAudioAttributes(audioAttributes)
                    context.resources.openRawResourceFd(resource).use { asset ->
                        player.setDataSource(asset.fileDescriptor, asset.startOffset, asset.length)
                    }
                    player.prepare()
                    player.setOnCompletionListener { finish(it) }
                    player.setOnErrorListener { failed, what, extra ->
                        Log.w("BinucaFeedback", "Audio playback failed: $what/$extra")
                        finish(failed)
                        true
                    }
                    player
                } catch (error: Exception) {
                    runCatching { player.release() }
                    Log.w("BinucaFeedback", "Could not prepare confirmation audio", error)
                    null
                }
            }
            players.addAll(prepared)
            prepared.forEach { player ->
                try {
                    player.start()
                } catch (error: Exception) {
                    players.remove(player)
                    runCatching { player.release() }
                    Log.w("BinucaFeedback", "Could not start confirmation audio", error)
                }
            }
        }
    }

    private fun finish(player: MediaPlayer) {
        if (!players.remove(player)) return
        runCatching { player.release() }
        if (!closed && players.isEmpty()) playNext()
    }

    override fun close() {
        closed = true
        queue.clear()
        players.forEach { runCatching { it.release() } }
        players.clear()
    }
}
