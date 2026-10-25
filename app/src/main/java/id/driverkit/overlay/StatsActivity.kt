package id.driverkit.overlay

import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

/** Halaman statistik: grafik 14 hari + rincian + export. */
class StatsActivity : AppCompatActivity() {

    private val rp: NumberFormat = NumberFormat.getCurrencyInstance(Locale("in", "ID"))

    override fun onCreate(savedInstanceState: Bundle?) {
        Settings.init(this)
        Settings.load()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.btnExportCsv).setOnClickListener {
            CsvExporter.shareCsv(this)
        }
        findViewById<TextView>(R.id.btnShare).setOnClickListener {
            CsvExporter.shareSummary(this)
        }

        render()
    }

    private fun render() {
        val keys = IncomeStore.last14DayKeys()
        val labelFmt = SimpleDateFormat("dd-MM", Locale.US)

        val series = keys.map { k ->
            val label = runCatching {
                labelFmt.format(SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(k)!!)
            }.getOrDefault(k)
            label to IncomeStore.amountOf(this, k)
        }

        val total = series.sumOf { it.second }
        findViewById<TextView>(R.id.txtTotal).text = rp.format(total)
        findViewById<BarChartView>(R.id.chart).setData(series)

        // Rincian per aplikasi hari ini
        val perApp = IncomeStore.perApp(this, IncomeStore.today())
        val sb = StringBuilder()
        if (perApp.isEmpty()) {
            sb.append("Belum ada catatan hari ini.")
        } else {
            for ((pkg, amount) in perApp.entries.sortedByDescending { it.value }) {
                val name = prettyName(pkg)
                sb.append(name).append("  →  ").append(rp.format(amount)).append('\n')
            }
            sb.append('\n')
            sb.append("Bersih: ").append(rp.format(IncomeStore.netToday(this)))
        }
        findViewById<TextView>(R.id.txtRincian).text = sb.toString()
    }

    private fun prettyName(pkg: String): String =
        if (pkg == "lainnya") "Semua aplikasi" else pkg.substringAfterLast('.')
}
