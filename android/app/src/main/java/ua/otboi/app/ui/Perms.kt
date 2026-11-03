package ua.otboi.app.ui

import ua.otboi.app.i18n.T
import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as SysSettings
import androidx.core.content.ContextCompat

data class PermItem(val key: String, val title: String, val hint: String, val ok: Boolean)

/** Что нужно разрешить, чтобы будильник надёжно сработал ночью. */
object Perms {
    fun items(ctx: Context): List<PermItem> {
        val out = ArrayList<PermItem>()
        val notifOk = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        out += PermItem("notif", T.permTitle("notif"), T.permHint("notif"), notifOk)

        val am = ctx.getSystemService(AlarmManager::class.java)
        val exactOk = Build.VERSION.SDK_INT < 31 || am?.canScheduleExactAlarms() == true
        out += PermItem("exact", T.permTitle("exact"), T.permHint("exact"), exactOk)

        if (Build.VERSION.SDK_INT >= 34) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            out += PermItem(
                "fsi", T.permTitle("fsi"), T.permHint("fsi"), nm?.canUseFullScreenIntent() == true
            )
        }

        val pm = ctx.getSystemService(PowerManager::class.java)
        out += PermItem(
            "battery", T.permTitle("battery"), T.permHint("battery"), pm?.isIgnoringBatteryOptimizations(ctx.packageName) == true
        )
        return out
    }

    fun missing(ctx: Context): Int = items(ctx).count { !it.ok }

    fun open(ctx: Context, key: String) {
        val pkg = Uri.parse("package:" + ctx.packageName)
        val intent: Intent = when (key) {
            "notif" -> Intent(SysSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(SysSettings.EXTRA_APP_PACKAGE, ctx.packageName)
            "exact" -> if (Build.VERSION.SDK_INT >= 31) {
                Intent(SysSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg)
            } else {
                Intent(SysSettings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
            }
            "fsi" -> if (Build.VERSION.SDK_INT >= 34) {
                Intent(SysSettings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg)
            } else {
                Intent(SysSettings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
            }
            "battery" -> Intent(SysSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg)
            else -> Intent(SysSettings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
        }
        try {
            ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            try {
                ctx.startActivity(
                    Intent(SysSettings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {
            }
        }
    }
}
