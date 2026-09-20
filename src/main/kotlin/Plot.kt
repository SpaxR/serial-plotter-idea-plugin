package de.serup

/**
 * A single configured plot's live state: its configuration UI plus the time-series data routed
 * into it from the raw stream. One [TimeSeries] per value position on the line (x, y, z, ...),
 * created lazily as new positions show up.
 */
class Plot(var title: String, val config: PlotConfigPanel) {
    private val series = mutableListOf<TimeSeries>()

    /** [values] holds a null for any position that failed to parse, so positions stay aligned. */
    @Synchronized
    fun addSample(timestampMs: Long, values: List<Double?>) {
        while (series.size < values.size) series.add(TimeSeries())
        values.forEachIndexed { index, value ->
            if (value != null) series[index].add(timestampMs, value)
        }
    }

    @Synchronized
    fun snapshotSeries(): List<List<TimeSeries.Point>> = series.map { it.snapshot() }
}
