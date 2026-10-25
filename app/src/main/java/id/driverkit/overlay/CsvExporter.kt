package id.driverkit.overlay

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.text.NumberFormat
import java.util.Locale

/** Berbagi berkas lokal lewat share sheet. Tidak memakai jaringan. */
object CsvExporter {

    /** Bagikan CSV hasil [IncomeStore.exportCsv] lewat share sheet. */
    fun shareCsv(ctx: Context) {
        val file = IncomeStore.exportCsv(ctx)
        val uri = FileProvider.getUriForFile(
            ctx,
            "${ctx.packageName}.files",
            file
        )
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "DriverKit CSV")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(
            Intent.createChooser(i, "Bagikan CSV").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Ringkasan teks harian untuk dibagikan. */
    fun shareSummary(ctx: Context) {
        IncomeStore.init(ctx)
        val rp = NumberFormat.getCurrencyInstance(Locale("in", "ID"))
        val income = IncomeStore.amountOf(ctx, IncomeStore.today())
        val trips = IncomeStore.tripsOf(ctx, IncomeStore.today())
        val net = IncomeStore.netToday(ctx)
        val worked = ShiftJournal.total(ShiftJournal.WORK)

        val text = buildString {
            appendLine("Ringkasan DriverKit — ${IncomeStore.today()}")
            appendLine("Penghasilan: ${rp.format(income)}")
            appendLine("Trip: $trips")
            appendLine("Bersih: ${rp.format(net)}")
            appendLine("Kerja: ${worked / 3_600_000} jam ${(worked / 60_000) % 60} menit")
        }

        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        ctx.startActivity(
            Intent.createChooser(i, "Bagikan ringkasan")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
