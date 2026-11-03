package ua.otboi.app.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ua.otboi.app.data.Store

/** Мелодия будильника с плавным нарастанием громкости + вибрация. */
class Ringer(private val ctx: Context) {
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var ramp: Job? = null
    private var restoreVolume = -1

    fun start(scope: CoroutineScope) {
        val audio = ctx.getSystemService(AudioManager::class.java)
        if (audio != null) {
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val cur = audio.getStreamVolume(AudioManager.STREAM_ALARM)
            // Если громкость будильника почти на нуле — на время звонка поднимаем её, потом вернём.
            if (max > 0 && cur < max / 3) {
                try {
                    audio.setStreamVolume(AudioManager.STREAM_ALARM, (max * 0.6f).toInt().coerceAtLeast(1), 0)
                    restoreVolume = cur
                } catch (_: Exception) {
                }
            }
        }

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val uris: List<Uri> = listOfNotNull(
            RingtoneManager.getActualDefaultRingtoneUri(ctx, RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
        )
        for (uri in uris) {
            val p = MediaPlayer()
            try {
                p.setAudioAttributes(attrs)
                p.setDataSource(ctx, uri)
                p.isLooping = true
                p.prepare()
                p.setVolume(0.2f, 0.2f)
                p.start()
                player = p
                break
            } catch (_: Exception) {
                p.release()
            }
        }
        if (player == null) Store.addLog("Не нашёл мелодию будильника — только вибрация")

        ramp = scope.launch {
            for (i in 1..15) {
                delay(2_000)
                val v = 0.2f + 0.8f * i / 15f
                try {
                    player?.setVolume(v, v)
                } catch (_: Exception) {
                }
            }
        }

        vibrator = if (Build.VERSION.SDK_INT >= 31) {
            ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            ctx.getSystemService(Vibrator::class.java)
        }
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 700, 500), 0)
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                vibrator?.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
            } else {
                vibrator?.vibrate(effect, attrs)
            }
        } catch (_: Exception) {
        }
    }

    fun stop() {
        ramp?.cancel()
        ramp = null
        player?.let {
            try {
                it.stop()
            } catch (_: Exception) {
            }
            it.release()
        }
        player = null
        try {
            vibrator?.cancel()
        } catch (_: Exception) {
        }
        vibrator = null
        if (restoreVolume >= 0) {
            try {
                ctx.getSystemService(AudioManager::class.java)
                    ?.setStreamVolume(AudioManager.STREAM_ALARM, restoreVolume, 0)
            } catch (_: Exception) {
            }
            restoreVolume = -1
        }
    }
}
