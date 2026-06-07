package com.bushers.flasher.ui.main

import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
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

data class DeviceInfo(
    val driver: UsbSerialDriver,
    val name: String,
    val hasPermission: Boolean,
    val chipType: String = "Unknown",
    val isCompatible: Boolean = false,
    val isProbing: Boolean = false
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
    private val usbMutex = Mutex()

    private val _uiState = MutableStateFlow(FlasherUiState())
    val uiState: StateFlow<FlasherUiState> = _uiState.asStateFlow()

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_PERMISSION) {
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                if (granted) {
                    logToUi(TAG_INFO, "USB Permission granted.")
                    scanDevices() // Rescan to update permission status
                } else {
                    logToUi(TAG_WARN, "USB Permission denied.")
                }
            }
        }
    }

    init {
        val filter = IntentFilter(ACTION_PERMISSION)
        ContextCompat.registerReceiver(
            application,
            permissionReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        scanDevices()
    }

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().unregisterReceiver(permissionReceiver)
        } catch (e: Exception) {
            // Ignored
        }
    }

    fun scanDevices() {
        logToUi(TAG_INFO, "Scanning for USB devices...")
        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        val deviceList = drivers.map { driver ->
            val hasPerm = usbManager.hasPermission(driver.device)
            val name = driver.device.productName ?: "USB Serial Device"
            
            // Retain probing info if we already know it
            val existing = _uiState.value.devices.find { it.driver.device.deviceName == driver.device.deviceName }
            
            DeviceInfo(
                driver = driver,
                name = name,
                hasPermission = hasPerm,
                chipType = existing?.chipType ?: "Unknown",
                isCompatible = existing?.isCompatible ?: false,
                isProbing = existing?.isProbing ?: false
            )
        }
        _uiState.update { it.copy(devices = deviceList) }
        logToUi(TAG_INFO, "Found ${deviceList.size} device(s).")
    }

    fun requestPermission(deviceInfo: DeviceInfo) {
        logToUi(TAG_INFO, "Requesting permission for ${deviceInfo.name}...")
        val flags = PendingIntent.FLAG_IMMUTABLE
        val pi = PendingIntent.getBroadcast(
            getApplication(),
            0,
            Intent(ACTION_PERMISSION).setPackage(getApplication<Application>().packageName),
            flags
        )
        usbManager.requestPermission(deviceInfo.driver.device, pi)
    }

    fun selectDevice(deviceInfo: DeviceInfo) {
        _uiState.update { it.copy(selectedDevice = deviceInfo) }
        logToUi(TAG_INFO, "Selected device: ${deviceInfo.name}")
        
        if (deviceInfo.hasPermission && (deviceInfo.chipType == "Unknown" || deviceInfo.chipType == "Probing")) {
            probeChip(deviceInfo)
        }
    }

    private fun probeChip(deviceInfo: DeviceInfo) {
        logToUi(TAG_INFO, "Probing chip type for ${deviceInfo.name}...")
        
        _uiState.update { state ->
            state.copy(devices = state.devices.map { 
                if (it.driver.device.deviceName == deviceInfo.driver.device.deviceName) it.copy(isProbing = true, chipType = "Probing...") else it 
            })
        }

        viewModelScope.launch(Dispatchers.IO) {
            usbMutex.withLock {
                var transport: UsbSerialTransport? = null
                try {
                    val serialPort = UsbSerial.open(usbManager, deviceInfo.driver)
                    transport = UsbSerialTransport(serialPort)
                    
                    val loader = EspLoader(transport, NoopLogger)
                    val nativeUsb = deviceInfo.driver.device.vendorId == 0x303A
                    
                    // Comprehensive Reset Strategy List
                    val strategies = mutableListOf(
                        ClassicReset(flowControl = false),
                        UsbJtagReset(),
                        ClassicReset(flowControl = true)
                    )
                    
                    var connected = false
                    var chip = "Unknown"
                    
                    for (strategy in strategies) {
                        try {
                            logToUi(TAG_INFO, "Testing Reset: ${strategy.javaClass.simpleName} (VID:${String.format("%04X", deviceInfo.driver.device.vendorId)})")
                            loader.connect(strategy)
                            chip = loader.chip?.name ?: "ESP"
                            connected = true
                            break
                        } catch (e: Exception) {
                            // Continue to next strategy
                        }
                    }
                    
                    if (connected) {
                        val isCompatible = chip == "ESP32" 
                        withContext(Dispatchers.Main) {
                            _uiState.update { state ->
                                val updatedDevices = state.devices.map { 
                                    if (it.driver.device.deviceName == deviceInfo.driver.device.deviceName) {
                                        it.copy(chipType = chip, isCompatible = isCompatible, isProbing = false)
                                    } else it 
                                }
                                val updatedSelected = if (state.selectedDevice?.driver?.device?.deviceName == deviceInfo.driver.device.deviceName) {
                                    state.selectedDevice.copy(chipType = chip, isCompatible = isCompatible, isProbing = false)
                                } else state.selectedDevice
                                
                                state.copy(
                                    devices = updatedDevices, 
                                    selectedDevice = updatedSelected,
                                    isCompatibleWithFirmware = isCompatible,
                                    targetChip = chip
                                )
                            }
                            logToUi(TAG_INFO, ">>> IDENTIFICATION COMPLETE: $chip detected <<<")
                            if (isCompatible) {
                                logToUi(TAG_INFO, "Hardware is COMPATIBLE with firmware.")
                            } else {
                                logToUi(TAG_WARN, "Hardware is INCOMPATIBLE (requires ESP32).", isWarning = true)
                            }
                        }
                    } else {
                        throw IOException("No ESP response after trying all reset strategies.")
                    }
                } catch (e: Exception) {
                    logToUi(TAG_ERR, "Probe failed: ${e.message}", isError = true)
                    _uiState.update { state ->
                        state.copy(devices = state.devices.map { 
                            if (it.driver.device.deviceName == deviceInfo.driver.device.deviceName) it.copy(isProbing = false, chipType = "Probe Failed") else it 
                        })
                    }
                } finally {
                    try { transport?.close() } catch (e: Exception) {}
                    delay(1000) // Longer cooling off for physical USB bus
                }
            }
        }
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
        val selected = state.selectedDevice
        
        if (selected == null) {
            logToUi(TAG_ERR, "No device selected.", isError = true)
            return
        }
        if (!selected.hasPermission) {
            logToUi(TAG_ERR, "No permission for selected device.", isError = true)
            return
        }
        if (!state.isCompatibleWithFirmware) {
            logToUi(TAG_ERR, "Flash Aborted: Target hardware (${state.targetChip}) is incompatible with ESP32 firmware.", isError = true)
            return
        }

        _uiState.update { it.copy(isFlashing = true, flashProgress = 0f, flashStatus = "PREPARING...") }
        logToUi(TAG_INFO, "Initializing Flash Sequence...")

        viewModelScope.launch(Dispatchers.IO) {
            usbMutex.withLock {
                var transport: UsbSerialTransport? = null
                try {
                    val context = getApplication<Application>()
                    val firmwareBytes = context.assets.open("Bushers_ESP32_DEV_SPI_FULL_SSD1306.ino.merged.bin").readBytes()
                    
                    val serialPort = UsbSerial.open(usbManager, selected.driver)
                    transport = UsbSerialTransport(serialPort)
                    
                    val appLogger = object : EspLogger {
                        override fun info(message: String) { logToUi(TAG_INFO, message) }
                        override fun detail(message: String) { logToUi(TAG_DEBUG, message) }
                        override fun progress(label: String, done: Long, total: Long) {
                            _uiState.update { it.copy(flashProgress = done.toFloat() / total.toFloat()) }
                        }
                    }
                    
                    val loader = EspLoader(transport, appLogger)
                    val nativeUsb = selected.driver.device.vendorId == 0x303A
                    val flowControl = selected.driver.device.vendorId == 0x10C4 && selected.driver.device.productId == 0xEA64
                    
                    loader.connect(if (nativeUsb) UsbJtagReset() else ClassicReset(flowControl = flowControl))
                    loader.runStub()
                    
                    if (!nativeUsb) {
                        loader.changeBaud(460_800)
                    }

                    _uiState.update { it.copy(flashStatus = "FLASHING...") }
                    
                    val segments = listOf(FlashSegment(0x0, firmwareBytes, "merged_firmware.bin"))
                    val settings = FlashSettings(FlashMode.DIO, getFlashSizeFromBytes(loader.detectFlashSize() ?: 0L), FlashFreq.F40M)

                    Flasher(loader, appLogger).writeFlash(segments, settings)

                    logToUi(TAG_INFO, "Flash Verified & Complete. Hard Resetting...")
                    _uiState.update { it.copy(flashProgress = 1f, flashStatus = "SUCCESS") }
                    loader.hardReset(HardReset(flowControl = flowControl))

                } catch (e: Exception) {
                    logToUi(TAG_ERR, "Flash Failure: ${e.message}", isError = true)
                    _uiState.update { it.copy(flashStatus = "FAILED") }
                } finally {
                    try { transport?.close() } catch (e: Exception) {}
                    withContext(Dispatchers.Main) { _uiState.update { it.copy(isFlashing = false) } }
                }
            }
        }
    }
    
    private fun getFlashSizeFromBytes(bytes: Long): FlashSize {
        return when {
            bytes >= 128L * 1024 * 1024 -> FlashSize.S128MB
            bytes >= 64L * 1024 * 1024 -> FlashSize.S64MB
            bytes >= 32L * 1024 * 1024 -> FlashSize.S32MB
            bytes >= 16L * 1024 * 1024 -> FlashSize.S16MB
            bytes >= 8L * 1024 * 1024 -> FlashSize.S8MB
            bytes >= 4L * 1024 * 1024 -> FlashSize.S4MB
            bytes >= 2L * 1024 * 1024 -> FlashSize.S2MB
            bytes >= 1L * 1024 * 1024 -> FlashSize.S1MB
            else -> FlashSize.S4MB
        }
    }

    fun getDeviceByIndex(index: Int): DeviceInfo? {
        return _uiState.value.devices.getOrNull(index)
    }
}

object NoopLogger : EspLogger
