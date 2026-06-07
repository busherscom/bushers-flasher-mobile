# esptool-kt

A **pure-Kotlin reimplementation of Espressif's [`esptool.py`](https://github.com/espressif/esptool)** serial
bootloader protocol — built to run on the **JVM/Linux** today and to be consumed by
**enterprise Android apps as an `.aar`** (phase 2).

It speaks the ESP ROM/stub serial protocol directly (SLIP framing, command/response,
flasher-stub upload, zlib-compressed flashing, MD5 verification, chip detection) with **no
native dependencies** in the core — only a thin, swappable serial transport per platform.

> **Status:** v0.1.0 — core flashing path complete and **validated on real ESP32-S2 hardware**
> (connect, stub, baud change, compressed `write_flash`, MD5 verify, boot confirmed). See
> [Validation](#validation).

---

## Why

`esptool.py` is the de-facto tool for flashing Espressif chips, but it is Python. Mobile and
JVM products that need to field-update ESP firmware can't ship a Python runtime. `esptool-kt`
provides the same protocol in idiomatic Kotlin so the **exact same core** drives a Linux CLI
and an Android library.

## Features

- Connect + auto-reset into the bootloader (classic / USB-JTAG / custom DTR-RTS sequences)
- Chip detection (GET_SECURITY_INFO + magic-register) — ESP8266 → ESP32-{S2,S3,C2,C3,C5,C6,C61,H2,P4}
- Flasher-stub upload (`OHAI` handshake) for fast, compressed flashing; baud-rate switching
- **Flash**: `write_flash` (zlib deflate + per-image MD5 verify), `read_flash`, `erase_flash`,
  `erase_region`, `verify_flash`
- **Diagnostics**: `flash_id`, `chip_id`, `read_mac`, `get_security_info` (secure boot / flash
  encryption / JTAG-USB lockdown / revision), `read_flash_status`, `write_flash_status`
- **Memory & RAM**: `read_mem`, `write_mem`, `dump_mem`, `load_ram` (load + execute in RAM)
- **Image tools (no device)**: `image_info` (full segment + extended-header parse), `merge_bin`
  (combine bootloader + partition table + app into one asset — ideal for Android), header
  flash-param patching (`--flash-mode/-size/-freq`)
- **High-level [`EspTool`](core/src/main/kotlin/io/github/ajsb85/esptoolkt/EspTool.kt) facade**
  for embedding in apps (connect → stub → flash in a few lines)
- **Secure Boot V2 (`espsecure`)**: RSA-3072 `generate_signing_key`, `sign_data`,
  `verify_signature`, `generate_flash_encryption_key` — host-side, **interoperable with
  Espressif's `espsecure.py`** (cross-verified both directions)
- **eFuse (`espefuse`)**: `efuse_summary` (read), and a **safeguarded** `burn_efuse` for
  ESP32-S2 BLOCK0 — dry-run by default, refuses without `--confirm BURN`, read-back verified

Planned follow-ups: `elf2image`, UF2 output, AES-XTS flash-data encryption, eFuse key/data
blocks (Reed-Solomon coded), RFC2217 server.

> ⚠️ **eFuse burns are permanent and irreversible.** `burn_efuse` never burns by accident: it
> prints a dry run and only programs when given the exact `--confirm BURN` token; the library API
> requires a `BurnConfirmation` token. A wrong burn can disable JTAG/USB/download or enable secure
> boot and lock you out. Only BLOCK0 fields are supported; key/data blocks are intentionally
> non-burnable until Reed-Solomon coding lands.

## Architecture

Multi-module Gradle build. The **core is platform-agnostic** (uses only `java.util.zip`,
`java.security.MessageDigest`, and `kotlinx-serialization-json`, all available on Android), so
the Android module reuses it **unchanged** — the only Android-specific code is a serial transport.

```
esptool-kt/
├── core/        Protocol engine — SLIP, commands, EspLoader, chip targets,
│                stub loader, image patching, Flasher, Secure Boot V2, eFuse.
│                Defines the SerialTransport seam + reset strategies.
├── jvm-serial/  Desktop/Linux SerialTransport via jSerialComm.
├── cli/         The `esptool-kt` command-line tool (Clikt).
├── android/     The .aar: UsbSerialTransport over usb-serial-for-android.
└── sample-app/  Android app that flashes an ESP32-S2 over USB-OTG (reference).
```

Key types: `EspLoader` (the engine, ≈ `esptool.py`'s `ESPLoader`), `Flasher` (high-level
`write_flash`/`verify_flash`), `Slip`/`Protocol` (framing), `Chips` (target registry),
`FlasherStub` (embedded stub blobs), `SerialTransport`/`ResetStrategy` (the platform seam).

### Protocol notes

Distilled from `esptool.py`, `esptool-js`, and `esp-serial-flasher`:

- **SLIP**: `0xC0` delimiter; `0xDB 0xDC`→`0xC0`, `0xDB 0xDD`→`0xDB`.
- **Command** header (LE): `dir=0x00 | op | size(2) | checksum(4)` + payload.
- **Response**: `dir=0x01 | op | size(2) | value(4)` + data + `status(2)`.
- Data checksum = `0xEF` XOR-folded over the payload; `SYNC` returns 8 replies.
- **Stub**: upload via `MEM_BEGIN/DATA/END` (0x1800 blocks), then chip emits `OHAI`.
- **Flash**: `FLASH_DEFL_*` (zlib), block 0x4000 (stub) / 0x400 (ROM), `SPI_FLASH_MD5` verify.

## Requirements

- JDK 17+ to compile (toolchain), **JDK ≤21 to *run* Gradle 8.14** (Gradle 8.14 does not yet
  support launching on JDK 26). Point Gradle at a supported JDK, e.g.
  `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew ...` or set `org.gradle.java.home`
  in `~/.gradle/gradle.properties`.
- A serial port to your ESP chip (e.g. `/dev/ttyUSB0`). On Linux add yourself to the `dialout`
  group, or `sudo chmod 666 /dev/ttyUSB0`. For a flashing/manufacturing host, install
  [`tools/udev/99-espressif-usb.rules`](tools/udev) — one ruleset that grants access to every
  Espressif native-USB board (VID `0x303A`, all ~900 PIDs) and the CP210x/CH34x/FTDI/Prolific
  bridges, and tells ModemManager to leave them alone.

## Build

```bash
./gradlew build              # compile + test + lint check + coverage gate (>=90%)
./gradlew :cli:installDist   # produce the runnable CLI
./cli/build/install/esptool-kt/bin/esptool-kt --help
```

> Gradle 8.14 must be launched on JDK 17–21 (your default `java` may be newer); set
> `org.gradle.java.home` in `~/.gradle/gradle.properties` or prefix commands with `JAVA_HOME=...`.

## Development & quality

| Concern | Tool | Command |
| --- | --- | --- |
| Formatting / lint | Spotless + ktlint | `./gradlew spotlessApply` / `spotlessCheck` |
| Warnings | `allWarningsAsErrors` | enforced by `build` |
| Test coverage | Kover (≥90% gate on `core`) | `./gradlew koverHtmlReport` (report in `core/build/reports/kover`) |
| API docs | Dokka v2 | `./gradlew dokkaGenerate` (HTML in `build/dokka/html`) |

The `core` module is covered to **>90%** by unit tests, including a `MockEspChip`
[`SerialTransport`](core/src/main/kotlin/io/github/ajsb85/esptoolkt/transport/SerialTransport.kt)
that emulates the ESP32-S2 ROM + stub protocol (SYNC, register I/O, stub upload, compressed
flashing with real MD5, the SPI flash-ID dance, and the eFuse program/read cycle) so the protocol
engine is exercised end-to-end without hardware. Hardware/CLI I/O wrappers are validated against
real silicon (see [Validation](#validation)) and excluded from the coverage gate.

API documentation is generated with Dokka and is organized per package (see the `Module.md`
files in each module). Build it with `./gradlew dokkaGenerate` and open `build/dokka/html/index.html`.

## Usage

```bash
ESPTOOL=./cli/build/install/esptool-kt/bin/esptool-kt

# Identify the chip
$ESPTOOL --port /dev/ttyUSB0 chip_id
$ESPTOOL --port /dev/ttyUSB0 flash_id

# Flash a full IDF build (bootloader + partition table + app)
$ESPTOOL --port /dev/ttyUSB0 --baud 460800 \
  write_flash --flash-mode dio --flash-size 2MB --flash-freq 80m \
  0x1000  build/bootloader/bootloader.bin \
  0x8000  build/partition_table/partition-table.bin \
  0x10000 build/hello_world.bin

# Read / erase / verify
$ESPTOOL --port /dev/ttyUSB0 read_flash 0x0 0x100000 dump.bin
$ESPTOOL --port /dev/ttyUSB0 erase_flash
$ESPTOOL --port /dev/ttyUSB0 verify_flash 0x10000 build/hello_world.bin

# Inspect a local image (no device) — lists segments + extended header
$ESPTOOL image_info build/hello_world.bin

# Merge bootloader + partition table + app into a single asset (no device)
$ESPTOOL merge_bin -o merged.bin --flash-mode dio --flash-size 2MB --flash-freq 80m \
  0x1000 build/bootloader/bootloader.bin \
  0x8000 build/partition_table/partition-table.bin \
  0x10000 build/hello_world.bin

# Diagnostics
$ESPTOOL --port /dev/ttyUSB0 get_security_info
$ESPTOOL --port /dev/ttyUSB0 read_flash_status
$ESPTOOL --port /dev/ttyUSB0 read_mem 0x40001000   # chip magic register
$ESPTOOL --port /dev/ttyUSB0 dump_mem 0x3ff00000 0x100 dump.bin
$ESPTOOL --port /dev/ttyUSB0 --no-stub load_ram app-in-ram.bin

# Secure Boot V2 (host-side; interoperates with espsecure.py)
$ESPTOOL generate_signing_key -o sb_key.pem --pub-keyfile sb_pub.pem
$ESPTOOL sign_data -k sb_key.pem -o app-signed.bin app.bin
$ESPTOOL verify_signature app-signed.bin
$ESPTOOL generate_flash_encryption_key fe_key.bin

# eFuse (ESP32-S2): read summary, and a guarded burn (dry-run unless --confirm BURN)
$ESPTOOL --port /dev/ttyUSB0 efuse_summary
$ESPTOOL --port /dev/ttyUSB0 burn_efuse DIS_USB 1              # DRY RUN
$ESPTOOL --port /dev/ttyUSB0 burn_efuse DIS_USB 1 --confirm BURN   # PERMANENT
```

### Embedding (the `EspTool` facade)

```kotlin
EspTool(transport, logger = myLogger).use { tool ->   // transport: any SerialTransport
    val chip = tool.connect()                          // reset + stub + baud
    tool.writeFlash(listOf(FlashSegment(0x10000, firmwareBytes)))
    val info = tool.securityInfo()
}                                                      // close() resets into the app
```

Global options: `--port -p`, `--baud -b`, `--chip`, `--before` (`default_reset`/`usb_reset`/
`no_reset`/custom), `--after` (`hard_reset`/`no_reset`), `--no-stub`, `--trace`.

## Validation

Validated end-to-end against an **ESP32-S2** (CP210x bridge, `/dev/ttyUSB0`):

| Check | esptool-kt | esptool.py |
| --- | --- | --- |
| Chip | ESP32-S2 | ESP32-S2 |
| MAC | `7c:df:a1:0e:d9:f8` | `7c:df:a1:0e:d9:f8` |
| Flash manufacturer / size | `20` / 4 MB | `20` / 4 MB |

`write_flash` uploaded the stub, switched to 460800 baud, deflate-compressed all three images,
and reported **"Hash of data verified"** for each. After a hard reset the board boots and emits
the firmware's expected `ESP32_MSG_START:<n>:ESP32_MSG_END` on the serial line.

It was **also validated on Android** (Xiaomi, Android 12) with the same ESP32-S2 over USB-OTG:
the sample app, using the `.aar`, detected the chip (MAC `7c:df:a1:0e:d9:f8`), uploaded the stub
at 460800 baud, deflate-flashed bootloader + partition table + app with MD5 verification, rebooted,
and the on-device serial monitor captured `ESP32_MSG_START:0:ESP32_MSG_END` — proving the chip
booted the firmware flashed from the phone.

## Android (`.aar`)

The `:android` module reuses `core` unchanged; the only Android-specific class is
[`UsbSerialTransport`](android/src/main/kotlin/io/github/ajsb85/esptoolkt/android/UsbSerialTransport.kt)
over [`usb-serial-for-android`](https://github.com/mik3y/usb-serial-for-android). Enterprise
integration is a few lines:

```kotlin
val driver = UsbSerial.availableDrivers(usbManager).first()   // CP210x/CH34x/FTDI/native-USB
// ... ensure usbManager.requestPermission(driver.device, ...) was granted ...
val port = UsbSerial.open(usbManager, driver)
EspTool(UsbSerialTransport(port), logger = myLogger).use { tool ->
    tool.connect()                                            // reset + stub + baud
    tool.writeFlash(listOf(FlashSegment(0x10000, firmwareBytes)))
}                                                             // resets into the app
```

Build the artifacts:

```bash
./gradlew :android:assembleRelease      # -> android/build/outputs/aar/android-release.aar
./gradlew :sample-app:installDebug      # build + install the reference app on a connected phone
```

The [`sample-app/`](sample-app) is a complete reference: it **detects the chip** and picks the
matching reset (classic for CP210x bridges, USB-JTAG for native-USB chips), flash offsets, and
bundled firmware (ESP32-S2 and ESP32-C5 are included), flashes with MD5 verification, then streams
the boot log:

```bash
./gradlew :sample-app:installDebug
adb shell am start -n io.github.ajsb85.esptoolkt.sample/.MainActivity
adb logcat -s ESPTOOLKT
```

Validated on a Xiaomi phone (Android 12): flashed an ESP32-S2 (CP210x) and an ESP32-C5
(native USB-Serial-JTAG, `303a:1001`) over USB-OTG, each booting its firmware.

## Consuming the published artifacts

Releases are published to **GitHub Packages** (Maven). Coordinates (group
`io.github.ajsb85.esptoolkt`):

| Artifact | Use |
| --- | --- |
| `esptool-kt-core` | platform-agnostic protocol engine (JVM + Android) |
| `esptool-kt-jvm-serial` | desktop/Linux jSerialComm transport |
| `esptool-kt-android` | the Android `.aar` (USB-serial transport) |

```kotlin
// settings.gradle.kts (or build) — GitHub Packages needs auth even for reads
repositories {
    maven("https://maven.pkg.github.com/ajsb85/esptool-kt") {
        credentials {
            username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
            password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
        }
    }
    maven("https://jitpack.io") // transitive: usb-serial-for-android
}

dependencies {
    implementation("io.github.ajsb85.esptoolkt:esptool-kt-android:0.1.0") // Android
    // or, for JVM tools:
    // implementation("io.github.ajsb85.esptoolkt:esptool-kt-core:0.1.0")
    // implementation("io.github.ajsb85.esptoolkt:esptool-kt-jvm-serial:0.1.0")
}
```

Maintainers publish with `GITHUB_ACTOR=<user> GITHUB_TOKEN=<token-with-write:packages> ./gradlew publish`.

## Credits & License

Protocol behavior is derived from Espressif's `esptool`, `esptool-js`, and `esp-serial-flasher`.
Licensed under **Apache-2.0** (see [LICENSE](LICENSE)), matching upstream esptool.
