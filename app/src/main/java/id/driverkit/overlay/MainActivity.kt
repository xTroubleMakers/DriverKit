package id.driverkit.overlay

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.provider.Settings.Secure
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/** Layar utama: pemberian izin + kontrol overlay. */
class MainActivity : AppCompatActivity() {

    private val reqNotif = 2001
    private var permissionStateReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Settings.init(this)
        Settings.load()
        Settings.applyTheme()
        setContentView(R.layout.activity_main)

        findViewById<View>(R.id.btnOverlayPerm).setOnClickListener { askOverlay() }
        findViewById<View>(R.id.btnNotifPerm).setOnClickListener { askNotifAccess() }
        findViewById<Button>(R.id.btnStart).setOnClickListener { startOverlay() }
        findViewById<Button>(R.id.btnStop).setOnClickListener { stopOverlay() }
        findViewById<View>(R.id.btnStats).setOnClickListener {
            startActivity(Intent(this, StatsActivity::class.java))
        }
        findViewById<View>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        refreshPermissionState()
        askNotificationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        if (permissionStateReady) refreshPermissionState()
    }

    private fun refreshPermissionState() {
        val overlayOn = AndroidSettings.canDrawOverlays(this)
        findViewById<TextView>(R.id.txtOverlayState).apply {
            text = getString(if (overlayOn) R.string.state_ready else R.string.state_off)
            setTextColor(ContextCompat.getColor(this@MainActivity, if (overlayOn) R.color.indigo else R.color.muted))
        }

        val notifOn = Secure.getString(contentResolver, "enabled_notification_listeners")
            ?.split(":")
            ?.any { it.equals(componentNameString(), ignoreCase = true) } == true
        findViewById<TextView>(R.id.txtNotifState).apply {
            text = getString(if (notifOn) R.string.state_ready else R.string.state_off)
            setTextColor(ContextCompat.getColor(this@MainActivity, if (notifOn) R.color.indigo else R.color.muted))
        }
        permissionStateReady = true
    }

    private fun componentNameString(): String =
        "$packageName/${packageName}.NotificationListenerService"

    private fun askOverlay() {
        if (AndroidSettings.canDrawOverlays(this)) {
            toast(getString(R.string.toast_overlay_permission_active))
            return
        }
        val i = Intent(
            AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        runCatching { startActivity(i) }
            .onFailure { toast(getString(R.string.open_overlay_settings)) }
    }

    private fun askNotifAccess() {
        val i = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
        runCatching { startActivity(i) }
            .onFailure { toast(getString(R.string.open_notif_settings)) }
    }

    private fun askNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < 33) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), reqNotif
            )
        }
    }

    private fun startOverlay() {
        if (!AndroidSettings.canDrawOverlays(this)) {
            toast(getString(R.string.enable_overlay_first))
            askOverlay()
            return
        }
        Settings.init(this)
        Settings.load()
        ReminderScheduler.reschedule(this)
        val i = Intent(this, OverlayService::class.java)
        runCatching { startForegroundService(i) }
            .onFailure { startService(i) }
        toast(getString(R.string.toast_overlay_started))
    }

    private fun stopOverlay() {
        stopService(Intent(this, OverlayService::class.java))
        toast(getString(R.string.toast_overlay_stopped))
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
