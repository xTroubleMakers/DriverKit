package id.driverkit.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Jadwalkan ulang pengingat setelah perangkat menyala. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        Settings.init(ctx)
        Settings.load()
        IncomeStore.init(ctx)
        ShiftJournal.init(ctx)
        ReminderScheduler.reschedule(ctx)
    }
}
