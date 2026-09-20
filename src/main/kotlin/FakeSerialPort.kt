package de.serup

import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import kotlin.random.Random

/**
 * A software-only stand-in for a serial port, listed in the port selector only in [DevMode] so the
 * graph can be exercised without real hardware. A background thread emits as if a real device were streaming it over the wire -
 * and [inputStream] can be read exactly how a real port's `SerialPort.getInputStream()` would be.
 */
object FakeSerialPort {
    const val DISPLAY_NAME = "Fake Port (dev)"

    private const val EMIT_INTERVAL_MILLIS = 200L
    private const val EMIT_MIN_VALUE = 0.0
    private const val EMIT_MAX_VALUE = 100.0

    private val outputStream = PipedOutputStream()
    val inputStream: InputStream = PipedInputStream(outputStream)

    init {
        Thread({
            while (true) {
                val plot_1_x = Random.nextDouble(EMIT_MIN_VALUE, EMIT_MAX_VALUE)
                val plot_1_y = Random.nextDouble(EMIT_MIN_VALUE, EMIT_MAX_VALUE)
                val plot_1_z = Random.nextDouble(EMIT_MIN_VALUE, EMIT_MAX_VALUE)
                outputStream.write("[plot 1] %.2f | %.2f | %.2f\n".format(plot_1_x, plot_1_y, plot_1_z).toByteArray())

                outputStream.flush()
                Thread.sleep(EMIT_INTERVAL_MILLIS)

                val plot_2_x = Random.nextDouble(EMIT_MIN_VALUE, EMIT_MAX_VALUE)
                val plot_2_y = Random.nextDouble(EMIT_MIN_VALUE, EMIT_MAX_VALUE)
                val plot_2_z = Random.nextDouble(EMIT_MIN_VALUE, EMIT_MAX_VALUE)
                outputStream.write("[plot 2] x:%.2f | y: %.2f | z: %.2f\n".format(plot_2_x, plot_2_y, plot_2_z).toByteArray())

                val plot_3_scalar = Random.nextDouble(EMIT_MIN_VALUE, EMIT_MAX_VALUE)
                outputStream.write("[plot 3] %.2f\n".format(plot_3_scalar).toByteArray())

                outputStream.flush()
                Thread.sleep(EMIT_INTERVAL_MILLIS)
            }
        }, "FakeSerialPort-emitter").apply {
            isDaemon = true
            start()
        }
    }
}
