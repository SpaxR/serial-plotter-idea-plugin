package de.serup

import com.intellij.icons.AllIcons
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTabbedPane
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * The "Plots" section: a tabbed pane of plot configurations, plus a "+" pseudo-tab to add new ones.
 * Starts out with just that "+" tab - call [loadPlots] to populate it, since which plots to show
 * depends on which port is selected and is decided by the caller, not this panel.
 */
class PlotsPanel(private val listener: Listener, private val onConfigChanged: () -> Unit) {
    /** Notified as plots are added/removed/renamed, so the Graph tab can mirror them. */
    interface Listener {
        fun onPlotAdded(plot: Plot)
        fun onPlotRemoved(plot: Plot)
        fun onPlotRenamed(plot: Plot)
    }

    private val tabbedPane = JBTabbedPane()

    // Tracks each tab's Plot alongside its content component, since the tabbed pane itself only
    // knows about JComponents and PlotTabHeaders, not the Plot objects behind them. EDT-only.
    private val plotsByComponent = mutableMapOf<JComponent, Plot>()

    // A separate thread-safe copy for route(), which runs on the PortConnection reader thread
    // while plotsByComponent above is only ever touched on the EDT.
    private val routablePlots = CopyOnWriteArrayList<Plot>()

    val component: JComponent = JBPanel<JBPanel<*>>(BorderLayout()).apply {
        border = BorderFactory.createTitledBorder(SerialPlotterBundle.message("toolwindow.SerialPlotter.plots.title"))
        add(tabbedPane, BorderLayout.CENTER)
    }

    init {
        tabbedPane.addTab(null, AllIcons.General.Add, JPanel())
        tabbedPane.setToolTipTextAt(0, SerialPlotterBundle.message("toolwindow.SerialPlotter.plots.add.tooltip"))

        // Intercept the press on the "+" tab before the tabbed pane selects it, so a real plot tab is
        // inserted (and selected) in its place instead of switching to the empty pseudo-tab.
        tabbedPane.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                val addTabIndex = tabbedPane.tabCount - 1
                if (tabbedPane.indexAtLocation(e.x, e.y) == addTabIndex) {
                    addPlotTab()
                }
            }
        })
    }

    /**
     * Replaces every current plot tab with one per entry in [plots] - or, if empty, a single blank
     * default tab, same as a brand new port with nothing configured yet.
     */
    fun loadPlots(plots: List<SerialPlotterSettings.PlotState>) {
        while (tabbedPane.tabCount > 1) {
            val plot = plotsByComponent.remove(tabbedPane.getComponentAt(0))
            tabbedPane.remove(0)
            if (plot != null) {
                routablePlots.remove(plot)
                listener.onPlotRemoved(plot)
            }
        }

        if (plots.isEmpty()) {
            addPlotTab()
        } else {
            plots.forEach { addPlotTab(it) }
        }
    }

    /** A snapshot of every current plot's configuration, e.g. to persist under the selected port. */
    fun exportPlots(): List<SerialPlotterSettings.PlotState> =
        (0 until tabbedPane.tabCount - 1).mapNotNull { index ->
            val title = (tabbedPane.getTabComponentAt(index) as? PlotTabHeader)?.title ?: return@mapNotNull null
            val configPanel = plotsByComponent[tabbedPane.getComponentAt(index)]?.config ?: return@mapNotNull null
            configPanel.toState(title)
        }

    private fun addPlotTab(initial: SerialPlotterSettings.PlotState? = null) {
        val insertIndex = tabbedPane.tabCount - 1
        val title = initial?.title ?: SerialPlotterBundle.message("toolwindow.SerialPlotter.plots.tab.defaultTitle")
        val configPanel = PlotConfigPanel(initial, onChange = onConfigChanged)
        val plot = Plot(title, configPanel)
        plotsByComponent[configPanel.component] = plot
        routablePlots.add(plot)
        tabbedPane.insertTab(title, null, configPanel.component, null, insertIndex)
        listener.onPlotAdded(plot)

        lateinit var header: PlotTabHeader
        header = PlotTabHeader(
            initialTitle = title,
            onSelect = {
                val index = tabbedPane.indexOfTabComponent(header)
                if (index != -1) {
                    tabbedPane.selectedIndex = index
                }
            },
            onRename = { newTitle ->
                val index = tabbedPane.indexOfTabComponent(header)
                if (index != -1) {
                    tabbedPane.setTitleAt(index, newTitle)
                    plot.title = newTitle
                    listener.onPlotRenamed(plot)
                    onConfigChanged()
                }
            },
            onClose = {
                val index = tabbedPane.indexOfTabComponent(header)
                if (index != -1) {
                    plotsByComponent.remove(tabbedPane.getComponentAt(index))
                    routablePlots.remove(plot)
                    tabbedPane.remove(index)
                    listener.onPlotRemoved(plot)
                    onConfigChanged()
                }
            },
        )
        tabbedPane.setTabComponentAt(insertIndex, header)
        tabbedPane.selectedIndex = insertIndex
        onConfigChanged()
    }

    /** Routes one raw line to every plot whose prefix matches it. Called from the reader thread. */
    fun route(line: String) {
        val now = System.currentTimeMillis()
        for (plot in routablePlots) {
            val prefix = plot.config.prefix
            if (prefix.isNotEmpty() && !line.startsWith(prefix)) continue

            val rest = if (prefix.isEmpty()) line else line.removePrefix(prefix)
            val values = rest.split(plot.config.separator.symbol).map { parseValue(it) }
            if (values.any { it.value != null }) {
                plot.addSample(now, values)
            }
        }
    }

    /** Marks a break in every plot's series - see [Plot.markGap]. Safe to call from any thread. */
    fun markDisconnected() {
        val now = System.currentTimeMillis()
        for (plot in routablePlots) {
            plot.markGap(now)
        }
    }

    /** Parses one token, e.g. "x:12.34" -> label "x", value 12.34; plain "12.34" -> no label. */
    private fun parseValue(token: String): Plot.ParsedValue {
        val trimmed = token.trim()
        val colonIndex = trimmed.indexOf(':')
        if (colonIndex == -1) {
            return Plot.ParsedValue(label = null, value = trimmed.toDoubleOrNull())
        }
        val label = trimmed.substring(0, colonIndex).trim().takeIf { it.isNotEmpty() }
        val value = trimmed.substring(colonIndex + 1).trim().toDoubleOrNull()
        return Plot.ParsedValue(label, value)
    }
}
