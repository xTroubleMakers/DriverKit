package id.driverkit.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager

/** Lokasi terakhir dari GPS/network. Dipakai Speedometer & SOS. */
object LastLocation {

    @SuppressLint("MissingPermission")
    fun best(ctx: Context): Location? {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER
        )
        var out: Location? = null
        for (p in providers) {
            val l = runCatching { lm.getLastKnownLocation(p) }.getOrNull() ?: continue
            val cur = out
            if (cur == null || l.time > cur.time) out = l
        }
        return out
    }
}
