# Changelog

All notable changes to this project are documented here. The format follows
[Conventional Commits](https://www.conventionalcommits.org/); releases are generated with
`tbdflow changelog`.

## [Unreleased]

### Fixed
- Bootloader header-patch offset is now `0x2000` for ESP32-C5/C61/P4 (was `0x0`), so `write_flash`
  patches the correct image on these newer chips.

### Validated (ESP32-C5)
- End-to-end on an **ESP32-C5** (Seeed XIAO) over native **USB-Serial-JTAG** (`303a:1001`):
  detect → C5 stub → deflate `write_flash` + MD5 verify of bootloader (0x2000) + partitions + a
  custom IDF app → reboot → captured the app's `ESPTOOLKT_C5_CUSTOM:n:OK` output.

### Added (Android)
- New `:android` library module producing the **`.aar`**: `UsbSerialTransport` +
  `UsbSerial` helpers over `usb-serial-for-android`, reusing `core` unchanged.
- New `:sample-app` reference Android app that enumerates the USB-serial bridge, requests
  USB permission, flashes bundled firmware via the `.aar`, and streams the boot log.
- **Validated on Android** (Xiaomi, Android 12): flashed an ESP32-S2 over USB-OTG
  (stub @460800, deflate + MD5 verify for bootloader/partition/app) and captured
  `ESP32_MSG_START:0:ESP32_MSG_END` from the on-device monitor after reboot.

### Tooling, tests & docs
- Added Kover coverage with a **≥90% gate on `core`** (currently ~93%); introduced a
  `MockEspChip` transport that emulates the ESP32-S2 ROM + stub so the protocol engine,
  flasher, facade, and eFuse controller are unit-tested without hardware.
- Added Spotless + ktlint formatting/lint and enabled `allWarningsAsErrors` on **every** module,
  including the Android `android`/`sample-app` modules.
- Set `org.gradle.jvmargs` (larger heap/metaspace) for reliable Android dexing.
- Added Dokka v2 API documentation with per-package `Module.md` docs and GitHub source links
  (`./gradlew dokkaGenerate`).
- `Flasher.writeFlash` now sends FLASH(/DEFL)_END after writing (matches esptool; no-op on ROM).

### Added (security)
- **core/secure**: Secure Boot V2 (RSA-3072) — `generateSigningKey`, `sign`, `verify`,
  and flash-encryption key generation. Cross-verified both directions against
  Espressif's `espsecure.py`.
- **core/efuse**: ESP32-S2 eFuse `summary` (read) and a safeguarded BLOCK0 burn engine —
  dry-run plans, a mandatory `BurnConfirmation` token / `--confirm BURN`, write-protect and
  one-way-bit checks, and post-burn read-back verification.
- **cli**: `generate_signing_key`, `sign_data`, `verify_signature`,
  `generate_flash_encryption_key`, `efuse_summary`, `burn_efuse`.

### Validated (security)
- esptool-kt signatures verify under `espsecure.py` and vice-versa; `efuse_summary` matches
  `espefuse.py`; burn dry-run and refusal paths confirmed on ESP32-S2 (no fuses burned).

### Added
- **core**: `read_mem`/`write_mem`/`dump_mem` memory access; `read_flash_status`/
  `write_flash_status` SPI status-register control; parsed `get_security_info`
  (secure boot, flash encryption, JTAG/USB lockdown, chip revision); `load_ram`
  (load an image into RAM and execute it).
- **core**: `EspImage` full segment + extended-header parser (richer `image_info`);
  host-side `merge_bin` to combine bootloader/partition/app into one binary.
- **core**: high-level `EspTool` facade (connect → stub → flash) for app/AAR embedding.
- **cli**: `get_security_info`, `read_flash_status`, `write_flash_status`, `read_mem`,
  `write_mem`, `dump_mem`, `load_ram`, `merge_bin` subcommands; segment-aware `image_info`.

### Validated
- On ESP32-S2: `read_mem 0x40001000` returns the chip magic `0x000007c6`;
  `get_security_info`, `read_flash_status`, `image_info`, and `merge_bin` confirmed.

## [0.1.0] — 2026-06-02

### Added
- **core**: platform-agnostic ESP serial bootloader protocol engine
  (`EspLoader`) — SLIP framing, command/response, checksum, chip detection
  (GET_SECURITY_INFO + magic register), register read/write.
- **core**: flasher-stub upload with `OHAI` handshake; embedded stubs for the
  ESP8266 → ESP32-{S2,S3,C2,C3,C5,C6,C61,H2,P4} family.
- **core**: `Flasher` — `write_flash` with zlib-deflate compression and per-image
  MD5 verification; `read_flash`, `erase_flash`, `erase_region`, `verify_flash`.
- **core**: bootloader image-header flash-param patching (`--flash-mode/-size/-freq`);
  flash-ID/size detection via SPI peripheral registers; baud-rate switching.
- **core**: reset strategies (classic, USB-JTAG, hard, custom DTR/RTS).
- **jvm-serial**: jSerialComm-backed `SerialTransport` for desktop/Linux.
- **cli**: `esptool-kt` command (Clikt) — `write_flash`, `read_flash`,
  `erase_flash`, `erase_region`, `verify_flash`, `flash_id`, `chip_id`,
  `read_mac`, `image_info`, `run`, `version`.

### Validated
- End-to-end on real ESP32-S2 hardware (CP210x, `/dev/ttyUSB0`): chip/MAC/flash-ID
  match `esptool.py`; compressed `write_flash` + MD5 verify; confirmed application boot.

[0.1.0]: https://github.com/ajsb85/esptool-kt/releases/tag/v0.1.0
