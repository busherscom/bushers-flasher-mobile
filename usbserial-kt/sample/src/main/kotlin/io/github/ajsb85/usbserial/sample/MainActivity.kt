package io.github.ajsb85.usbserial.sample

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
import io.github.ajsb85.usbserial.UsbSerial
import io.github.ajsb85.usbserial.UsbSerialDriver

/**
 * Validates the pure-Kotlin driver against real hardware: open the attached ESP bridge, drive the
 * auto-reset lines to reboot the chip, and stream its serial output. Mirrored to logcat
 * (`USBSERIALKT`) so it can be observed headlessly.
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
        log("usb-serial-for-android-kt sample")
        begin()
    }

    private fun begin() {
        val manager = getSystemService(Context.USB_SERVICE) as UsbManager
        val drivers = UsbSerial.availableDrivers(manager)
        if (drivers.isEmpty()) {
            log("No USB-serial device found. Attach an ESP board.")
            return
        }
        val driver = drivers.first()
        log("Found ${driver.javaClass.simpleName}: vid=0x%04x pid=0x%04x".format(driver.device.vendorId, driver.device.productId))
        if (manager.hasPermission(driver.device)) {
            startMonitor(manager, driver)
        } else {
            log("Requesting USB permission…")
            val pi = PendingIntent.getBroadcast(this, 0, Intent(action).setPackage(packageName), PendingIntent.FLAG_MUTABLE)
            ContextCompat.registerReceiver(this, permissionReceiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
            manager.requestPermission(driver.device, pi)
        }
    }

    private fun startMonitor(manager: UsbManager, driver: UsbSerialDriver) {
        if (started) return
        started = true
        Thread { runMonitor(manager, driver) }.start()
    }

    private fun runMonitor(manager: UsbManager, driver: UsbSerialDriver) {
        try {
            val port = UsbSerial.open(manager, driver, baudRate = 115200)
            log("Port open. Serial=${runCatching { port.serial }.getOrNull()}")

            // Classic auto-reset into the running app: IO0 high (DTR=false), pulse EN (RTS).
            log("Resetting chip (DTR/RTS)…")
            port.setDTR(false)
            port.setRTS(true)
            Thread.sleep(100)
            port.setRTS(false)

            log("Reading serial @115200 for 6 s…")
            val sb = StringBuilder()
            val buf = ByteArray(256)
            val end = System.currentTimeMillis() + 6000
            while (System.currentTimeMillis() < end) {
                val n = port.read(buf, 400)
                if (n > 0) sb.append(String(buf, 0, n, Charsets.US_ASCII))
            }
            val line = sb.lines().firstOrNull { it.contains("ESP32_MSG_START") || it.contains("boot:") }
            log("Read ${sb.length} bytes." + (line?.let { " First app/boot line: ${it.trim()}" } ?: " (no recognizable line)"))
            port.close()
            log("DONE ✓ — Kotlin driver opened, reset, and read the device.")
        } catch (e: Exception) {
            Log.e(TAG, "monitor failed", e)
            log("ERROR: ${e.message}")
        }
    }

    private fun log(message: String) {
        Log.i(TAG, message)
        runOnUiThread { logView.append(message + "\n") }
    }

    private companion object {
        const val TAG = "USBSERIALKT"
    }
}
