# Module jvm-serial

Desktop/Linux serial backend for esptool-kt. Provides a
[io.github.ajsb85.esptoolkt.jvm.JSerialCommTransport] implementation of
`SerialTransport` (backed by jSerialComm) plus port-enumeration helpers, so the
platform-agnostic `core` can talk to a real ESP chip over a USB-serial bridge.

# Package io.github.ajsb85.esptoolkt.jvm

The jSerialComm-backed transport and `SerialPorts` discovery utilities.
