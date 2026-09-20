package de.serup

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTabbedPane
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel

/** The "Plots" section: a tabbed pane of plot configurations, plus a "+" pseudo-tab to add new ones. */
class PlotsPanel(private val project: Project, private val listener: Listener) {
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

        val persistedPlots = SerialPlotterSettings.getInstance(project).state.plots
        if (persistedPlots.isEmpty()) {
            addPlotTab()
        } else {
            persistedPlots.forEach { addPlotTab(it) }
        }
    }

    private fun addPlotTab(initial: SerialPlotterSettings.PlotState? = null) {
        val insertIndex = tabbedPane.tabCount - 1
        val title = initial?.title ?: SerialPlotterBundle.message("toolwindow.SerialPlotter.plots.tab.defaultTitle")
        val configPanel = PlotConfigPanel(initial, onChange = ::saveState)
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
                    saveState()
                }
            },
            onClose = {
                val index = tabbedPane.indexOfTabComponent(header)
                if (index != -1) {
                    plotsByComponent.remove(tabbedPane.getComponentAt(index))
                    routablePlots.remove(plot)
                    tabbedPane.remove(index)
                    listener.onPlotRemoved(plot)
                    saveState()
                }
            },
        )
        tabbedPane.setTabComponentAt(insertIndex, header)
        tabbedPane.selectedIndex = insertIndex
        saveState()
    }

    /** Routes one raw line to every plot whose prefix matches it. Called from the reader thread. */
    fun route(line: String) {
        val now = System.currentTimeMillis()
        for (plot in routablePlots) {
            val prefix = plot.config.prefix
            if (prefix.isNotEmpty() && !line.startsWith(prefix)) continue

            val rest = if (prefix.isEmpty()) line else line.removePrefix(prefix)
            val values = rest.split(plot.config.separator.symbol).map { it.trim().toDoubleOrNull() }
            if (values.any { it != null }) {
                plot.addSample(now, values)
            }
        }
    }

    private fun saveState() {
        val plots = (0 until tabbedPane.tabCount - 1).mapNotNull { index ->
            val title = (tabbedPane.getTabComponentAt(index) as? PlotTabHeader)?.title ?: return@mapNotNull null
            val configPanel = plotsByComponent[tabbedPane.getComponentAt(index)]?.config ?: return@mapNotNull null
            configPanel.toState(title)
        }

        val state = SerialPlotterSettings.getInstance(project).state
        state.plots.clear()
        state.plots.addAll(plots)
    }
}
