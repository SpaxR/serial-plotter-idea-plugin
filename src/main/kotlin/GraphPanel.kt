package de.serup

import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextArea
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.text.DefaultCaret

/** The "Graph" section: a tab with the (future) plot, and a tab with the raw data read from the selected port. */
class GraphPanel {
    private val logArea = JBTextArea().apply {
        isEditable = false
        // The default caret auto-follows every insert, which would force the view back to the
        // bottom on every appended line even after the user scrolls up to read older ones. Turn
        // that off and drive scrolling ourselves in appendLogLine() instead.
        (caret as DefaultCaret).updatePolicy = DefaultCaret.NEVER_UPDATE
    }
    private val logScrollPane = JBScrollPane(logArea)

    val component: JComponent = JBTabbedPane().apply {
        addTab(SerialPlotterBundle.message("toolwindow.SerialPlotter.graph.tab.graph"), createGraphPlaceholder())
        addTab(SerialPlotterBundle.message("toolwindow.SerialPlotter.graph.tab.logs"), logScrollPane)
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

    private fun createGraphPlaceholder(): JComponent {
        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(
                JBLabel(SerialPlotterBundle.message("toolwindow.SerialPlotter.graph.placeholder"), SwingConstants.CENTER),
                BorderLayout.CENTER
            )
        }
    }
}
