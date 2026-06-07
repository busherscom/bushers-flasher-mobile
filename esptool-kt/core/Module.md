# Module core

The platform-agnostic heart of **esptool-kt**: a pure-Kotlin implementation of the Espressif
serial bootloader protocol. It depends only on the Kotlin stdlib, `kotlinx-serialization-json`,
and JDK APIs available on Android (`java.util.zip`, `java.security`), so the same artifact powers
the desktop CLI today and the Android `.aar` in phase 2.

Start at [io.github.ajsb85.esptoolkt.EspTool] for the high-level facade, or
[io.github.ajsb85.esptoolkt.EspLoader] for direct protocol control.

# Package io.github.ajsb85.esptoolkt

Top-level API: the [EspLoader] protocol engine, the batteries-included [EspTool] facade,
[SecurityInfo] reporting, and the [EspLogger] progress sink.

# Package io.github.ajsb85.esptoolkt.transport

The [SerialTransport] abstraction (the single platform seam) and the DTR/RTS
[ResetStrategy] implementations (classic, USB-JTAG, hard, custom).

# Package io.github.ajsb85.esptoolkt.protocol

The wire layer: SLIP framing, command/response encoding, checksums, little-endian helpers,
and the typed exception hierarchy.

# Package io.github.ajsb85.esptoolkt.targets

The supported-chip registry (registers, efuse base, magic values) and chip detection.

# Package io.github.ajsb85.esptoolkt.stub

Loading and decoding the bundled flasher stubs uploaded into chip RAM for fast flashing.

# Package io.github.ajsb85.esptoolkt.image

Firmware-image helpers: header flash-param patching, full segment parsing, flash mode/size/freq
settings, and host-side `merge_bin`.

# Package io.github.ajsb85.esptoolkt.flasher

High-level flashing orchestration (`write_flash`/`verify_flash`) with compression and MD5
verification.

# Package io.github.ajsb85.esptoolkt.secure

Host-side Secure Boot V2 (RSA-3072) signing/verification and flash-encryption key generation,
interoperable with Espressif's `espsecure.py`.

# Package io.github.ajsb85.esptoolkt.efuse

ESP32-S2 eFuse reading and a safeguarded, confirmation-gated BLOCK0 burn engine.
**eFuse burns are permanent and irreversible.**
