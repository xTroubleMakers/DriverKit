package id.driverkit.overlay

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Penyimpanan penghasilan harian. Lokal, terenkripsi, tanpa jaringan. */
object IncomeStore {

    private var sp: SharedPreferences? = null

    fun init(ctx: Context) {
        if (sp == null) sp = Prefs.get(ctx.applicationContext)
    }

    fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun key(date: String) = "income_$date"

    fun entries(date: String): JSONArray {
        val p = sp ?: return JSONArray()
        return runCatching { JSONArray(p.getString(key(date), "[]") ?: "[]") }
            .getOrDefault(JSONArray())
    }

    fun record(context: Context, pkg: String, amount: Long) {
        init(context)
        if (amount <= 0L) return
        val arr = entries(today())
        arr.put(
            JSONObject().apply {
                put("pkg", if (Settings.perAppSplit) pkg else "lainnya")
                put("amount", amount)
                put("ts", System.currentTimeMillis())
            }
        )
        sp?.edit()?.putString(key(today()), arr.toString())?.apply()
        SummaryWidget.refresh(context)
    }

    fun amountOf(context: Context, date: String): Long {
        init(context)
        val arr = entries(date)
        var sum = 0L
        for (i in 0 until arr.length()) {
            sum += arr.optJSONObject(i)?.optLong("amount", 0L) ?: 0L
        }
        return sum
    }

    fun tripsOf(context: Context, date: String): Int {
        init(context)
        return entries(date).length()
    }

    fun perApp(context: Context, date: String): Map<String, Long> {
        init(context)
        val arr = entries(date)
        val out = LinkedHashMap<String, Long>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val p = o.optString("pkg", "lainnya")
            out[p] = (out[p] ?: 0L) + o.optLong("amount", 0L)
        }
        return out
    }

    fun costToday(): Long =
        Settings.fuelPerDay + Settings.tollPerDay + Settings.parkingPerDay

    fun setoranToday(context: Context): Long =
        amountOf(context, today()) * Settings.splitPercent / 100

    fun netToday(context: Context): Long {
        val gross = amountOf(context, today())
        return (gross - costToday() - setoranToday(context)).coerceAtLeast(0L)
    }

    fun last14DayKeys(): List<String> {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val out = ArrayList<String>(14)
        for (i in 13 downTo 0) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -i)
            out.add(fmt.format(cal.time))
        }
        return out
    }

    /** Paket aplikasi mitra yang dicatat. */
    fun watch(context: Context): Set<String> {
        init(context)
        return sp?.getStringSet("watch_pkgs", emptySet()) ?: emptySet()
    }

    fun saveWatch(context: Context, set: Set<String>) {
        init(context)
        sp?.edit()?.putStringSet("watch_pkgs", set)?.apply()
    }

    /** Tulis CSV 14 hari terakhir ke folder aplikasi (tanpa izin storage). */
    fun exportCsv(context: Context): File {
        init(context)
        val dir = File(context.filesDir, "export")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "driverkit-${today()}.csv")
        val timeFmt = SimpleDateFormat("HH:mm", Locale.US)
        val sb = StringBuilder("tanggal;paket;nominal;waktu\n")
        for (d in last14DayKeys()) {
            val arr = entries(d)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val t = timeFmt.format(Date(o.optLong("ts", System.currentTimeMillis())))
                sb.append(d).append(';')
                    .append(o.optString("pkg", "lainnya")).append(';')
                    .append(o.optLong("amount", 0L)).append(';')
                    .append(t).append('\n')
            }
        }
        file.writeText(sb.toString())
        return file
    }

    fun wipe(context: Context) {
        init(context)
        val e = sp?.edit() ?: return
        for (d in last14DayKeys()) e.remove(key(d))
        e.remove("watch_pkgs")
        e.apply()
    }
}
