package io.github.ajsb85.esptoolkt.jvm

import com.fazecast.jSerialComm.SerialPort

/** Convenience helpers for enumerating serial ports on the host. */
object SerialPorts {
    /** System names of all detected serial ports, e.g. `/dev/ttyUSB0`. */
    fun list(): List<String> = SerialPort.getCommPorts().map { it.systemPortName }

    /** Human-readable `name — description` lines for the `--list-ports` style output. */
    fun describe(): List<String> = SerialPort.getCommPorts().map { "${it.systemPortName} — ${it.portDescription}" }
}
