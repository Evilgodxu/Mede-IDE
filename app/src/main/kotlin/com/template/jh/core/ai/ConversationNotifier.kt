package com.template.jh.core.ai

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.template.jh.data.model.NotificationEventType
import com.template.jh.data.model.NotificationSettings

// 对话流通知：音效 + Toast 弹出提示
object ConversationNotifier {

    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    fun notify(context: Context, type: NotificationEventType, message: String, settings: NotificationSettings) {
        val (sound, popup) = when (type) {
            NotificationEventType.TaskCompleted -> settings.taskCompletedSound to settings.taskCompletedPopup
            NotificationEventType.TaskFailed -> settings.taskFailedSound to settings.taskFailedPopup
            NotificationEventType.WaitingUserAction -> settings.waitingUserActionSound to settings.waitingUserActionPopup
        }

        if (sound) playSound(context, type)
        if (popup) showToast(context, message)
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

    private fun showToast(context: Context, message: String) {
        handler.post {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    fun release() {
        try { mediaPlayer?.release() } catch (_: Exception) {}
        mediaPlayer = null
    }
}
