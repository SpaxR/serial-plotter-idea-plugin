package de.serup

import com.intellij.icons.AllIcons
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTabbedPane
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants

/** The "Plots" section: a tabbed pane of plot configurations, plus a "+" pseudo-tab to add new ones. */
class PlotsPanel {
    private val tabbedPane = JBTabbedPane()

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

    private fun addPlotTab() {
        val insertIndex = tabbedPane.tabCount - 1
        val title = SerialPlotterBundle.message("toolwindow.SerialPlotter.plots.tab.defaultTitle")
        tabbedPane.insertTab(title, null, createPlotConfigPanel(), null, insertIndex)

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
                }
            },
            onClose = {
                val index = tabbedPane.indexOfTabComponent(header)
                if (index != -1) {
                    tabbedPane.remove(index)
                }
            },
        )
        tabbedPane.setTabComponentAt(insertIndex, header)
        tabbedPane.selectedIndex = insertIndex
    }

    private fun createPlotConfigPanel(): JComponent {
        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(
                JBLabel(SerialPlotterBundle.message("toolwindow.SerialPlotter.plots.placeholder"), SwingConstants.CENTER),
                BorderLayout.CENTER
            )
        }
    }
}
