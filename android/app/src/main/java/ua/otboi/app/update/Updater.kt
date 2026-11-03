package ua.otboi.app.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ua.otboi.app.data.Store
import ua.otboi.app.net.Http
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Новая версия, найденная на GitHub. */
data class UpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val notes: String,
    val sha256: String,
)

/**
 * Автообновление: раз в несколько часов смотрит version.json в публичных релизах,
 * по кнопке скачивает APK, сверяет контрольную сумму и ставит через системный установщик
 * (Android покажет своё окно «Обновить приложение?»).
 */
object Updater {
    /** Публичный репозиторий с релизами — тот, где собрано приложение. */
    val REPO: String = ua.otboi.app.BuildConfig.UPDATE_REPO
    private val BASE = "https://github.com/$REPO/releases/download/android-latest/"
    private val VERSION_URL = BASE + "version.json"
    private val APK_URL = BASE + "Otboi.apk"
    private const val CHECK_EVERY_MS = 6L * 60 * 60 * 1000
    const val ACTION_STATUS = "ua.otboi.app.update.STATUS"

    sealed class State {
        object Idle : State()
        object Checking : State()
        object UpToDate : State()
        data class Available(val info: UpdateInfo) : State()
        data class Downloading(val info: UpdateInfo, val percent: Int) : State()
        data class Installing(val info: UpdateInfo) : State()
        data class NeedsPermission(val info: UpdateInfo) : State()
        data class Failed(val message: String, val info: UpdateInfo? = null) : State()
    }

    val state = MutableStateFlow<State>(State.Idle)
    private val mutex = Mutex()

    fun currentVersionCode(ctx: Context): Long = try {
        PackageInfoCompat.getLongVersionCode(ctx.packageManager.getPackageInfo(ctx.packageName, 0))
    } catch (_: Exception) {
        0L
    }

    fun currentVersionName(ctx: Context): String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
    } catch (_: Exception) {
        "?"
    }

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("otboi_update", Context.MODE_PRIVATE)

    /** Проверить, есть ли новая версия. force — не смотреть, когда проверяли в прошлый раз. */
    suspend fun check(ctx: Context, force: Boolean = false) = mutex.withLock {
        val p = prefs(ctx)
        val now = System.currentTimeMillis()
        val busy = state.value is State.Downloading || state.value is State.Installing
        if (busy) return@withLock
        if (!force && now - p.getLong("checkedAt", 0L) < CHECK_EVERY_MS) {
            // Недавно проверяли — показываем сохранённый результат без сети.
            if (state.value is State.Idle) restore(ctx)
            return@withLock
        }
        if (REPO.isBlank()) {
            state.value = State.Failed("Автообновление выключено в этой сборке")
            return@withLock
        }
        state.value = State.Checking
        val result = withContext(Dispatchers.IO) {
            try {
                val res = Http.get("$VERSION_URL?t=$now", "Otboi-Android", "application/json")
                when (res.code) {
                    200 -> parse(res.body)
                    404 -> Result.failure(Exception("релизов пока нет"))
                    else -> Result.failure(Exception("GitHub ответил ${res.code}"))
                }
            } catch (e: Exception) {
                Result.failure(Exception(Http.shortError(e)))
            }
        }
        result.onSuccess { info ->
            p.edit().putLong("checkedAt", now).putString("latest", infoJson(info)).apply()
            state.value = if (info.versionCode > currentVersionCode(ctx)) State.Available(info) else State.UpToDate
            if (info.versionCode > currentVersionCode(ctx)) Store.addLog("Доступно обновление ${info.versionName}")
        }.onFailure {
            state.value = State.Failed("Не удалось проверить обновления: ${it.message}")
        }
    }

    private fun restore(ctx: Context) {
        val info = prefs(ctx).getString("latest", null)?.let { parse(it).getOrNull() } ?: return
        state.value = if (info.versionCode > currentVersionCode(ctx)) State.Available(info) else State.UpToDate
    }

    fun parse(body: String): Result<UpdateInfo> = try {
        val o = JSONObject(body)
        val code = o.getLong("versionCode")
        Result.success(
            UpdateInfo(
                versionCode = code,
                versionName = o.optString("versionName", code.toString()),
                notes = o.optString("notes", ""),
                sha256 = o.optString("sha256", "").lowercase(),
            )
        )
    } catch (e: Exception) {
        Result.failure(Exception("непонятный ответ (version.json)"))
    }

    private fun infoJson(i: UpdateInfo): String = JSONObject()
        .put("versionCode", i.versionCode).put("versionName", i.versionName)
        .put("notes", i.notes).put("sha256", i.sha256)
        .toString()

    /** Можно ли приложению ставить APK (Android 8+ спрашивает это отдельно). */
    fun canInstall(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(ctx: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(intent)
        } catch (_: Exception) {
            ctx.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** Скачать и установить. */
    suspend fun install(ctx: Context, info: UpdateInfo) {
        if (!canInstall(ctx)) {
            state.value = State.NeedsPermission(info)
            openInstallPermission(ctx)
            return
        }
        state.value = State.Downloading(info, 0)
        val file = File(ctx.cacheDir, "update.apk")
        val downloaded = withContext(Dispatchers.IO) { download(file, info) }
        if (downloaded != null) {
            state.value = State.Failed("Не удалось скачать: $downloaded", info)
            return
        }
        state.value = State.Installing(info)
        val err = withContext(Dispatchers.IO) { startSession(ctx, file) }
        if (err != null) state.value = State.Failed("Не удалось установить: $err", info)
    }

    /** null — всё хорошо, иначе текст ошибки. */
    private fun download(file: File, info: UpdateInfo): String? = try {
        var url = URL(APK_URL)
        var conn: HttpURLConnection
        var hops = 0
        while (true) {
            conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", "Otboi-Android")
            val code = conn.responseCode
            if (code in 300..399 && hops < 5) {
                val loc = conn.getHeaderField("Location") ?: break
                url = URL(url, loc)
                conn.disconnect()
                hops++
                continue
            }
            break
        }
        if (conn.responseCode != 200) {
            "сервер ответил ${conn.responseCode}"
        } else {
            val total = conn.contentLengthLong
            val md = MessageDigest.getInstance("SHA-256")
            conn.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var lastPct = -1
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        md.update(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val pct = (done * 100 / total).toInt()
                            if (pct != lastPct) {
                                lastPct = pct
                                state.value = State.Downloading(info, pct)
                            }
                        }
                    }
                }
            }
            conn.disconnect()
            val hash = md.digest().joinToString("") { "%02x".format(it) }
            if (info.sha256.isNotBlank() && hash != info.sha256) {
                file.delete()
                "файл повреждён (контрольная сумма не совпала) — попробуй ещё раз"
            } else {
                null
            }
        }
    } catch (e: Exception) {
        Http.shortError(e)
    }

    private fun startSession(ctx: Context, file: File): String? = try {
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(ctx.packageName)
        if (Build.VERSION.SDK_INT >= 31) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("otboi.apk", 0, file.length()).use { out ->
                file.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(ctx, UpdateReceiver::class.java).setAction(ACTION_STATUS)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(ctx, 77, intent, flags)
            session.commit(pending.intentSender)
        }
        Store.addLog("Ставлю обновление…")
        null
    } catch (e: Exception) {
        e.message ?: e.javaClass.simpleName
    }
}

/** Ответ системного установщика: показать окно подтверждения или сообщить об ошибке. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Updater.ACTION_STATUS) return
        val info = (Updater.state.value as? Updater.State.Installing)?.info
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm: Intent? = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirm != null) {
                    try {
                        context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (e: Exception) {
                        Updater.state.value = Updater.State.Failed("Android не показал окно установки", info)
                    }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Store.addLog("Обновление установлено")
            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                Updater.state.value = info?.let { Updater.State.Available(it) } ?: Updater.State.Idle
            }
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "код $status"
                val hint = if (status == PackageInstaller.STATUS_FAILURE_CONFLICT ||
                    status == PackageInstaller.STATUS_FAILURE_INCOMPATIBLE
                ) {
                    " — эта версия подписана другим ключом: удали «Отбой» и поставь новую версию вручную"
                } else {
                    ""
                }
                Store.addLog("Обновление не установилось: $msg")
                Updater.state.value = Updater.State.Failed("Не установилось: $msg$hint", info)
            }
        }
    }
}
