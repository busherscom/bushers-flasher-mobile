# Contributing to usb-serial-for-android-kt

Thanks for helping! This project targets **robust hardware manipulation for production lines**, so
correctness and stability outrank feature breadth.

## Workflow

- Trunk-based development on `main`; short-lived branches; **Conventional Commits**
  (`feat`, `fix`, `docs`, `refactor`, `test`, `build`, `chore`).
- Before pushing, keep all of these green:
  - `./gradlew :usbserial:assembleRelease` — builds (warnings are errors via `allWarningsAsErrors`)
  - `./gradlew :usbserial:testDebugUnitTest` — unit tests
  - `./gradlew koverVerify` — **≥90% line coverage** gate on the library
  - `./gradlew spotlessCheck` — ktlint formatting (`spotlessApply` to fix)
  - `./gradlew dokkaGenerate` — API docs build

## Testing without hardware

Driver logic is tested with **mockk** standing in for the Android USB objects (`UsbManager`,
`UsbDevice`, `UsbInterface`, `UsbEndpoint`, `UsbDeviceConnection`, `UsbRequest`). See
`usbserial/src/test/.../TestSupport.kt` for the shared mock builders — reuse them when adding a
driver so its control-transfer sequence is covered without a physical device. Hardware-dependent
behavior should also be smoke-tested with the [`sample`](sample) app and noted in the PR.

## Adding a driver

New drivers (e.g. FTDI, Prolific) must:
1. Extend `CommonUsbSerialPort` and faithfully implement the chip's control transfers.
2. Provide a `UsbSerialDriverFactory` (`object Factory`) and register it in
   `UsbSerialProber.defaultProbeTable()`.
3. **Be validated on real hardware** — note the chip, board, and what you tested in the PR. We do
   not ship serial drivers that haven't been exercised against a physical device.

## Style

Kotlin official style; null-safe; `@Throws` on public throwing methods for Java interop; KDoc on
public types. Avoid reflection (keeps R8/ProGuard behavior predictable for enterprise consumers).
