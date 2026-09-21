package de.serup

/**
 * One line's values over a fixed trailing time window, used to render a single scrolling series. A
 * [Point] with a `null` [Point.value] is a gap marker rather than real data - see [addGap].
 */
class TimeSeries {
    data class Point(val timestampMs: Long, val value: Double?)

    private val points = ArrayDeque<Point>()

    @Synchronized
    fun add(timestampMs: Long, value: Double) = addPoint(Point(timestampMs, value))

    /**
     * Marks a break at [timestampMs] - e.g. the connection was stopped - with no value of its own, so
     * rendering can leave a visible gap instead of drawing a misleading line straight across however
     * long the stream was actually down for.
     */
    @Synchronized
    fun addGap(timestampMs: Long) = addPoint(Point(timestampMs, null))

    private fun addPoint(point: Point) {
        points.addLast(point)
        while (points.isNotEmpty() && points.first().timestampMs < point.timestampMs - WINDOW_MS) {
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
