# Changelog

Format follows [Conventional Commits](https://www.conventionalcommits.org/).

## [0.3.0] — 2026-06-02

### Added
- CH34x `setBreak` support (BREAK condition control), matching the other drivers.
- Quality tooling: Kover coverage with a **≥90% gate** (currently ~93%), Spotless+ktlint
  formatting, and Dokka v2 API docs with per-package `Module.md`.
- mockk-based unit-test suite covering the drivers, `CommonUsbSerialPort`, prober, `UsbSerial`,
  `SerialInputOutputManager`, and utilities without hardware (shared mock builders in `TestSupport`).

### Docs
- README gains Sample app + Development & quality sections; CONTRIBUTING documents the
  test/coverage/lint/docs gates.

## [0.2.0] — 2026-06-02

### Added
- `EspressifUsb` registry: identify Espressif native-USB devices (VID `0x303A`) and their mode
  (USB-Serial-JTAG, S2 OTG ROM, P4 HS ROM, TinyUSB CDC/MSC/HID, devkit `0x7xxx`, community `0x8xxx`),
  with `isEspressif`/`identify`/`describe` and an `exposesSerial` flag. Derived from the official
  espressif/usb-pids registry.
- The default prober now matches Espressif serial PIDs (`0x0002`, `0x0010`, `0x0011`, `0x1001`,
  `0x1007`) explicitly via the CDC-ACM factory, in addition to structural CDC probing.

## [0.1.0] — 2026-06-02

### Added
- Pure-Kotlin Android USB-serial library (`.aar`), derived from usb-serial-for-android (MIT).
- Core API: `UsbSerialPort`, `UsbSerialDriver`, factory-based `UsbSerialProber`/`ProbeTable`
  (no reflection), `CommonUsbSerialPort` base with monotonic-clock timeouts and connection probing.
- Drivers: `Cp21xxSerialDriver` (Silicon Labs CP210x), `Ch34xSerialDriver` (WCH CH340/341),
  `CdcAcmSerialDriver` (USB CDC-ACM / ESP32-S2/S3/C3 native USB).
- `SerialInputOutputManager` for background read/write; `UsbSerial` open/discover helpers.
- Hardware-free unit tests for the driver registry and helpers.

### Validated
- `Cp21xxSerialDriver` validated on Android 12 against an ESP32-S2 over USB-OTG: open, read serial
  number, DTR/RTS reset, and bulk-read of the device boot log.

[0.1.0]: https://github.com/ajsb85/usbserial-kt/releases/tag/v0.1.0
