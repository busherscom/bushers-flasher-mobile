package io.github.ajsb85.esptoolkt.sample

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.util.Log
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.hoho.android.usbserial.driver.UsbSerialDriver
import io.github.ajsb85.esptoolkt.EspLoader
import io.github.ajsb85.esptoolkt.EspLogger
import io.github.ajsb85.esptoolkt.android.UsbSerial
import io.github.ajsb85.esptoolkt.android.UsbSerialTransport
import io.github.ajsb85.esptoolkt.flasher.FlashSegment
import io.github.ajsb85.esptoolkt.flasher.Flasher
import io.github.ajsb85.esptoolkt.image.FlashFreq
import io.github.ajsb85.esptoolkt.image.FlashMode
import io.github.ajsb85.esptoolkt.image.FlashSettings
import io.github.ajsb85.esptoolkt.image.FlashSize
import io.github.ajsb85.esptoolkt.transport.ClassicReset
import io.github.ajsb85.esptoolkt.transport.HardReset
import io.github.ajsb85.esptoolkt.transport.UsbJtagReset

/**
 * Reference integration: enumerate the attached USB-serial bridge, obtain USB permission, and use
 * the esptool-kt `.aar` ([UsbSerialTransport] + the platform-agnostic [EspLoader]/[Flasher]) to
 * flash the bundled ESP32-S2 firmware, then reboot and stream the boot log. Everything is mirrored
 * to logcat (tag `ESPTOOLKT`) so the run can be observed headlessly via `adb logcat`.
 */
class MainActivity : Activity() {

    private val action by lazy { "$packageName.USB_PERMISSION" }
    private lateinit var logView: TextView
    private var started = false

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != action) return
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            log(if (granted) "USB permission granted." else "USB permission denied.")
            runCatching { unregisterReceiver(this) }
            if (granted) begin()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logView = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setPadding(24, 24, 24, 24)
        }
        setContentView(ScrollView(this).apply { addView(logView) })
        log("esptool-kt sample — flashing ESP32-S2 over USB-OTG")
        begin()
    }

    private fun begin() {
        val manager = getSystemService(Context.USB_SERVICE) as UsbManager
        val drivers = UsbSerial.availableDrivers(manager)
        if (drivers.isEmpty()) {
            log("No USB-serial devices found.")
            return
        }
        
        // Find first driver we don't have permission for
        val driverWithoutPermission = drivers.firstOrNull { !manager.hasPermission(it.device) }
        if (driverWithoutPermission != null) {
            log("Requesting USB permission for ${driverWithoutPermission.device.deviceName}…")
            val flags = PendingIntent.FLAG_MUTABLE
            val pi = PendingIntent.getBroadcast(this, 0, Intent(action).setPackage(packageName), flags)
            ContextCompat.registerReceiver(this, permissionReceiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
            manager.requestPermission(driverWithoutPermission.device, pi)
            return
        }

        // We have permission for all devices! Let's probe all of them.
        log("Have permission for all ${drivers.size} devices. Probing...")
        if (!started) {
            started = true
            Thread { runProbing(manager, drivers) }.start()
        }
    }

    private fun runProbing(manager: UsbManager, drivers: List<UsbSerialDriver>) {
        for (driver in drivers) {
            val dev = driver.device
            log("Probing device: name=${dev.deviceName}, vid=0x%04x, pid=0x%04x".format(dev.vendorId, dev.productId))
            try {
                val transport = UsbSerialTransport(UsbSerial.open(manager, driver))
                val loader = EspLoader(transport, logger)
                val nativeUsb = dev.vendorId == 0x303A
                val flowControl = dev.vendorId == 0x10C4 && dev.productId == 0xEA64
                log("Connecting…")
                loader.connect(if (nativeUsb) UsbJtagReset() else ClassicReset(flowControl = flowControl))
                val chip = loader.chip?.name
                val mac = loader.readMac().joinToString(":") { "%02x".format(it) }
                log("PROBE SUCCESS -> device=${dev.deviceName}, chip=$chip, mac=$mac")
                runCatching { transport.close() }
            } catch (e: Exception) {
                log("PROBE FAILED -> device=${dev.deviceName}: ${e.message}")
            }
        }
        log("PROBING DONE ✓")
    }

    private fun asset(name: String): ByteArray = assets.open(name).use { it.readBytes() }

    private val logger = object : EspLogger {
        override fun info(message: String) = log(message)
        override fun detail(message: String) {
            Log.d(TAG, message)
        }
        override fun progress(label: String, done: Long, total: Long) {
            if (total > 0 && done >= total) log("  $label: wrote $total bytes")
        }
    }

    private fun log(message: String) {
        Log.i(TAG, message)
        runOnUiThread { logView.append(message + "\n") }
    }

    companion object {
        private const val TAG = "ESPTOOLKT"
    }
}
