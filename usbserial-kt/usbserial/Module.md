# Module usbserial

Pure-Kotlin USB-serial driver library for Android, focused on robust hardware manipulation for
Espressif manufacturing/provisioning lines. Start at [io.github.ajsb85.usbserial.UsbSerial] to
discover and open a port, then drive it through [io.github.ajsb85.usbserial.UsbSerialPort].

# Package io.github.ajsb85.usbserial

Public API: the [UsbSerialPort] interface, factory-based discovery
([UsbSerialProber]/[ProbeTable]/[UsbSerialDriverFactory]), the [CommonUsbSerialPort] base, the
[UsbSerial] convenience facade, USB id constants ([UsbId]), and the Espressif native-USB registry
([EspressifUsb]).

# Package io.github.ajsb85.usbserial.driver

Concrete drivers: [io.github.ajsb85.usbserial.driver.Cp21xxSerialDriver] (Silicon Labs),
[io.github.ajsb85.usbserial.driver.Ch34xSerialDriver] (WCH), and
[io.github.ajsb85.usbserial.driver.CdcAcmSerialDriver] (USB CDC-ACM / ESP native USB).

# Package io.github.ajsb85.usbserial.util

Helpers: [io.github.ajsb85.usbserial.util.SerialInputOutputManager] (background read/write),
[io.github.ajsb85.usbserial.util.MonotonicClock], and USB descriptor parsing.
