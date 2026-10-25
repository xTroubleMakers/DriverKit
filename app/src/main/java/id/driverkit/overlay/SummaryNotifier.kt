package id.driverkit.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.text.NumberFormat
import java.util.Locale

/** Notifikasi pengingat & kanal notifikasi aplikasi. */
object SummaryNotifier {

    const val CH_RUNNING = "dk_running"
    const val CH_REMINDER = "dk_reminder"
    const val ID_RUNNING = 9001
    const val ID_DRINK = 9002
    const val ID_SUMMARY = 9003

    fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = ctx.getSystemService(NotificationManager::class.java) ?: return

        mgr.createNotificationChannel(
            NotificationChannel(
                CH_RUNNING,
                "Panel aktif",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Notifikasi panel melayang sedang berjalan" }
        )
        mgr.createNotificationChannel(
            NotificationChannel(
                CH_REMINDER,
                "Pengingat",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Pengingat minum & ringkasan harian" }
        )
    }

    fun buildRunning(ctx: Context): android.app.Notification {
        ensureChannels(ctx)
        val open = PendingIntent.getActivity(
            ctx, 1,
            Intent(ctx, MainActivity::class.java),
            pendingFlags()
        )
        return NotificationCompat.Builder(ctx, CH_RUNNING)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(ctx.getString(R.string.app_name))
            .setContentText(ctx.getString(R.string.notif_running))
            .setOngoing(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun notifyDrink(ctx: Context) {
        ensureChannels(ctx)
        post(
            ctx, ID_DRINK,
            ctx.getString(R.string.notif_drink),
            ctx.getString(R.string.notif_drink_body)
        )
    }

    fun notifySummary(ctx: Context) {
        ensureChannels(ctx)
        IncomeStore.init(ctx)
        val rp = NumberFormat.getCurrencyInstance(Locale("in", "ID"))
        val income = rp.format(IncomeStore.amountOf(ctx, IncomeStore.today()))
        val trips = IncomeStore.tripsOf(ctx, IncomeStore.today())
        val body = "$trips trip · $income"
        post(ctx, ID_SUMMARY, ctx.getString(R.string.notif_summary), body)
    }

    private fun post(ctx: Context, id: Int, title: String, body: String) {
        val mgr = ctx.getSystemService(NotificationManager::class.java) ?: return
        val n = NotificationCompat.Builder(ctx, CH_REMINDER)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { mgr.notify(id, n) }
    }

    private fun pendingFlags(): Int {
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= 23) flags = flags or PendingIntent.FLAG_IMMUTABLE
        return flags
    }
}
