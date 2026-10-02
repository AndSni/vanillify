package app.vanillify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.vanillify.shell.ShizukuBridge

/** The one notification Vanillify posts: protection is waiting for Shizuku after a reboot. */
object Notifier {

    private const val CHANNEL = "protection"
    private const val PAUSED_ID = 1

    fun showPaused(context: Context, count: Int) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.channel_protection), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.channel_protection_desc) },
        )
        // Tapping opens Shizuku to start it; Vanillify re-applies as soon as it connects.
        val target = context.packageManager.getLaunchIntentForPackage(ShizukuBridge.PACKAGE)
            ?: Intent(context, MainActivity::class.java)
        val tap = PendingIntent.getActivity(
            context, 0, target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.paused_title))
            .setContentText(context.resources.getQuantityString(R.plurals.paused_text, count, count))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.resources.getQuantityString(R.plurals.paused_text, count, count)))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(PAUSED_ID, n) }
    }

    fun clearPaused(context: Context) {
        NotificationManagerCompat.from(context).cancel(PAUSED_ID)
    }
}
