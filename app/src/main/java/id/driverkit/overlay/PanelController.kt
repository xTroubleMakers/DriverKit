package id.driverkit.overlay

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.slider.Slider
import java.text.NumberFormat
import java.util.Locale

/**
 * Mengisi panel gelap bergaya toggle-list.
 *
 * WAJIB memakai ContextThemeWrapper: LayoutInflater mentah dari Service tidak
 * membawa Material theme, sehingga SwitchCompat/Slider gagal inflate.
 */
class PanelController(private val ctx: Context) {

    enum class Mode { FULL, MINI }

    private val themed: Context = ContextThemeWrapper(ctx, R.style.Theme_DriverKit)
    private val rp = NumberFormat.getCurrencyInstance(Locale("in", "ID"))

    fun inflate(mode: Mode): View =
        LayoutInflater.from(themed).inflate(
            if (mode == Mode.MINI) R.layout.overlay_panel_mini else R.layout.overlay_panel,
            null
        )

    /** Pasang nilai awal + callback. [onToggle] menerima (key, value). */
    fun bindControls(root: View, onToggle: (String, Boolean) -> Unit) {

        fun sw(id: Int, key: String, value: Boolean) {
            val s = root.findViewById<SwitchCompat>(id) ?: return
            s.isChecked = value
            s.setOnCheckedChangeListener { _, v -> onToggle(key, v) }
        }

        sw(R.id.swAutoCatch, "autoCatch", Settings.autoCatcher)
        sw(R.id.swTarget, "target", Settings.dailyTarget > 0)
        sw(R.id.swPerApp, "perApp", Settings.perAppSplit)
        sw(R.id.swAutoBreak, "autoBreak", Settings.autoBreakJournal)
        sw(R.id.swDrink, "drink", Settings.drinkReminder)
        sw(R.id.swIdle, "idle", Settings.idleWarnMin > 0)
        sw(R.id.swSpeed, "speed", Settings.speedometer)
        sw(R.id.swSos, "sos", Settings.sosEnabled)
        sw(R.id.swMini, "mini", Settings.miniMode)
        sw(R.id.swAutoHide, "autoHide", Settings.autoHide)

        root.findViewById<Slider>(R.id.sliderOpacity)?.apply {
            value = Settings.panelOpacity.toFloat().coerceIn(40f, 100f)
            addOnChangeListener { _, _, fromUser ->
                if (fromUser) onToggle("opacity", true)
            }
        }
    }

    /** Ringkasan panel penuh. Dipanggil tiap detik oleh OverlayService. */
    fun bindSummary(root: View, shiftMs: Long, battery: Int, speedText: String?) {
        val income = IncomeStore.amountOf(ctx, IncomeStore.today())
        val trips = IncomeStore.tripsOf(ctx, IncomeStore.today())

        root.findViewById<TextView>(R.id.txtIncome)?.text = rp.format(income)

        val speedPart =
            if (Settings.speedometer && speedText != null) " · $speedText" else ""
        root.findViewById<TextView>(R.id.txtSummary)?.text =
            "$trips trip · $battery%$speedPart · ${hm(shiftMs)}"

        val bar = root.findViewById<ProgressBar>(R.id.progressTarget) ?: return
        val target = Settings.dailyTarget
        if (target > 0) {
            bar.visibility = View.VISIBLE
            bar.progress = ((income * 100) / target).coerceIn(0, 100).toInt()
        } else {
            bar.visibility = View.GONE
        }
    }

    fun bindMini(root: View, shiftMs: Long) {
        val income = IncomeStore.amountOf(ctx, IncomeStore.today())
        val trips = IncomeStore.tripsOf(ctx, IncomeStore.today())
        root.findViewById<TextView>(R.id.txtIncome)?.text = rp.format(income)
        root.findViewById<TextView>(R.id.txtMiniTrips)?.text =
            "· $trips trip · ${hm(shiftMs)}"
    }

    private fun hm(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0L)
        return "%02d:%02d".format(s / 3600, (s % 3600) / 60)
    }
}
