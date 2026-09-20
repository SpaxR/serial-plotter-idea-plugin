package de.serup

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBPanel
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.util.Locale
import javax.swing.BorderFactory

/** Renders one [Plot] as a scrolling line chart over the trailing [TimeSeries.WINDOW_MS] window. */
class PlotGraphView(private val plot: Plot) : JBPanel<PlotGraphView>(BorderLayout()) {
    init {
        preferredSize = Dimension(200, CHART_HEIGHT)
        border = BorderFactory.createTitledBorder(plot.title)
    }

    /** The border's title is fixed at construction time, so a rename needs to refresh it explicitly. */
    fun updateTitle(title: String) {
        border = BorderFactory.createTitledBorder(title)
    }

    // BoxLayout(Y_AXIS) only stacks children up to their maximum size, leaving any extra vertical
    // space blank below the last chart instead of stretching every chart to fill it.
    override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        val borderInsets = insets
        val left = borderInsets.left + AXIS_LABEL_WIDTH
        val top = borderInsets.top + PADDING
        val right = width - borderInsets.right - PADDING
        val bottom = height - borderInsets.bottom - TIME_LABEL_HEIGHT
        if (right <= left || bottom <= top) return

        val now = System.currentTimeMillis()
        val seriesSnapshots = plot.snapshotSeries()
        val allPoints = seriesSnapshots.flatten()
        if (allPoints.isEmpty()) {
            drawNoData(g2, left, top, right, bottom)
            return
        }

        val minValue = allPoints.minOf { it.value }
        val maxValue = allPoints.maxOf { it.value }
        val range = (maxValue - minValue).takeIf { it > 1e-9 } ?: 1.0
        val paddedMin = minValue - range * 0.1
        val paddedMax = maxValue + range * 0.1

        fun xFor(timestampMs: Long): Float =
            left + ((timestampMs - (now - TimeSeries.WINDOW_MS)).toFloat() / TimeSeries.WINDOW_MS) * (right - left)

        fun yFor(value: Double): Float =
            bottom - ((value - paddedMin) / (paddedMax - paddedMin)).toFloat() * (bottom - top)

        drawGridlines(g2, left, top, right, bottom, paddedMin, paddedMax)
        drawTimeAxis(g2, left, right, bottom)

        seriesSnapshots.forEachIndexed { index, points ->
            if (points.isEmpty()) return@forEachIndexed
            drawSeries(g2, points, PALETTE[index % PALETTE.size], plot.config.renderStyle, ::xFor, ::yFor, bottom.toFloat())
        }
    }

    private fun drawNoData(g2: Graphics2D, left: Int, top: Int, right: Int, bottom: Int) {
        g2.color = JBColor.GRAY
        val message = SerialPlotterBundle.message("toolwindow.SerialPlotter.graph.noData")
        val metrics = g2.fontMetrics
        val x = left + (right - left - metrics.stringWidth(message)) / 2
        val y = top + (bottom - top + metrics.ascent) / 2
        g2.drawString(message, x, y)
    }

    private fun drawGridlines(g2: Graphics2D, left: Int, top: Int, right: Int, bottom: Int, minValue: Double, maxValue: Double) {
        g2.font = AXIS_FONT
        val metrics = g2.fontMetrics
        for (i in 0..GRIDLINE_COUNT) {
            val fraction = i.toFloat() / GRIDLINE_COUNT
            val y = bottom - fraction * (bottom - top)
            g2.color = GRID_COLOR
            g2.drawLine(left, y.toInt(), right, y.toInt())

            val value = minValue + fraction * (maxValue - minValue)
            val label = String.format(Locale.ROOT, "%.2f", value)
            g2.color = JBColor.GRAY
            g2.drawString(label, left - metrics.stringWidth(label) - 4, y.toInt() + metrics.ascent / 2)
        }
    }

    private fun drawTimeAxis(g2: Graphics2D, left: Int, right: Int, bottom: Int) {
        g2.color = JBColor.GRAY
        g2.font = AXIS_FONT
        val metrics = g2.fontMetrics
        val startLabel = "-${TimeSeries.WINDOW_MS / 1000}s"
        g2.drawString(startLabel, left, bottom + metrics.ascent + 2)
        val endLabel = "now"
        g2.drawString(endLabel, right - metrics.stringWidth(endLabel), bottom + metrics.ascent + 2)
    }

    private fun drawSeries(
        g2: Graphics2D,
        points: List<TimeSeries.Point>,
        color: Color,
        style: PlotConfigPanel.RenderStyle,
        xFor: (Long) -> Float,
        yFor: (Double) -> Float,
        baselineY: Float,
    ) {
        val path = Path2D.Float()
        points.forEachIndexed { index, point ->
            val x = xFor(point.timestampMs)
            val y = yFor(point.value)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        if (style.hasFill) {
            val fillPath = path.clone() as Path2D.Float
            fillPath.lineTo(xFor(points.last().timestampMs), baselineY)
            fillPath.lineTo(xFor(points.first().timestampMs), baselineY)
            fillPath.closePath()
            g2.color = Color(color.red, color.green, color.blue, 50)
            g2.fill(fillPath)
        }

        if (style.hasLine) {
            g2.color = color
            g2.stroke = BasicStroke(1.5f)
            g2.draw(path)
        }

        if (style.hasDots) {
            g2.color = color
            points.forEach { point ->
                val x = xFor(point.timestampMs)
                val y = yFor(point.value)
                g2.fill(Ellipse2D.Float(x - DOT_RADIUS, y - DOT_RADIUS, DOT_RADIUS * 2, DOT_RADIUS * 2))
            }
        }
    }

    companion object {
        private const val CHART_HEIGHT = 160
        private const val PADDING = 8
        private const val AXIS_LABEL_WIDTH = 40
        private const val TIME_LABEL_HEIGHT = 16
        private const val GRIDLINE_COUNT = 4
        private const val DOT_RADIUS = 2.5f
        private val AXIS_FONT = Font(Font.SANS_SERIF, Font.PLAIN, 10)
        private val GRID_COLOR = JBColor(Color(0, 0, 0, 40), Color(255, 255, 255, 40))

        private val PALETTE = listOf(
            JBColor(Color(0x1F77B4), Color(0x5DA5DA)),
            JBColor(Color(0xFF7F0E), Color(0xFAA43A)),
            JBColor(Color(0x2CA02C), Color(0x60BD68)),
            JBColor(Color(0xD62728), Color(0xF17CB0)),
            JBColor(Color(0x9467BD), Color(0xB276B2)),
            JBColor(Color(0x8C564B), Color(0xDECF3F)),
        )
    }
}
