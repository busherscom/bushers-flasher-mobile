# usbserial-kt

A **pure-Kotlin USB-serial driver library for Android**, reimplemented from
[`usb-serial-for-android`](https://github.com/mik3y/usb-serial-for-android) (MIT) and focused on
**robust, stable hardware manipulation for Espressif manufacturing/provisioning lines** — flashing
and talking to ESP8266/ESP32 devices over USB-OTG from a phone or rugged Android terminal.

> Built as a standalone `.aar`. Pairs naturally with
> [esptool-kt](https://github.com/ajsb85/esptool-kt) (provide a `UsbSerialTransport` backed by this
> library), but has no dependency on it.

## Why a Kotlin port

- **No reflection.** Drivers are discovered via explicit `UsbSerialDriverFactory` objects, not
  reflective static-method scanning. This is safe under aggressive R8/ProGuard shrinking — important
  for locked-down enterprise builds (no "keep" rules needed for discovery to work).
- **Deterministic timeouts.** All blocking I/O uses a monotonic clock and disambiguates timeouts
  from device disconnects with a cheap `GET_STATUS` probe — so a flaky cable on the line surfaces as
  a clear error, not silent data loss.
- **Idiomatic, null-safe Kotlin** with `@Throws` annotations for seamless Java interop.
- **Small, auditable surface** — only the drivers that matter for ESP hardware.

## Supported devices

| Driver | Chips | Typical ESP boards |
| --- | --- | --- |
| `Cp21xxSerialDriver` | Silicon Labs CP2102/3/4/5/8/9 | most ESP32 DevKits, many production jigs |
| `Ch34xSerialDriver` | WCH CH340 / CH341 | low-cost ESP boards |
| `CdcAcmSerialDriver` | USB CDC-ACM | ESP32-S2/S3/C3 **native USB** |

FTDI and Prolific are **not yet ported** (see [Roadmap](#roadmap)); porting them untested would be
a liability on a production line.

### Validated on hardware

The `Cp21xxSerialDriver` is validated end-to-end on a Xiaomi phone (Android 12) against an
**ESP32-S2** (CP210x bridge) over USB-OTG: the sample app discovered the driver, opened the port,
read the device serial number, pulsed DTR/RTS to reset the chip, and bulk-read its serial output
(`rst:0x1 (POWERON),boot:0x8 (SPI_FAST_FLASH_BOOT)` …). This exercises interface claiming, endpoint
discovery, the SET_BAUDRATE / SET_LINE_CTL / SET_MHS control transfers, and bulk reads.

## Install

GitHub Packages (Maven), group `io.github.ajsb85.usbserial`:

```kotlin
repositories {
    maven("https://maven.pkg.github.com/ajsb85/usbserial-kt") {
        credentials {
            username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
            password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
        }
    }
}
dependencies {
    implementation("io.github.ajsb85.usbserial:usbserial-kt:0.3.0")
}
```

## Usage

```kotlin
val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
val driver = UsbSerial.availableDrivers(manager).firstOrNull() ?: return
// ... ensure manager.requestPermission(driver.device, pendingIntent) was granted ...

val port = UsbSerial.open(manager, driver, baudRate = 115200) // 8N1
port.setDTR(false)            // ESP IO0 high (normal boot)
port.setRTS(true); Thread.sleep(100); port.setRTS(false)  // pulse EN -> reset

val buf = ByteArray(256)
val n = port.read(buf, timeoutMillis = 500)
port.write("hello".toByteArray(), timeoutMillis = 1000)
port.close()
```

For continuous monitoring, use `SerialInputOutputManager(port, listener).apply { start() }`.

## API overview

- `UsbSerialPort` — open/close, `read`/`write` (timeout in ms, `0` = block), `setParameters`,
  `setDTR`/`setRTS`, `getControlLines`, `purgeHwBuffers`, `setBreak`.
- `UsbSerialDriver` / `UsbSerialDriverFactory` — device → driver, factory-based discovery.
- `UsbSerialProber` / `ProbeTable` — `getDefaultProber().findAllDrivers(manager)`.
- `UsbSerial` — `availableDrivers` + `open` convenience.
- `SerialInputOutputManager` — background read/write with a `Listener`.
- `EspressifUsb` — recognize Espressif native-USB devices (VID `0x303A`) and their mode via
  `describe(device)` / `identify(pid)`: USB-Serial-JTAG (`0x1001`), S2 OTG ROM (`0x0002`), P4 HS ROM
  (`0x1007`), TinyUSB CDC/MSC/HID (`0x0010`–`0x0013`), devkit bootloaders (`0x7xxx`), community
  runtimes (`0x8xxx`) — plus whether the mode exposes a serial interface. The default prober matches
  the serial PIDs explicitly (covering all ~900 registry entries by VID + structural probe).

## Build

```bash
./gradlew :usbserial:assembleRelease   # -> usbserial/build/outputs/aar/usbserial-release.aar
./gradlew :usbserial:testDebugUnitTest # hardware-free unit tests
./gradlew :sample:installDebug         # reference app: open a bridge, reset, monitor serial
```

Requires the Android SDK and a JDK 17–21 to launch Gradle 8.14 (AGP 8.7.3, Kotlin 2.3.21,
compileSdk 35, minSdk 21). Set the SDK path in `local.properties` (`sdk.dir=...`).

## Sample app

[`sample/`](sample) is a runnable reference: it enumerates the attached bridge, requests USB
permission, opens the port, pulses DTR/RTS to reset the chip, and streams the serial output
(mirrored to logcat tag `USBSERIALKT`).

```bash
./gradlew :sample:installDebug
adb shell am start -n io.github.ajsb85.usbserial.sample/.MainActivity
adb logcat -s USBSERIALKT
```

## Development & quality

| Concern | Tool | Command |
| --- | --- | --- |
| Formatting / lint | Spotless + ktlint | `./gradlew spotlessApply` / `spotlessCheck` |
| Warnings | `allWarningsAsErrors` | enforced by the build |
| Test coverage | Kover (≥90% gate) | `./gradlew :usbserial:koverHtmlReport` |
| API docs | Dokka v2 | `./gradlew dokkaGenerate` → `build/dokka/html` |

The library is unit-tested to **>90%** line coverage (currently ~93%) using **mockk** to stand in
for the Android USB host objects (`UsbManager`/`UsbDevice`/`UsbInterface`/`UsbEndpoint`/
`UsbDeviceConnection`/`UsbRequest`), so the driver control-transfer sequences, parameter encoding,
control-line decoding, and the read/write timeout logic are exercised without hardware. The CP210x
driver is additionally validated on a real ESP32-S2 (see [above](#validated-on-hardware)).

## Roadmap

- FTDI (FT232/FT231X) and Prolific PL2303 drivers (need on-hardware validation before shipping).
- Multi-buffered async read queue (`UsbRequest`) for sustained high-baud capture.
- Coroutine-friendly `Flow<ByteArray>` read API.

## Credits & License

Derived from **usb-serial-for-android** by Google Inc. and Mike Wakerly, used under the **MIT
License**. This Kotlin port is also **MIT**-licensed; original copyright notices are retained in
[LICENSE](LICENSE) and [NOTICE](NOTICE).
