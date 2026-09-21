package de.serup

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextArea
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.text.DefaultCaret

/**
 * The "Graph" section: a tab with one scrolling chart per plot, a tab with the raw port data, and a
 * tab to configure the serial connection itself. [onBaudRateChanged] is called whenever the baud rate
 * is changed, so the caller can reopen the port connection with it.
 */
class GraphPanel(project: Project, private val onBaudRateChanged: () -> Unit) : PlotsPanel.Listener {
    private val logArea = JBTextArea().apply {
        isEditable = false
        // The default caret auto-follows every insert, which would force the view back to the
        // bottom on every appended line even after the user scrolls up to read older ones. Turn
        // that off and drive scrolling ourselves in appendLogLine() instead.
        (caret as DefaultCaret).updatePolicy = DefaultCaret.NEVER_UPDATE
    }
    private val logScrollPane = JBScrollPane(logArea)

    private val chartsPanel = JBPanel<JBPanel<*>>().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
    }
    private val chartsScrollPane = JBScrollPane(chartsPanel)
    private val chartViewsByPlot = mutableMapOf<Plot, PlotGraphView>()

    private val serialConfigPanel = SerialConfigPanel(
        initialBaudRate = SerialPlotterSettings.getInstance(project).state.baudRate,
        onChange = { baudRate ->
            SerialPlotterSettings.getInstance(project).state.baudRate = baudRate
            onBaudRateChanged()
        },
    )

    /** The currently configured baud rate, to open the port connection with. */
    val baudRate: Int get() = serialConfigPanel.baudRate

    // Repaints the charts on a fixed cadence, independent of how often data actually arrives, so
    // the time axis keeps scrolling smoothly even between samples - like an oscilloscope.
    private val redrawTimer = Timer(REDRAW_INTERVAL_MS) { chartViewsByPlot.values.forEach { it.repaint() } }

    val component: JComponent = JBTabbedPane().apply {
        addTab(SerialPlotterBundle.message("toolwindow.SerialPlotter.graph.tab.graph"), chartsScrollPane)
        addTab(SerialPlotterBundle.message("toolwindow.SerialPlotter.graph.tab.logs"), logScrollPane)
        addTab(SerialPlotterBundle.message("toolwindow.SerialPlotter.graph.tab.config"), serialConfigPanel.component)
        addChangeListener {
            if (selectedComponent == logScrollPane) {
                scrollLogToBottom()
            }
        }
    }

    init {
        redrawTimer.start()
    }

    override fun onPlotAdded(plot: Plot) {
        val view = PlotGraphView(plot)
        chartViewsByPlot[plot] = view
        chartsPanel.add(view)
        chartsPanel.revalidate()
        chartsPanel.repaint()
    }

    override fun onPlotRemoved(plot: Plot) {
        val view = chartViewsByPlot.remove(plot) ?: return
        chartsPanel.remove(view)
        chartsPanel.revalidate()
        chartsPanel.repaint()
    }

    override fun onPlotRenamed(plot: Plot) {
        chartViewsByPlot[plot]?.updateTitle(plot.title)
    }

    /** Stops the redraw timer. Must be called when the tool window is disposed. */
    fun dispose() {
        redrawTimer.stop()
    }

    /** Appends a line read from the currently selected serial port. Safe to call from any thread. */
    fun appendLogLine(line: String) {
        SwingUtilities.invokeLater {
            val scrollBar = logScrollPane.verticalScrollBar
            val wasAtBottom = scrollBar.value + scrollBar.visibleAmount >= scrollBar.maximum
            logArea.append(line + "\n")
            if (wasAtBottom) {
                // The scrollbar's maximum only reflects the new line's height after layout has
                // run, which happens on the EDT after this event - so defer the actual scroll.
                SwingUtilities.invokeLater { scrollBar.value = scrollBar.maximum }
            }
        }
    }

    /** Jumps the raw log tab to its bottom. Deferred since selecting the tab lays it out first. */
    private fun scrollLogToBottom() {
        SwingUtilities.invokeLater {
            val scrollBar = logScrollPane.verticalScrollBar
            SwingUtilities.invokeLater { scrollBar.value = scrollBar.maximum }
        }
    }

    companion object {
        private const val REDRAW_INTERVAL_MS = 100
    }
}
