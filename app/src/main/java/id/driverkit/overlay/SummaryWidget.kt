package id.driverkit.overlay

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.text.NumberFormat
import java.util.Locale

/** Widget layar utama: ringkasan harian. */
class SummaryWidget : AppWidgetProvider() {

    companion object {
        /** Dipanggil setelah ada penghasilan baru. */
        fun refresh(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx) ?: return
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, SummaryWidget::class.java))
            if (ids.isEmpty()) return
            ctx.sendBroadcast(
                Intent(ctx, SummaryWidget::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
            )
        }
    }

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        Settings.init(ctx)
        Settings.load()
        IncomeStore.init(ctx)

        val rp = NumberFormat.getCurrencyInstance(Locale("in", "ID"))
        val income = IncomeStore.amountOf(ctx, IncomeStore.today())
        val trips = IncomeStore.tripsOf(ctx, IncomeStore.today())
        val net = IncomeStore.netToday(ctx)

        for (id in ids) {
            val v = RemoteViews(ctx.packageName, R.layout.widget_summary)
            v.setTextViewText(R.id.widgetIncome, rp.format(income))
            v.setTextViewText(R.id.widgetTrips, ctx.getString(R.string.widget_trips, trips))
            v.setTextViewText(R.id.widgetNet, ctx.getString(R.string.widget_net, rp.format(net)))
            mgr.updateAppWidget(id, v)
        }
    }
}
