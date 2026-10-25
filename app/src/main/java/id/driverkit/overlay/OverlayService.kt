package id.driverkit.overlay

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.view.ContextThemeWrapper
import com.google.android.material.slider.Slider

/**
 * Layanan overlay: bola melayang + panel produktivitas.
 * Hanya menampilkan data milik perangkat sendiri. Tanpa akses jaringan.
 */
class OverlayService : Service() {

    companion object {
        @Volatile var running = false
            private set
    }

    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val controller by lazy { PanelController(this) }

    private var bubble: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null

    private var panel: View? = null
    private var panelParams: WindowManager.LayoutParams? = null

    private var panelMode = PanelController.Mode.FULL
    private var shiftStartMs = System.currentTimeMillis()
    private var batteryPct = 100

    private var speedo: Speedometer? = null
    private val idleDetector = IdleDetector()
    private var lastBreakWarnAt = 0L

    private var dragDX = 0f
    private var dragDY = 0f
    private var downX = 0f
    private var downY = 0f
    private var lastInteractionAt = 0L

    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            maybeAutoHide()
            maybeWarnBreak()
            handler.postDelayed(this, 1000L)
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            val level = i?.getIntExtra("level", -1) ?: -1
            val scale = i?.getIntExtra("scale", 100) ?: 100
            if (level >= 0 && scale > 0) batteryPct = level * 100 / scale
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Settings.init(this)
        Settings.load()
        IncomeStore.init(this)
        ShiftJournal.init(this)
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        SummaryNotifier.ensureChannels(this)

        startForegroundCompat()
        shiftStartMs = System.currentTimeMillis()

        if (Settings.autoBreakJournal) {
            ShiftJournal.open(this, ShiftJournal.WORK)
        }

        if (Settings.speedometer) maybeStartSpeedometer()

        registerReceiver(
            batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )

        attachBubble()

        if (!Settings.panelCollapsed) attachPanel() else attachMini()

        handler.postDelayed(ticker, 1000L)
        running = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        START_STICKY

    override fun onDestroy() {
        running = false
        handler.removeCallbacks(ticker)
        runCatching { unregisterReceiver(batteryReceiver) }
        speedo?.stop()
        if (Settings.autoBreakJournal) ShiftJournal.close(this)
        detachPanel()
        detachBubble()
        super.onDestroy()
    }

    // ---------------- Foreground ----------------

    private fun startForegroundCompat() {
        val n = SummaryNotifier.buildRunning(this)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                SummaryNotifier.ID_RUNNING,
                n,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(SummaryNotifier.ID_RUNNING, n)
        }
    }

    // ---------------- Bola melayang ----------------

    private fun attachBubble() {
        if (bubble != null) return
        val themed = ContextThemeWrapper(this, R.style.Theme_DriverKit)
        val v = LayoutInflater.from(themed).inflate(R.layout.overlay_bubble, null)

        val p = WindowManager.LayoutParams(
            dp(56), dp(56),
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = Settings.launcherX
            y = Settings.launcherY
        }

        v.alpha = if (Settings.autoHide) 0.55f else 1f

        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragDX = e.rawX; dragDY = e.rawY
                    downX = e.rawX; downY = e.rawY
                    lastInteractionAt = System.currentTimeMillis()
                }
                MotionEvent.ACTION_MOVE -> {
                    p.x += (e.rawX - dragDX).toInt()
                    p.y += (e.rawY - dragDY).toInt()
                    dragDX = e.rawX; dragDY = e.rawY
                    runCatching { wm.updateViewLayout(v, p) }
                }
                MotionEvent.ACTION_UP -> {
                    Settings.launcherX = p.x
                    Settings.launcherY = p.y
                    Settings.save()
                    val moved = kotlin.math.abs(e.rawX - downX) > dp(8) ||
                            kotlin.math.abs(e.rawY - downY) > dp(8)
                    if (!moved) togglePanel()
                }
            }
            true
        }

        runCatching { wm.addView(v, p) }
        bubble = v
        bubbleParams = p
    }

    private fun detachBubble() {
        val v = bubble ?: return
        runCatching { wm.removeView(v) }
        bubble = null
        bubbleParams = null
    }

    private fun togglePanel() {
        lastInteractionAt = System.currentTimeMillis()
        if (panel != null) detachPanel() else {
            if (Settings.miniMode) attachMini() else attachPanel()
        }
    }

    // ---------------- Panel ----------------

    private fun attachPanel() {
        if (panel != null) detachPanel()
        panelMode = PanelController.Mode.FULL
        val v = controller.inflate(panelMode)

        val maxH = dp(Settings.panelExpandedHeightDp)
        val p = WindowManager.LayoutParams(
            dp(310), maxH,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = Settings.panelX
            y = Settings.panelY
        }

        v.alpha = Settings.panelOpacity / 100f
        controller.bindControls(v) { key, value -> onToggleChanged(key, value, v, p) }
        wireButtons(v, p)

        runCatching { wm.addView(v, p) }
        panel = v
        panelParams = p
        lastInteractionAt = System.currentTimeMillis()
        refresh()
    }

    /** Panel ringkas. */
    private fun attachMini() {
        if (panel != null) detachPanel()
        panelMode = PanelController.Mode.MINI
        val v = controller.inflate(panelMode)

        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = Settings.panelX
            y = Settings.panelY
        }

        v.alpha = Settings.panelOpacity / 100f
        v.findViewById<TextView>(R.id.btnExpand)?.setOnClickListener {
            Settings.miniMode = false
            Settings.panelCollapsed = false
            Settings.save()
            attachPanel()
        }
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragDX = e.rawX; dragDY = e.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    p.x += (e.rawX - dragDX).toInt()
                    p.y += (e.rawY - dragDY).toInt()
                    dragDX = e.rawX; dragDY = e.rawY
                    runCatching { wm.updateViewLayout(v, p) }
                }
                MotionEvent.ACTION_UP -> {
                    Settings.panelX = p.x; Settings.panelY = p.y; Settings.save()
                }
            }
            true
        }

        runCatching { wm.addView(v, p) }
        panel = v
        panelParams = p
        lastInteractionAt = System.currentTimeMillis()
        refresh()
    }

    private fun detachPanel() {
        val v = panel ?: return
        runCatching { wm.removeView(v) }
        panel = null
        panelParams = null
    }

    private fun onToggleChanged(
        key: String,
        value: Boolean,
        v: View,
        p: WindowManager.LayoutParams
    ) {
        lastInteractionAt = System.currentTimeMillis()
        when (key) {
            "autoCatch" -> Settings.autoCatcher = value
            "perApp" -> Settings.perAppSplit = value
            "autoBreak" -> Settings.autoBreakJournal = value
            "drink" -> {
                Settings.drinkReminder = value
                ReminderScheduler.reschedule(this)
            }
            "idle" -> Settings.idleWarnMin = if (value) 30 else 0
            "speed" -> {
                Settings.speedometer = value
                if (value) maybeStartSpeedometer() else speedo?.stop()
            }
            "sos" -> Settings.sosEnabled = value
            "mini" -> {
                Settings.miniMode = value
                if (value) attachMini() else attachPanel()
            }
            "autoHide" -> {
                Settings.autoHide = value
                bubble?.alpha = if (value) 0.55f else 1f
            }
            "target" -> {
                if (!value) Settings.dailyTarget = 0L
                else if (Settings.dailyTarget <= 0L) Settings.dailyTarget = 350_000L
            }
            "opacity" -> {
                val s = v.findViewById<Slider>(R.id.sliderOpacity)
                Settings.panelOpacity = (s?.value ?: 100f).toInt()
                panel?.alpha = Settings.panelOpacity / 100f
            }
        }
        Settings.save()
    }

    private fun wireButtons(v: View, p: WindowManager.LayoutParams) {
        v.findViewById<TextView>(R.id.btnClose)?.setOnClickListener { detachPanel() }

        v.findViewById<TextView>(R.id.btnCollapse)?.setOnClickListener {
            Settings.panelCollapsed = true
            Settings.miniMode = true
            Settings.save()
            attachMini()
        }

        v.findViewById<TextView>(R.id.btnShiftReset)?.setOnClickListener {
            shiftStartMs = System.currentTimeMillis()
            if (Settings.autoBreakJournal) {
                ShiftJournal.close(this)
                ShiftJournal.open(this, ShiftJournal.WORK)
            }
            toast(getString(R.string.toast_shift_reset))
        }

        v.findViewById<TextView>(R.id.btnSos)?.setOnClickListener {
            val r = SosHelper.trigger(this)
            toast(r.getOrElse { it.message ?: "SOS gagal" })
        }

        val header = v.findViewById<View>(R.id.header) ?: return
        header.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragDX = e.rawX; dragDY = e.rawY
                    lastInteractionAt = System.currentTimeMillis()
                }
                MotionEvent.ACTION_MOVE -> {
                    p.x += (e.rawX - dragDX).toInt()
                    p.y += (e.rawY - dragDY).toInt()
                    dragDX = e.rawX; dragDY = e.rawY
                    runCatching { wm.updateViewLayout(v, p) }
                }
                MotionEvent.ACTION_UP -> {
                    Settings.panelX = p.x; Settings.panelY = p.y; Settings.save()
                }
            }
            true
        }
    }

    // ---------------- Ticker & bantu ----------------

    private fun refresh() {
        val v = panel ?: return
        val shiftMs = System.currentTimeMillis() - shiftStartMs
        val spd = if (Settings.speedometer) speedo?.display() else null
        if (panelMode == PanelController.Mode.FULL) {
            controller.bindSummary(v, shiftMs, batteryPct, spd)
        } else {
            controller.bindMini(v, shiftMs)
        }
    }

    private fun maybeAutoHide() {
        if (!Settings.autoHide) return
        val idleFor = System.currentTimeMillis() - lastInteractionAt
        if (idleFor > 30_000L) {
            bubble?.alpha = 0.35f
        } else {
            bubble?.alpha = 0.55f
        }
    }

    private fun maybeWarnBreak() {
        if (!Settings.autoBreakJournal) return
        if (Settings.restIntervalMin <= 0) return
        val now = System.currentTimeMillis()
        if (now - lastBreakWarnAt < 30 * 60_000L) return
        val worked = now - shiftStartMs
        if (worked >= Settings.restIntervalMin * 60_000L) {
            lastBreakWarnAt = now
            toast(getString(R.string.toast_break_time))
        }
    }

    private fun maybeStartSpeedometer() {
        if (!Settings.speedometer) return
        val s = speedo ?: Speedometer(this).also { speedo = it }
        val ok = s.start()
        if (!ok) toast(getString(R.string.toast_no_location))
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private fun toast(msg: String) {
        runCatching { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
    }

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()
}
