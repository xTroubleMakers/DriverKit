package id.driverkit.overlay

import android.content.Context
import android.text.InputType
import android.widget.EditText
import androidx.appcompat.app.AlertDialog

/** Gerbang PIN sederhana untuk melindungi halaman sensitif. */
object PinGate {

    /** Jalankan [onOk] setelah PIN benar. Bila PIN belum diatur, langsung lanjut. */
    fun require(ctx: Context, onOk: () -> Unit) {
        val saved = Settings.pin
        if (saved.isEmpty()) {
            onOk()
            return
        }
        val input = EditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "PIN"
        }
        AlertDialog.Builder(ctx)
            .setTitle("Masukkan PIN")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                if (input.text.toString() == saved) onOk()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    /** Dialog untuk mengatur / mengubah PIN. */
    fun setPin(ctx: Context, onDone: () -> Unit) {
        val input = EditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "PIN baru (kosongkan untuk menghapus)"
        }
        AlertDialog.Builder(ctx)
            .setTitle("PIN aplikasi")
            .setView(input)
            .setPositiveButton("Simpan") { _, _ ->
                Settings.pin = input.text.toString().trim()
                Settings.save()
                onDone()
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}
