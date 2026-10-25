package id.driverkit.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Menerima alarm pengingat minum & ringkasan harian. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent?) {
        val kind = intent?.getStringExtra("kind") ?: return
        Settings.init(ctx)
        Settings.load()
        IncomeStore.init(ctx)

        when (kind) {
            ReminderScheduler.KIND_DRINK -> SummaryNotifier.notifyDrink(ctx)
            ReminderScheduler.KIND_SUMMARY -> SummaryNotifier.notifySummary(ctx)
        }
    }
}
