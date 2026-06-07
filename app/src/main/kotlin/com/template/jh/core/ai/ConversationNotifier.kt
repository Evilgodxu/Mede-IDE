package com.template.jh.core.ai

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import com.template.jh.data.model.NotificationEventType
import com.template.jh.data.model.NotificationSettings

// 对话流通知：音效提示
object ConversationNotifier {

    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    fun notify(context: Context, type: NotificationEventType, settings: NotificationSettings) {
        val sound = when (type) {
            NotificationEventType.TaskCompleted -> settings.taskCompletedSound
            NotificationEventType.TaskFailed -> settings.taskFailedSound
            NotificationEventType.WaitingUserAction -> settings.waitingUserActionSound
        }

        if (sound) playSound(context, type)
    }

    private fun playSound(context: Context, type: NotificationEventType) {
        try {
            when (type) {
                NotificationEventType.TaskFailed -> {
                    // 短促错误蜂鸣音（非音乐）
                    val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
                    tg.startTone(ToneGenerator.TONE_PROP_NACK, 300)
                    handler.postDelayed({ try { tg.release() } catch (_: Exception) {} }, 400)
                }
                else -> {
                    mediaPlayer?.release()
                    val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    mediaPlayer = MediaPlayer().apply {
                        setDataSource(context, soundUri)
                        setOnCompletionListener { it.release() }
                        prepare()
                        start()
                    }
                }
            }
        } catch (_: Exception) {}
    }

    fun release() {
        try { mediaPlayer?.release() } catch (_: Exception) {}
        mediaPlayer = null
    }
}
