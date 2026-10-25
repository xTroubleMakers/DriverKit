package id.driverkit.overlay

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Preferensi terenkripsi (AES-256-GCM).
 * Semua data tetap di perangkat; aplikasi tidak punya izin INTERNET.
 */
object Prefs {

    @Volatile private var sp: SharedPreferences? = null

    fun get(ctx: Context): SharedPreferences {
        sp?.let { return it }
        synchronized(this) {
            sp?.let { return it }
            val app = ctx.applicationContext
            val masterKey = MasterKey.Builder(app, "driverkit_master")
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val created = EncryptedSharedPreferences.create(
                app,
                "driverkit_secure",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            sp = created
            return created
        }
    }
}
