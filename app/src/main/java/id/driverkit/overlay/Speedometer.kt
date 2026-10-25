package id.driverkit.overlay

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.core.content.ContextCompat

/**
 * Hitung kecepatan dari GPS perangkat sendiri.
 * Opt-in: hanya aktif kalau pengguna mengizinkan lokasi.
 */
class Speedometer(private val ctx: Context) {

    @Volatile
    var speedKmh: Float = 0f
        private set

    private var lm: LocationManager? = null
    private var lastSpeed = 0f

    private val listener = object : LocationListener {
        override fun onLocationChanged(loc: Location) {
            val v = loc.speed * 3.6f // m/s -> km/jam
            // Smoothing supaya angka tidak berkedip
            lastSpeed = if (lastSpeed == 0f) v else lastSpeed * 0.7f + v * 0.3f
            speedKmh = lastSpeed.coerceAtLeast(0f)
        }

        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        val granted = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return false

        lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return runCatching {
            lm!!.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 1000L, 1f, listener
            )
            true
        }.getOrDefault(false)
    }

    fun stop() {
        runCatching { lm?.removeUpdates(listener) }
        lastSpeed = 0f
        speedKmh = 0f
    }

    /** Teks siap tampil, mis. "62 km/jam". Null bila izin belum ada. */
    fun display(): String? {
        val granted = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return if (!granted) null else "${speedKmh.toInt()} km/jam"
    }
}
