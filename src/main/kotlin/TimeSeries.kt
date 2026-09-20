package de.serup

/** One line's values over a fixed trailing time window, used to render a single scrolling series. */
class TimeSeries {
    data class Point(val timestampMs: Long, val value: Double)

    private val points = ArrayDeque<Point>()

    @Synchronized
    fun add(timestampMs: Long, value: Double) {
        points.addLast(Point(timestampMs, value))
        while (points.isNotEmpty() && points.first().timestampMs < timestampMs - WINDOW_MS) {
            points.removeFirst()
        }
    }

    /** A defensive copy, safe to read from the EDT while [add] runs concurrently on the reader thread. */
    @Synchronized
    fun snapshot(): List<Point> = points.toList()

    companion object {
        const val WINDOW_MS = 30_000L
    }
}
