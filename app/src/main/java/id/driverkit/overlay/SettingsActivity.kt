package id.driverkit.overlay

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.slider.Slider
import java.text.NumberFormat
import java.util.Locale

/** Halaman pengaturan lengkap. */
class SettingsActivity : AppCompatActivity() {

    private val rp: NumberFormat = NumberFormat.getCurrencyInstance(Locale("in", "ID"))
    private val reqLocation = 3001
    private val reqSms = 3002
    private var pendingSpeedOn = false
    private var pendingSosOn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        Settings.init(this)
        Settings.load()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        bindTheme()
        bindOpacity()
        bindSwitches()
        bindFields()
        bindActions()
    }

    // ---------------- Tampilan ----------------

    private fun bindTheme() {
        val seg = findViewById<RadioGroup>(R.id.segTheme) ?: return
        when (Settings.themeMode) {
            1 -> seg.check(R.id.btnLight)
            2 -> seg.check(R.id.btnDark)
            else -> seg.check(R.id.btnSystem)
        }
        seg.setOnCheckedChangeListener { _, id ->
            Settings.themeMode = when (id) {
                R.id.btnLight -> 1
                R.id.btnDark -> 2
                else -> 0
            }
            Settings.save()
            Settings.applyTheme()
        }
    }

    private fun bindOpacity() {
        val s = findViewById<Slider>(R.id.sliderOpacity) ?: return
        s.value = Settings.panelOpacity.toFloat().coerceIn(40f, 100f)
        s.addOnChangeListener { _, v, fromUser ->
            if (fromUser) {
                Settings.panelOpacity = v.toInt()
                Settings.save()
            }
        }
    }

    private fun bindSwitches() {
        bindSwitch(R.id.swMini, Settings.miniMode) {
            Settings.miniMode = it
            Settings.save()
        }
        bindSwitch(R.id.swAutoHide, Settings.autoHide) {
            Settings.autoHide = it
            Settings.save()
        }
        bindSwitch(R.id.swDrink, Settings.drinkReminder) {
            Settings.drinkReminder = it
            Settings.save()
            ReminderScheduler.reschedule(this)
        }
        bindSwitch(R.id.swSummary, Settings.dailySummary) {
            Settings.dailySummary = it
            Settings.save()
            ReminderScheduler.reschedule(this)
        }
        bindSwitch(R.id.swSpeed, Settings.speedometer) { on ->
            if (on) {
                pendingSpeedOn = true
                askLocation()
            } else {
                Settings.speedometer = false
                Settings.save()
            }
        }
        bindSwitch(R.id.swSos, Settings.sosEnabled) { on ->
            if (on) {
                pendingSosOn = true
                askSms()
            } else {
                Settings.sosEnabled = false
                Settings.save()
            }
        }
    }

    private fun bindSwitch(id: Int, initial: Boolean, onChange: (Boolean) -> Unit) {
        val s = findViewById<SwitchCompat>(id) ?: return
        s.isChecked = initial
        s.setOnCheckedChangeListener { _, v -> onChange(v) }
    }

    // ---------------- Baris nilai ----------------

    private fun bindFields() {
        updateFieldText(R.id.rowTarget, rp.format(Settings.dailyTarget))
        updateFieldText(R.id.rowFuel, rp.format(Settings.fuelPerDay))
        updateFieldText(R.id.rowToll, rp.format(Settings.tollPerDay))
        updateFieldText(R.id.rowParking, rp.format(Settings.parkingPerDay))
        updateFieldText(R.id.rowSplit, "${Settings.splitPercent}%")
        updateFieldText(R.id.rowDrinkMin, "${Settings.drinkIntervalMin} menit")
        updateFieldText(R.id.rowIdleMin, "${Settings.idleWarnMin} menit")
        updateFieldText(
            R.id.rowSosNumber,
            Settings.sosNumber.ifEmpty { getString(R.string.field_unset) }
        )

        rowClick(R.id.rowTarget) { askNumber("Target harian", Settings.dailyTarget, true) { v ->
            Settings.dailyTarget = v; Settings.save(); bindFields()
        } }
        rowClick(R.id.rowFuel) { askNumber("Bensin/hari", Settings.fuelPerDay, true) { v ->
            Settings.fuelPerDay = v; Settings.save(); bindFields()
        } }
        rowClick(R.id.rowToll) { askNumber("Tol/hari", Settings.tollPerDay, true) { v ->
            Settings.tollPerDay = v; Settings.save(); bindFields()
        } }
        rowClick(R.id.rowParking) { askNumber("Parkir/hari", Settings.parkingPerDay, true) { v ->
            Settings.parkingPerDay = v; Settings.save(); bindFields()
        } }
        rowClick(R.id.rowSplit) {
            askNumber("Setoran ke pemilik (%)", Settings.splitPercent.toLong(), false) { v ->
                Settings.splitPercent = v.toInt().coerceIn(0, 100)
                Settings.save(); bindFields()
            }
        }
        rowClick(R.id.rowDrinkMin) {
            askNumber("Interval pengingat (menit)", Settings.drinkIntervalMin.toLong(), false) { v ->
                Settings.drinkIntervalMin = v.toInt().coerceAtLeast(15)
                Settings.save(); ReminderScheduler.reschedule(this); bindFields()
            }
        }
        rowClick(R.id.rowIdleMin) {
            askNumber("Peringatan idle (menit)", Settings.idleWarnMin.toLong(), false) { v ->
                Settings.idleWarnMin = v.toInt().coerceAtLeast(0)
                Settings.save(); bindFields()
            }
        }
        rowClick(R.id.rowSosNumber) { askPhone() }
    }

    private fun rowClick(id: Int, block: () -> Unit) {
        findViewById<LinearLayout>(id)?.setOnClickListener { block() }
    }

    private fun updateFieldText(rowId: Int, text: String) {
        val row = findViewById<LinearLayout>(rowId) ?: return
        row.findViewById<TextView>(R.id.fieldValue)?.text = text
    }

    // ---------------- Aksi ----------------

    private fun bindActions() {
        rowClick(R.id.rowPin) {
            PinGate.setPin(this) { toast(getString(R.string.toast_pin_updated)) }
        }
        rowClick(R.id.rowWipe) {
            AlertDialog.Builder(this)
                .setTitle(R.string.set_wipe)
                .setMessage("Semua catatan penghasilan akan dihapus. Lanjut?")
                .setPositiveButton("Hapus") { _, _ ->
                    IncomeStore.wipe(this)
                    toast(getString(R.string.toast_wiped))
                }
                .setNegativeButton(R.string.dlg_cancel, null)
                .show()
        }
        rowClick(R.id.rowExport) { CsvExporter.shareCsv(this) }
        rowClick(R.id.rowShare) { CsvExporter.shareSummary(this) }
    }

    // ---------------- Dialog ----------------

    private fun askNumber(title: String, current: Long, money: Boolean, onOk: (Long) -> Unit) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(current.toString())
            setSelection(text.length)
        }
        var dialog: AlertDialog? = null
        dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton(R.string.dlg_save) { _, _ ->
                val v = input.text.toString().toLongOrNull()
                if (v == null || v < 0) {
                    toast("Angka tidak valid")
                } else {
                    onOk(v)
                }
            }
            .setNegativeButton(R.string.dlg_cancel, null)
            .show()

        val label = if (money) "Format: angka rupiah" else "Masukkan angka"
        dialog.setMessage(label)
    }

    private fun askPhone() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_PHONE
            setText(Settings.sosNumber)
            setSelection(text.length)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.set_sos_number)
            .setView(input)
            .setPositiveButton(R.string.dlg_save) { _, _ ->
                Settings.sosNumber = input.text.toString().trim()
                Settings.save()
                bindFields()
            }
            .setNegativeButton(R.string.dlg_cancel, null)
            .show()
    }

    // ---------------- Izin runtime ----------------

    private fun askLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            Settings.speedometer = true
            Settings.save()
            return
        }
        ActivityCompat.requestPermissions(
            this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), reqLocation
        )
    }

    private fun askSms() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            Settings.sosEnabled = true
            Settings.save()
            return
        }
        ActivityCompat.requestPermissions(
            this, arrayOf(Manifest.permission.SEND_SMS), reqSms
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted = grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED

        when (requestCode) {
            reqLocation -> {
                Settings.speedometer = granted
                Settings.save()
                if (!granted) toast(getString(R.string.toast_no_location))
                bindSwitches()
            }
            reqSms -> {
                Settings.sosEnabled = granted
                Settings.save()
                if (!granted) toast("Izin SMS ditolak — SOS nonaktif")
                bindSwitches()
            }
        }
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
