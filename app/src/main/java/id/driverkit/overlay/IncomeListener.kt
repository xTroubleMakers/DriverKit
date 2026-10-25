package id.driverkit.overlay

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Membaca notifikasi penghasilan dari aplikasi mitra yang di-whitelist pengguna.
 * Hanya mengekstrak angka nominal; isi notifikasi penuh tidak disimpan.
 * Tidak ada data yang dikirim keluar (aplikasi tanpa izin INTERNET).
 */
class IncomeListener : NotificationListenerService() {

    private val rpRegex =
        Regex("""(?:Rp|IDR)\s?\.?\s?(\d[\d.,]{2,})""", RegexOption.IGNORE_CASE)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (!Settings.autoCatcher) return
        val pkg = sbn?.packageName ?: return
        if (!Settings.isWatched(pkg)) return

        val extras = sbn.notification?.extras ?: return
        val text = buildString {
            append(extras.getCharSequence(Notification.EXTRA_TITLE) ?: "")
            append(' ')
            append(extras.getCharSequence(Notification.EXTRA_TEXT) ?: "")
            append(' ')
            append(extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: "")
        }
        if (text.isBlank()) return

        val amount = parseAmount(text) ?: return
        IncomeStore.record(this, pkg, amount)
    }

    /** "Rp 18.500" / "Rp18.500" / "IDR 18,500" -> 18500. */
    private fun parseAmount(s: String): Long? {
        val m = rpRegex.find(s) ?: return null
        val digits = m.groupValues[1].filter { it.isDigit() }
        if (digits.length < 3) return null
        return digits.toLongOrNull()?.takeIf { it in 1_000..10_000_000 }
    }
}
