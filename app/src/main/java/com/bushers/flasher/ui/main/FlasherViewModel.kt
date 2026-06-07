package com.bushers.flasher.ui.main

import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import io.github.ajsb85.usbserial.UsbSerialDriver
import io.github.ajsb85.usbserial.UsbSerialPort
import io.github.ajsb85.usbserial.UsbSerialProber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

enum class DeviceStatus {
    READY,
    PROBING,
    COMPATIBLE,
    INCOMPATIBLE,
    PROBE_FAILED,
    NEED_PERMISSION
}

data class DeviceInfo(
    val devicePath: String,
    val driver: UsbSerialDriver?,
    val name: String,
    val hasPermission: Boolean,
    val chipType: String = "Unknown",
    val status: DeviceStatus = DeviceStatus.READY,
    val workingPortIdx: Int = 0,
    val isMock: Boolean = false
)

data class TerminalLineData(val tag: String, val message: String, val isError: Boolean = false, val isWarning: Boolean = false)

data class FlasherUiState(
    val devices: List<DeviceInfo> = emptyList(),
    val selectedDevice: DeviceInfo? = null,
    val isFlashing: Boolean = false,
    val flashProgress: Float = 0f,
    val targetChip: String = "Unknown",
    val flashStatus: String = "IDLE",
    val terminalLogs: List<TerminalLineData> = emptyList(),
    val isCompatibleWithFirmware: Boolean = false
)

class FlasherViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        const val TAG_DEBUG = "FlasherDebug"
        const val ACTION_PERMISSION = "com.bushers.flasher.USB_PERMISSION"
    }

    private val usbManager = application.getSystemService(Context.USB_SERVICE) as UsbManager
    private val hardwareMutex = Mutex()
    private var activePort: UsbSerialPort? = null
    private var activeConnection: UsbDeviceConnection? = null
    private var isMockMode = false

    private val _uiState = MutableStateFlow(FlasherUiState())
    val uiState: StateFlow<FlasherUiState> = _uiState.asStateFlow()

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_PERMISSION) {
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                Log.d(TAG_DEBUG, "USB Permission: $granted")
                scanDevices()
                if (granted) {
                    val device = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java) else intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    device?.let { usbDev ->
                        viewModelScope.launch {
                            delay(1000)
                            _uiState.value.devices.find { it.devicePath == usbDev.deviceName }?.let { probeChip(it) }
                        }
                    }
                }
            }
        }
    }

    init {
        Log.d(TAG_DEBUG, "ViewModel INIT START (isMockMode=$isMockMode)")
        val filter = IntentFilter(ACTION_PERMISSION)
        ContextCompat.registerReceiver(application, permissionReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        scanDevices()
    }

    fun setMockMode(enabled: Boolean) {
        isMockMode = enabled
        scanDevices()
        if (enabled) logToUi("INFO", "MOCK MODE ENABLED")
    }

    fun scanDevices() {
        if (isMockMode) {
            _uiState.update { it.copy(devices = listOf(
                DeviceInfo("mock_esp32", null, "Standard ESP32 (MOCK)", true, "Unknown", DeviceStatus.READY, isMock = true),
                DeviceInfo("mock_s2", null, "ESP32-S2 (MOCK)", true, "Unknown", DeviceStatus.READY, isMock = true)
            )) }
            Log.d(TAG_DEBUG, "Mocks populated in scanDevices")
            return
        }

        val prober = UsbSerialProber.getDefaultProber()
        val allDrivers = prober.findAllDrivers(usbManager)
        
        val filteredDrivers = allDrivers.filter { driver ->
            val vid = driver.device.vendorId
            val pid = driver.device.productId
            !(vid == 0x303A && pid == 0x1001) // Exclude JTAG
        }

        val deviceList = filteredDrivers.map { driver ->
            val hasPerm = usbManager.hasPermission(driver.device)
            val existing = _uiState.value.devices.find { it.devicePath == driver.device.deviceName }
            DeviceInfo(
                devicePath = driver.device.deviceName,
                driver = driver,
                name = driver.device.productName ?: "USB Serial Device",
                hasPermission = hasPerm,
                chipType = existing?.chipType ?: "Unknown",
                status = if (hasPerm) (existing?.status ?: DeviceStatus.READY) else DeviceStatus.NEED_PERMISSION
            )
        }
        _uiState.update { it.copy(devices = deviceList) }
        Log.d(TAG_DEBUG, "Scan: Found ${deviceList.size} devices")
    }

    private fun cleanupResources() {
        try { activePort?.let { if (it.isOpen) it.close() } } catch (_: Exception) {}
        try { activeConnection?.close() } catch (_: Exception) {}
        activePort = null
        activeConnection = null
    }

    fun selectDevice(deviceInfo: DeviceInfo) {
        Log.d(TAG_DEBUG, "Select call for ${deviceInfo.name}")
        if (deviceInfo.status == DeviceStatus.INCOMPATIBLE) {
            logToUi("WARN", "Device blocked: Non-ESP32.")
            return
        }
        _uiState.update { it.copy(selectedDevice = deviceInfo) }
        if (deviceInfo.isMock || (deviceInfo.hasPermission && deviceInfo.driver != null)) {
            if (deviceInfo.status != DeviceStatus.COMPATIBLE) probeChip(deviceInfo)
        } else if (deviceInfo.driver != null) {
            val flags = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            val pi = PendingIntent.getBroadcast(getApplication(), 0, Intent(ACTION_PERMISSION).setPackage(getApplication<Application>().packageName), flags)
            usbManager.requestPermission(deviceInfo.driver.device, pi)
        }
    }

    private fun probeChip(deviceInfo: DeviceInfo) {
        if (deviceInfo.isMock) {
            simulateProbe(deviceInfo)
            return
        }
        if (deviceInfo.status == DeviceStatus.PROBING || deviceInfo.driver == null) return
        updateDeviceState(deviceInfo.devicePath, DeviceStatus.PROBING, "Unknown")
        
        viewModelScope.launch(Dispatchers.IO) {
            hardwareMutex.withLock {
                cleanupResources()
                var detectedChip = "Unknown"
                var finalStatus = DeviceStatus.PROBE_FAILED
                
                try {
                    val conn = usbManager.openDevice(deviceInfo.driver.device) ?: throw IOException("Denied")
                    activeConnection = conn
                    val port = deviceInfo.driver.ports.first()
                    port.open(conn)
                    activePort = port
                    port.setParameters(115200, 8, 1, UsbSerialPort.PARITY_NONE)
                    val transport = UsbSerialTransport(port)
                    transport.flushInput()
                    transport.setDtr(false)
                    transport.setRts(false)
                    delay(1000)

                    val loader = EspLoader(transport, NoopLogger)
                    loader.connect(ClassicReset(), attempts = 5)
                    detectedChip = loader.chip?.name ?: "Unknown"
                    finalStatus = if (detectedChip == "ESP32") DeviceStatus.COMPATIBLE else DeviceStatus.INCOMPATIBLE
                    
                } catch (e: Exception) {
                    Log.e(TAG_DEBUG, "Identification failed: ${e.message}")
                } finally {
                    cleanupResources()
                    withContext(Dispatchers.Main) {
                        updateDeviceState(deviceInfo.devicePath, finalStatus, detectedChip)
                        logToUi("INFO", "Chip Detected: $detectedChip (Status: $finalStatus)")
                    }
                }
            }
        }
    }

    private fun simulateProbe(deviceInfo: DeviceInfo) {
        updateDeviceState(deviceInfo.devicePath, DeviceStatus.PROBING, "Unknown")
        viewModelScope.launch {
            delay(1500)
            val chip = if (deviceInfo.name.contains("Standard")) "ESP32" else "ESP32-S2"
            updateDeviceState(deviceInfo.devicePath, if (chip == "ESP32") DeviceStatus.COMPATIBLE else DeviceStatus.INCOMPATIBLE, chip)
            logToUi("INFO", "Mock Identify: $chip")
        }
    }

    private fun updateDeviceState(devicePath: String, status: DeviceStatus, chipType: String) {
        _uiState.update { state ->
            val updated = state.devices.map { if (it.devicePath == devicePath) it.copy(status = status, chipType = chipType) else it }
            val updatedSelected = if (state.selectedDevice?.devicePath == devicePath) state.selectedDevice.copy(status = status, chipType = chipType) else state.selectedDevice
            state.copy(devices = updated, selectedDevice = updatedSelected, isCompatibleWithFirmware = updatedSelected?.status == DeviceStatus.COMPATIBLE, targetChip = updatedSelected?.chipType ?: "Unknown")
        }
    }

    private fun logToUi(tag: String, message: String, isError: Boolean = false, isWarning: Boolean = false) {
        Log.d(TAG_DEBUG, "UI: [$tag] $message")
        _uiState.update { state ->
            val logs = state.terminalLogs.toMutableList()
            logs.add(TerminalLineData(tag, message, isError, isWarning))
            if (logs.size > 100) logs.removeAt(0)
            state.copy(terminalLogs = logs)
        }
    }

    fun startFlashing() {
        val selected = _uiState.value.selectedDevice ?: return
        Log.d(TAG_DEBUG, "startFlashing: ${selected.name} status=${selected.status}")
        if (selected.status != DeviceStatus.COMPATIBLE) return
        _uiState.update { it.copy(isFlashing = true, flashProgress = 0f, flashStatus = "STARTING") }
        
        viewModelScope.launch(Dispatchers.IO) {
            if (selected.isMock) {
                for (i in 0..10) {
                    delay(300)
                    _uiState.update { it.copy(flashProgress = i / 10f, flashStatus = "MOCK FLASHING") }
                }
                _uiState.update { it.copy(flashStatus = "SUCCESS", isFlashing = false) }
                logToUi("INFO", "MOCK FLASH SUCCESS")
                return@launch
            }
            hardwareMutex.withLock {
                cleanupResources()
                try {
                    val context = getApplication<Application>()
                    val firmware = context.assets.open("Bushers_ESP32_DEV_SPI_FULL_SSD1306.ino.merged.bin").readBytes()
                    val conn = usbManager.openDevice(selected.driver!!.device) ?: throw IOException("Denied")
                    activeConnection = conn
                    val port = selected.driver.ports.first()
                    port.open(conn)
                    activePort = port
                    port.setParameters(115200, 8, 1, UsbSerialPort.PARITY_NONE)
                    val transport = UsbSerialTransport(port)
                    val appLogger = object : EspLogger {
                        override fun info(message: String) { logToUi("ESP", message) }
                        override fun progress(label: String, done: Long, total: Long) {
                            _uiState.update { it.copy(flashProgress = done.toFloat() / total.toFloat(), flashStatus = "FLASHING") }
                        }
                    }
                    val loader = EspLoader(transport, appLogger)
                    loader.connect(ClassicReset())
                    loader.runStub()
                    loader.changeBaud(460_800)
                    Flasher(loader, appLogger).writeFlash(listOf(FlashSegment(0x0, firmware, "merged.bin")), FlashSettings(FlashMode.DIO, FlashSize.S4MB, FlashFreq.F40M))
                    _uiState.update { it.copy(flashStatus = "SUCCESS", flashProgress = 1f) }
                    logToUi("INFO", "SUCCESS!")
                } catch (e: Exception) {
                    logToUi("ERR", "Flash Error: ${e.message}", isError = true)
                    _uiState.update { it.copy(flashStatus = "FAILED") }
                } finally {
                    cleanupResources()
                    withContext(Dispatchers.Main) { _uiState.update { it.copy(isFlashing = false) } }
                }
            }
        }
    }

    fun getDeviceByIndex(index: Int): DeviceInfo? = _uiState.value.devices.getOrNull(index)
}

object NoopLogger : EspLogger {
    override fun info(message: String) {}
}
