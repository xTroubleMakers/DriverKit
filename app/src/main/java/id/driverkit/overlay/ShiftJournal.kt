package id.driverkit.overlay

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Catatan jam kerja vs istirahat per hari. Lokal saja. */
object ShiftJournal {

    const val WORK = "kerja"
    const val REST = "istirahat"

    private var sp: SharedPreferences? = null
    private var openKey: String? = null

    fun init(ctx: Context) {
        if (sp == null) sp = ctx.applicationContext
            .getSharedPreferences("dk_journal", Context.MODE_PRIVATE)
    }

    private fun key(date: String = today()) = "seg_$date"

    fun open(ctx: Context, kind: String) {
        init(ctx)
        close(ctx)
        val k = key()
        openKey = k
        val arr = JSONArray(sp?.getString(k, "[]") ?: "[]")
        arr.put(
            JSONObject().apply {
                put("kind", kind)
                put("start", System.currentTimeMillis())
                put("end", 0L)
            }
        )
        sp?.edit()?.putString(k, arr.toString())?.apply()
    }

    /** Tutup segmen yang sedang terbuka. */
    fun close(ctx: Context) {
        init(ctx)
        val k = openKey ?: return
        val arr = JSONArray(sp?.getString(k, "[]") ?: "[]")
        if (arr.length() > 0) {
            val last = arr.getJSONObject(arr.length() - 1)
            if (last.optLong("end") == 0L) {
                last.put("end", System.currentTimeMillis())
                arr.put(arr.length() - 1, last)
                sp?.edit()?.putString(k, arr.toString())?.apply()
            }
        }
        openKey = null
    }

    fun isOpen(): Boolean = openKey != null

    /** Total milidetik per jenis untuk satu tanggal. */
    fun total(kind: String, date: String = today()): Long {
        val arr = JSONArray(sp?.getString(key(date), "[]") ?: "[]")
        var sum = 0L
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("kind") != kind) continue
            val s = o.optLong("start")
            val e = if (o.optLong("end") == 0L) System.currentTimeMillis() else o.optLong("end")
            sum += (e - s).coerceAtLeast(0L)
        }
        return sum
    }

    private fun today() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
}
