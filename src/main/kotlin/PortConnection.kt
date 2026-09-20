package de.serup

import com.fazecast.jSerialComm.SerialPort
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Reads lines from [portName] on a background thread and forwards each to [onLine], until [close]d.
 * [portName] is opened as a real port via jSerialComm, unless it is [FakeSerialPort.DISPLAY_NAME].
 */
class PortConnection(portName: String, private val onLine: (String) -> Unit) {
    private val serialPort: SerialPort? =
        if (portName == FakeSerialPort.DISPLAY_NAME) {
            null
        } else {
            SerialPort.getCommPort(portName).apply { openPort() }
        }

    private val reader = BufferedReader(InputStreamReader(serialPort?.inputStream ?: FakeSerialPort.inputStream))

    @Volatile
    private var closed = false

    init {
        Thread({
            try {
                while (!closed) {
                    val line = reader.readLine() ?: break
                    onLine(line)
                }
            } catch (_: Exception) {
                // The port was closed while a read was in progress.
            }
        }, "PortConnection-reader").apply {
            isDaemon = true
            start()
        }
    }

    fun close() {
        closed = true
        serialPort?.closePort()
    }
}
