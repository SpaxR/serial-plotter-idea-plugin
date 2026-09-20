package de.serup

/**
 * A single configured plot's live state: its configuration UI plus the time-series data routed
 * into it from the raw stream. One [Series] per value position on the line (x, y, z, ...),
 * created lazily as new positions show up.
 */
class Plot(var title: String, val config: PlotConfigPanel) {
    /** A value parsed from one token, e.g. "x:12.34" -> label "x", value 12.34. */
    data class ParsedValue(val label: String?, val value: Double?)

    class SeriesSnapshot(val label: String?, val points: List<TimeSeries.Point>)

    private class Series {
        var label: String? = null
        val timeSeries = TimeSeries()
    }

    private val series = mutableListOf<Series>()

    /**
     * A null value at some position is skipped (parse failure), so positions stay aligned. A
     * position with no explicit label that has never parsed a value is marked [INVALID_LABEL] -
     * cleared again the moment a real value comes in, so a one-off glitch doesn't permanently
     * mislabel a position that's actually fine.
     */
    @Synchronized
    fun addSample(timestampMs: Long, values: List<ParsedValue>) {
        while (series.size < values.size) series.add(Series())
        values.forEachIndexed { index, parsed ->
            val s = series[index]
            when {
                parsed.label != null -> s.label = parsed.label
                parsed.value != null -> if (s.label == INVALID_LABEL) s.label = null
                s.label == null -> s.label = INVALID_LABEL
            }
            parsed.value?.let { s.timeSeries.add(timestampMs, it) }
        }
    }

    @Synchronized
    fun snapshotSeries(): List<SeriesSnapshot> = series.map { SeriesSnapshot(it.label, it.timeSeries.snapshot()) }

    companion object {
        private const val INVALID_LABEL = "invalid"
    }
}
