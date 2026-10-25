package id.driverkit.overlay

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import androidx.core.content.ContextCompat

/**
 * Kirim SOS (lokasi + pesan) ke nomor darurat.
 * Sepenuhnya opt-in: butuh izin lokasi & SMS, plus nomor tersimpan.
 */
object SosHelper {

    @SuppressLint("MissingPermission")
    fun trigger(ctx: Context): Result<String> {
        if (!Settings.sosEnabled) {
            return Result.failure(
                IllegalStateException("Tombol SOS belum diaktifkan di Pengaturan")
            )
        }
        val number = Settings.sosNumber.trim()
        if (number.isEmpty()) {
            return Result.failure(IllegalStateException("Nomor darurat belum diatur"))
        }

        val smsOk = ContextCompat.checkSelfPermission(ctx, Manifest.permission.SEND_SMS) ==
                PackageManager.PERMISSION_GRANTED
        if (!smsOk) return Result.failure(SecurityException("Izin SMS belum diberikan"))

        val loc = LastLocation.best(ctx)
        val where = loc?.let { "https://maps.google.com/?q=${it.latitude},${it.longitude}" }
            ?: "lokasi tidak tersedia"
        val msg = "SOS dari DriverKit. $where"

        return runCatching {
            @Suppress("DEPRECATION")
            val mgr = SmsManager.getDefault()
            mgr.sendTextMessage(number, null, msg, null, null)
            "SOS terkirim ke $number"
        }
    }
}
