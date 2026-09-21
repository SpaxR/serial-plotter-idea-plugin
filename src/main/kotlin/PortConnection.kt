package de.serup

import com.fazecast.jSerialComm.SerialPort
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

private const val READ_TIMEOUT_MILLIS = 1000
private const val RECONNECT_DELAY_MILLIS = 1000L

/**
 * Reads lines from [portName] on a background thread and forwards each to [onLine], until [close]d.
 * [portName] is opened as a real port via jSerialComm at [baudRate], unless it is
 * [FakeSerialPort.DISPLAY_NAME].
 *
 * The port is opened in semi-blocking mode with a read timeout, rather than jSerialComm's fully
 * non-blocking default: with the default, a read that finds no bytes yet available throws a
 * SerialPortTimeoutException instead of just returning 0, which used to kill the reader thread the
 * moment the device paused between chunks - in practice, after just one chunk. Semi-blocking mode
 * together with getInputStreamWithSuppressedTimeoutExceptions() turns that into an ordinary zero-byte
 * read instead, so a read never holds the port past [READ_TIMEOUT_MILLIS] and [close] stays
 * responsive. If reading still fails - the device was unplugged, the port errored out - the port is
 * closed and reopened until it succeeds again or [close] is called.
 */
class PortConnection(private val portName: String, private val baudRate: Int, private val onLine: (String) -> Unit) {
    @Volatile
    private var closed = false

    // Releases whichever resource openReader() last opened - the real SerialPort, or the fake port's
    // input stream - so close() can unblock a read that's currently blocked waiting for more bytes.
    @Volatile
    private var closer: (() -> Unit)? = null

    init {
        Thread({ readLoop() }, "PortConnection-reader").apply {
            isDaemon = true
            start()
        }
    }

    private fun readLoop() {
        while (!closed) {
            try {
                val reader = openReader()
                while (!closed) {
                    val line = reader.readLine() ?: break
                    onLine(line)
                }
            } catch (_: Exception) {
                // The port failed to open, failed while reading, or was closed mid-read; retry below.
            } finally {
                closer?.invoke()
                closer = null
            }

            if (!closed) Thread.sleep(RECONNECT_DELAY_MILLIS)
        }
    }

    private fun openReader(): BufferedReader {
        if (portName == FakeSerialPort.DISPLAY_NAME) {
            val input = FakeSerialPort.openInputStream()
            closer = { input.close() }
            return BufferedReader(InputStreamReader(input))
        }

        val port = SerialPort.getCommPort(portName)
        port.baudRate = baudRate
        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, READ_TIMEOUT_MILLIS, 0)
        if (!port.openPort()) throw IOException("Could not open port $portName")
        closer = { port.closePort() }
        return BufferedReader(InputStreamReader(port.getInputStreamWithSuppressedTimeoutExceptions()))
    }

    fun close() {
        closed = true
        closer?.invoke()
    }
}
