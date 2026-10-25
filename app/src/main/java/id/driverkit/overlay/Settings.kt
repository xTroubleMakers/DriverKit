package id.driverkit.overlay

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate

/** Pengaturan aplikasi. Disimpan terenkripsi via [Prefs]. */
object Settings {

    private var sp: SharedPreferences? = null
    private var appCtx: Context? = null

    // Target & biaya harian
    var dailyTarget = 350_000L
    var fuelPerDay = 60_000L
    var tollPerDay = 15_000L
    var parkingPerDay = 5_000L
    var splitPercent = 20

    // Panel
    var panelOpacity = 92
    var miniMode = false
    var autoHide = true
    var panelCollapsed = false
    var panelExpandedHeightDp = 440

    // Penghasilan & jurnal
    var autoCatcher = true
    var perAppSplit = false
    var autoBreakJournal = true
    var restIntervalMin = 240

    // Pengingat
    var drinkReminder = true
    var drinkIntervalMin = 90
    var dailySummary = true
    var idleWarnMin = 30

    // Opt-in
    var speedometer = false
    var sosEnabled = false
    var sosNumber = ""

    // Keamanan & tema
    var pin = ""
    var themeMode = 0

    // Posisi overlay
    var launcherX = 40
    var launcherY = 320
    var panelX = 40
    var panelY = 180

    fun init(ctx: Context) {
        if (sp == null) {
            appCtx = ctx.applicationContext
            sp = Prefs.get(appCtx!!)
        }
    }

    fun contextOrNull(): Context? = appCtx

    fun load() {
        val p = sp ?: return
        dailyTarget = p.getLong("daily_target", 350_000L)
        fuelPerDay = p.getLong("fuel", 60_000L)
        tollPerDay = p.getLong("toll", 15_000L)
        parkingPerDay = p.getLong("parking", 5_000L)
        splitPercent = p.getInt("split", 20)

        panelOpacity = p.getInt("panel_opacity", 92)
        miniMode = p.getBoolean("mini_mode", false)
        autoHide = p.getBoolean("auto_hide", true)
        panelCollapsed = p.getBoolean("panel_collapsed", false)
        panelExpandedHeightDp = p.getInt("panel_h", 440)

        autoCatcher = p.getBoolean("auto_catch", true)
        perAppSplit = p.getBoolean("per_app", false)
        autoBreakJournal = p.getBoolean("auto_break", true)
        restIntervalMin = p.getInt("rest_min", 240)

        drinkReminder = p.getBoolean("drink", true)
        drinkIntervalMin = p.getInt("drink_min", 90)
        dailySummary = p.getBoolean("summary", true)
        idleWarnMin = p.getInt("idle_min", 30)

        speedometer = p.getBoolean("speed", false)
        sosEnabled = p.getBoolean("sos", false)
        sosNumber = p.getString("sos_number", "") ?: ""

        pin = p.getString("pin", "") ?: ""
        themeMode = p.getInt("theme", 0)

        launcherX = p.getInt("lx", 40)
        launcherY = p.getInt("ly", 320)
        panelX = p.getInt("px", 40)
        panelY = p.getInt("py", 180)
    }

    fun save() {
        val p = sp ?: return
        p.edit()
            .putLong("daily_target", dailyTarget)
            .putLong("fuel", fuelPerDay)
            .putLong("toll", tollPerDay)
            .putLong("parking", parkingPerDay)
            .putInt("split", splitPercent)
            .putInt("panel_opacity", panelOpacity)
            .putBoolean("mini_mode", miniMode)
            .putBoolean("auto_hide", autoHide)
            .putBoolean("panel_collapsed", panelCollapsed)
            .putInt("panel_h", panelExpandedHeightDp)
            .putBoolean("auto_catch", autoCatcher)
            .putBoolean("per_app", perAppSplit)
            .putBoolean("auto_break", autoBreakJournal)
            .putInt("rest_min", restIntervalMin)
            .putBoolean("drink", drinkReminder)
            .putInt("drink_min", drinkIntervalMin)
            .putBoolean("summary", dailySummary)
            .putInt("idle_min", idleWarnMin)
            .putBoolean("speed", speedometer)
            .putBoolean("sos", sosEnabled)
            .putString("sos_number", sosNumber)
            .putString("pin", pin)
            .putInt("theme", themeMode)
            .putInt("lx", launcherX)
            .putInt("ly", launcherY)
            .putInt("px", panelX)
            .putInt("py", panelY)
            .apply()
    }

    fun applyTheme() {
        AppCompatDelegate.setDefaultNightMode(
            when (themeMode) {
                1 -> AppCompatDelegate.MODE_NIGHT_NO
                2 -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }

    /** Paket aplikasi mitra yang boleh dicatat penghasilannya. */
    fun isWatched(pkg: String): Boolean {
        val c = appCtx ?: return false
        return IncomeStore.watch(c).contains(pkg)
    }
}
