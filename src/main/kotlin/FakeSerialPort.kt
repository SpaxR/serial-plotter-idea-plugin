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

    // How far a value can drift on a single step, as a fraction of its current value.
    private const val STEP_FRACTION = 0.10

    private val outputStream = PipedOutputStream()
    val inputStream: InputStream = PipedInputStream(outputStream)

    private val plot1X = RandomWalkValue()
    private val plot1Y = RandomWalkValue()
    private val plot1Z = RandomWalkValue()
    private val plot2X = RandomWalkValue()
    private val plot2Y = RandomWalkValue()
    private val plot2Z = RandomWalkValue()
    private val plot3Scalar = RandomWalkValue()

    init {
        Thread({
            while (true) {
                outputStream.write(
                    "[plot 1] %.2f | %.2f | %.2f\n".format(plot1X.next(), plot1Y.next(), plot1Z.next()).toByteArray()
                )
                outputStream.flush()
                Thread.sleep(EMIT_INTERVAL_MILLIS)

                outputStream.write(
                    "[plot 2] x:%.2f | y: %.2f | z: %.2f\n".format(plot2X.next(), plot2Y.next(), plot2Z.next()).toByteArray()
                )
                outputStream.write("[plot 3] %.2f\n".format(plot3Scalar.next()).toByteArray())

                outputStream.flush()
                Thread.sleep(EMIT_INTERVAL_MILLIS)
            }
        }, "FakeSerialPort-emitter").apply {
            isDaemon = true
            start()
        }
    }

    /**
     * A value that starts out random and then wanders - each [next] nudges it by a random fraction
     * (up to [STEP_FRACTION]) of its current value, instead of jumping to a whole new random value.
     */
    private class RandomWalkValue(private var value: Double = Random.nextDouble(0.0, 100.0)) {
        fun next(): Double {
            value += value * Random.nextDouble(-STEP_FRACTION, STEP_FRACTION)
            return value
        }
    }
}
