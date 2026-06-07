package com.bushers.flasher.ui.main

import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
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
import kotlinx.coroutines.withTimeout
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
    val driver: UsbSerialDriver,
    val name: String,
    val hasPermission: Boolean,
    val chipType: String = "Unknown",
    val status: DeviceStatus = DeviceStatus.READY,
    val lastError: String? = null
)

data class TerminalLineData(val tag: String, val message: String, val isError: Boolean = false, val isWarning: Boolean = false)

data class FlasherUiState(
    val devices: List<DeviceInfo> = emptyList(),
    val selectedDevice: DeviceInfo? = null,
    val isFlashing: Boolean = false,
    val flashProgress: Float = 0f,
    val terminalLogs: List<TerminalLineData> = emptyList(),
    val targetChip: String = "Unknown",
    val flashStatus: String = "IDLE",
    val isCompatibleWithFirmware: Boolean = false
)

class FlasherViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        const val TAG_INFO = "INFO"
        const val TAG_WARN = "WARN"
        const val TAG_ERR = "ERR"
        const val TAG_ESP = "ESP"
        const val TAG_DEBUG = "DEBUG"
        const val ACTION_PERMISSION = "com.bushers.flasher.USB_PERMISSION"
    }

    private val usbManager = application.getSystemService(Context.USB_SERVICE) as UsbManager
    private val hardwareMutex = Mutex()
    private var activePort: UsbSerialPort? = null

    private val _uiState = MutableStateFlow(FlasherUiState())
    val uiState: StateFlow<FlasherUiState> = _uiState.asStateFlow()

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_PERMISSION) {
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                val device = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                }
                
                if (granted && device != null) {
                    logToUi(TAG_INFO, "USB Permission Granted.")
                    scanDevices()
                    viewModelScope.launch {
                        delay(1000)
                        val state = _uiState.value
                        state.devices.find { it.driver.device.deviceName == device.deviceName }?.let { probeChip(it) }
                    }
                } else {
                    logToUi(TAG_WARN, "USB Permission Denied.")
                }
            }
        }
    }

    init {
        val filter = IntentFilter(ACTION_PERMISSION)
        ContextCompat.registerReceiver(application, permissionReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        scanDevices()
    }

    fun scanDevices() {
        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        val deviceList = drivers.map { driver ->
            val hasPerm = usbManager.hasPermission(driver.device)
            val name = driver.device.productName ?: "USB Serial Device"
            val existing = _uiState.value.devices.find { it.driver.device.deviceName == driver.device.deviceName }
            
            DeviceInfo(
                driver = driver,
                name = name,
                hasPermission = hasPerm,
                chipType = existing?.chipType ?: "Unknown",
                status = if (!hasPerm) DeviceStatus.NEED_PERMISSION else (existing?.status ?: DeviceStatus.READY),
                lastError = existing?.lastError
            )
        }
        _uiState.update { it.copy(devices = deviceList) }
    }

    fun requestPermission(deviceInfo: DeviceInfo) {
        logToUi(TAG_INFO, "Requesting USB permission...")
        val pi = PendingIntent.getBroadcast(getApplication(), 0, 
            Intent(ACTION_PERMISSION).setPackage(getApplication<Application>().packageName), 
            PendingIntent.FLAG_MUTABLE)
        usbManager.requestPermission(deviceInfo.driver.device, pi)
    }

    fun selectDevice(deviceInfo: DeviceInfo) {
        _uiState.update { it.copy(
            selectedDevice = deviceInfo,
            isCompatibleWithFirmware = deviceInfo.status == DeviceStatus.COMPATIBLE,
            targetChip = deviceInfo.chipType
        ) }
        
        if (!deviceInfo.hasPermission) {
            requestPermission(deviceInfo)
        } else if (deviceInfo.status == DeviceStatus.READY || deviceInfo.status == DeviceStatus.PROBE_FAILED) {
            probeChip(deviceInfo)
        }
    }

    private fun probeChip(deviceInfo: DeviceInfo) {
        updateDeviceState(deviceInfo, DeviceStatus.PROBING, "Unknown")
        viewModelScope.launch(Dispatchers.IO) {
            hardwareMutex.withLock {
                ensurePortClosed()
                var port: UsbSerialPort? = null
                try {
                    withTimeout(30000L) {
                        logToUi(TAG_INFO, "Opening bridge to ${deviceInfo.name}...")
                        val p = UsbSerial.open(usbManager, deviceInfo.driver)
                        port = p
                        activePort = p
                        delay(200)
                        
                        val transport = UsbSerialTransport(p, writeTimeoutMs = 3000)
                        transport.baudRate = 115200 
                        val loader = EspLoader(transport, NoopLogger)
                        val vid = deviceInfo.driver.device.vendorId
                        val pid = deviceInfo.driver.device.productId
                        
                        val strategies = listOf(
                             if (vid == 0x303A) UsbJtagReset() else ClassicReset(flowControl = (vid == 0x10C4 && pid == 0xEA64)),
                             if (vid == 0x303A) ClassicReset(flowControl = false) else UsbJtagReset()
                        )
                        
                        var identifiedChip = "Unknown"
                        var connected = false
                        for (strategy in strategies) {
                            try {
                                logToUi(TAG_INFO, "Trying Reset Strategy: ${strategy.javaClass.simpleName}...")
                                loader.connect(strategy, attempts = 5)
                                identifiedChip = loader.chip?.name ?: "Unknown"
                                connected = true
                                break
                            } catch (_: Exception) {}
                        }
                        
                        if (connected) {
                            val isCompatible = identifiedChip == "ESP32"
                            withContext(Dispatchers.Main) {
                                updateDeviceState(deviceInfo, if (isCompatible) DeviceStatus.COMPATIBLE else DeviceStatus.INCOMPATIBLE, identifiedChip)
                                logToUi(TAG_INFO, ">>> IDENTIFIED: $identifiedChip <<<")
                            }
                        } else {
                            throw IOException("ESP Hardware not responding to serial sync.")
                        }
                    }
                } catch (e: Exception) {
                    val errorMsg = e.message ?: "Identification Failed"
                    logToUi(TAG_ERR, "Error: $errorMsg")
                    withContext(Dispatchers.Main) { 
                        updateDeviceState(deviceInfo, DeviceStatus.PROBE_FAILED, "Unknown", errorMsg) 
                    }
                } finally {
                    ensurePortClosed()
                    delay(1000)
                }
            }
        }
    }

    private fun updateDeviceState(deviceInfo: DeviceInfo, status: DeviceStatus, chipType: String, error: String? = null) {
        _uiState.update { state ->
            val updated = state.devices.map { 
                if (it.driver.device.deviceName == deviceInfo.driver.device.deviceName) 
                    it.copy(status = status, chipType = chipType, lastError = error) 
                else it 
            }
            val updatedSelected = if (state.selectedDevice?.driver?.device?.deviceName == deviceInfo.driver.device.deviceName) {
                state.selectedDevice.copy(status = status, chipType = chipType, lastError = error)
            } else state.selectedDevice
            
            state.copy(
                devices = updated, 
                selectedDevice = updatedSelected,
                isCompatibleWithFirmware = updatedSelected?.status == DeviceStatus.COMPATIBLE,
                targetChip = updatedSelected?.chipType ?: "Unknown"
            )
        }
    }

    private fun ensurePortClosed() {
        try { activePort?.let { if (it.isOpen) it.close() } } catch (_: Exception) {} finally { activePort = null }
    }

    private fun logToUi(tag: String, message: String, isError: Boolean = false, isWarning: Boolean = false) {
        Log.i("FlasherViewModel", "[$tag] $message")
        _uiState.update { state ->
            val newLogs = state.terminalLogs.toMutableList()
            newLogs.add(TerminalLineData(tag, message, isError, isWarning))
            if (newLogs.size > 200) newLogs.removeAt(0)
            state.copy(terminalLogs = newLogs)
        }
    }

    fun startFlashing() {
        val state = _uiState.value
        val selected = state.selectedDevice ?: return
        if (selected.status != DeviceStatus.COMPATIBLE || state.isFlashing) return

        _uiState.update { it.copy(isFlashing = true, flashProgress = 0f, flashStatus = "STARTING") }
        
        viewModelScope.launch(Dispatchers.IO) {
            hardwareMutex.withLock {
                ensurePortClosed()
                var port: UsbSerialPort? = null
                try {
                    val context = getApplication<Application>()
                    val firmwareBytes = context.assets.open("Bushers_ESP32_DEV_SPI_FULL_SSD1306.ino.merged.bin").readBytes()
                    
                    val p = UsbSerial.open(usbManager, selected.driver)
                    port = p
                    activePort = p
                    
                    val transport = UsbSerialTransport(p, writeTimeoutMs = 3000)
                    val appLogger = object : EspLogger {
                        override fun info(message: String) { logToUi(TAG_INFO, message) }
                        override fun progress(label: String, done: Long, total: Long) {
                            _uiState.update { it.copy(flashProgress = done.toFloat() / total.toFloat()) }
                        }
                    }
                    
                    val loader = EspLoader(transport, appLogger)
                    val vid = selected.driver.device.vendorId
                    val pid = selected.driver.device.productId
                    val resetStrategy = if (vid == 0x303A) UsbJtagReset() else ClassicReset(flowControl = (vid == 0x10C4 && pid == 0xEA64))
                    
                    loader.connect(resetStrategy)
                    loader.runStub()
                    if (vid != 0x303A) loader.changeBaud(460_800)

                    _uiState.update { it.copy(flashStatus = "FLASHING") }
                    val settings = FlashSettings(FlashMode.DIO, getFlashSizeFromBytes(loader.detectFlashSize() ?: 0L), FlashFreq.F40M)
                    Flasher(loader, appLogger).writeFlash(listOf(FlashSegment(0x0, firmwareBytes, "merged.bin")), settings)

                    logToUi(TAG_INFO, "SUCCESS! Device Flashed.")
                    _uiState.update { it.copy(flashStatus = "DONE", flashProgress = 1f) }
                    loader.hardReset(HardReset(flowControl = (vid == 0x10C4 && pid == 0xEA64)))
                } catch (e: Exception) {
                    logToUi(TAG_ERR, "FLASH ERROR: ${e.message}")
                    _uiState.update { it.copy(flashStatus = "FAILED") }
                } finally {
                    ensurePortClosed()
                    withContext(Dispatchers.Main) { _uiState.update { it.copy(isFlashing = false) } }
                }
            }
        }
    }

    private fun getFlashSizeFromBytes(bytes: Long): FlashSize {
        return when {
            bytes >= 4L * 1024 * 1024 -> FlashSize.S4MB
            bytes >= 2L * 1024 * 1024 -> FlashSize.S2MB
            bytes >= 8L * 1024 * 1024 -> FlashSize.S8MB
            bytes >= 16L * 1024 * 1024 -> FlashSize.S16MB
            else -> FlashSize.S2MB
        }
    }

    fun getDeviceByIndex(index: Int): DeviceInfo? = _uiState.value.devices.getOrNull(index)
}

object NoopLogger : EspLogger {
    override fun info(message: String) {}
}
