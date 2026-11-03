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
import ua.otboi.app.data.Sound
import ua.otboi.app.data.Store
import ua.otboi.app.i18n.T

private val alarmAttrs: AudioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_ALARM)
    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
    .build()

/** Мелодии по порядку: выбранная → будильник телефона → звонок → уведомление. */
private fun candidates(ctx: Context, uri: String): List<Uri> {
    val out = ArrayList<Uri>()
    if (uri.isNotBlank() && uri != Sound.NONE) runCatching { out += Uri.parse(uri) }
    listOfNotNull(
        RingtoneManager.getActualDefaultRingtoneUri(ctx, RingtoneManager.TYPE_ALARM),
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
    ).forEach { if (it !in out) out += it }
    return out
}

private fun startPlayer(ctx: Context, uris: List<Uri>, volume: Float, looping: Boolean): Pair<MediaPlayer, Int>? {
    for ((i, uri) in uris.withIndex()) {
        val p = MediaPlayer()
        try {
            p.setAudioAttributes(alarmAttrs)
            p.setDataSource(ctx, uri)
            p.isLooping = looping
            p.prepare()
            p.setVolume(volume, volume)
            p.start()
            return p to i
        } catch (_: Exception) {
            p.release()
        }
    }
    return null
}

/** Звонок будильника: мелодия (плавно или сразу громко) и/или вибрация. */
class Ringer(private val ctx: Context, private val sound: Sound) {
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var ramp: Job? = null
    private var restoreVolume = -1

    fun start(scope: CoroutineScope) {
        if (!sound.vibrationOnly) {
            val audio = ctx.getSystemService(AudioManager::class.java)
            if (audio != null) {
                val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                val cur = audio.getStreamVolume(AudioManager.STREAM_ALARM)
                // Если громкость будильника почти на нуле, на время звонка поднимаем её, потом вернём.
                if (max > 0 && cur < max / 3) {
                    try {
                        audio.setStreamVolume(AudioManager.STREAM_ALARM, (max * 0.6f).toInt().coerceAtLeast(1), 0)
                        restoreVolume = cur
                    } catch (_: Exception) {
                    }
                }
            }
            val startVol = if (sound.gradual) 0.15f else 1f
            val started = startPlayer(ctx, candidates(ctx, sound.uri), startVol, looping = true)
            player = started?.first
            if (started != null && started.second > 0 && sound.uri.isNotBlank()) Store.addLog(T.soundFileGone)
            if (player == null) Store.addLog(T.lNoSound)
            if (sound.gradual && player != null) {
                ramp = scope.launch {
                    for (i in 1..15) {
                        delay(2_000)
                        val v = 0.15f + 0.85f * i / 15f
                        try {
                            player?.setVolume(v, v)
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        }

        // Вибрация: если включена, если «только вибрация» или если мелодия так и не заиграла.
        if (sound.vibrate || sound.vibrationOnly || player == null) {
            vibrator = if (Build.VERSION.SDK_INT >= 31) {
                ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                ctx.getSystemService(Vibrator::class.java)
            }
            val effect = VibrationEffect.createWaveform(longArrayOf(0, 700, 500), 0)
            try {
                if (Build.VERSION.SDK_INT >= 33) {
                    vibrator?.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(effect, alarmAttrs)
                }
            } catch (_: Exception) {
            }
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

/** Прослушивание мелодии в редакторе будильника (до 15 секунд, громкость будильника). */
object Preview {
    private var player: MediaPlayer? = null
    private var stopAt = 0L

    val playing: Boolean get() = player != null && System.currentTimeMillis() < stopAt

    fun play(ctx: Context, uri: String): Boolean {
        stop()
        if (uri == Sound.NONE) return false
        val started = startPlayer(ctx, candidates(ctx, uri), 1f, looping = false) ?: return false
        player = started.first
        stopAt = System.currentTimeMillis() + 15_000L
        started.first.setOnCompletionListener { stop() }
        return true
    }

    fun stop() {
        player?.let {
            try {
                it.stop()
            } catch (_: Exception) {
            }
            it.release()
        }
        player = null
        stopAt = 0L
    }
}
