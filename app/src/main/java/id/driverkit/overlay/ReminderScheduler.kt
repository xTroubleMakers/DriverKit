package id.driverkit.overlay

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

/** Penjadwal pengingat minum dan ringkasan harian 21:00. */
object ReminderScheduler {

    private const val REQ_DRINK = 1001
    private const val REQ_SUMMARY = 1002

    private const val EXTRA_KIND = "kind"
    const val KIND_DRINK = "drink"
    const val KIND_SUMMARY = "summary"

    fun reschedule(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        cancel(ctx, REQ_DRINK)
        cancel(ctx, REQ_SUMMARY)

        if (Settings.drinkReminder) {
            scheduleDrink(ctx, am)
        }
        if (Settings.dailySummary) {
            scheduleSummary(ctx, am)
        }
    }

    private fun scheduleDrink(ctx: Context, am: AlarmManager) {
        val intervalMs = Settings.drinkIntervalMin.coerceAtLeast(15) * 60_000L
        val pi = pending(ctx, REQ_DRINK, KIND_DRINK)
        runCatching {
            am.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + intervalMs,
                intervalMs,
                pi
            )
        }
    }

    private fun scheduleSummary(ctx: Context, am: AlarmManager) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 21)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        val pi = pending(ctx, REQ_SUMMARY, KIND_SUMMARY)
        runCatching {
            am.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                cal.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                pi
            )
        }
    }

    private fun pending(ctx: Context, req: Int, kind: String): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_KIND, kind)
        }
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (android.os.Build.VERSION.SDK_INT >= 23) flags = flags or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(ctx, req, i, flags)
    }

    private fun cancel(ctx: Context, req: Int) {
        val kind = if (req == REQ_DRINK) KIND_DRINK else KIND_SUMMARY
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        runCatching { am.cancel(pending(ctx, req, kind)) }
    }
}
