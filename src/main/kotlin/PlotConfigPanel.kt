package de.serup

import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.ButtonGroup
import javax.swing.JComponent
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * Configuration for a single plot: which prefix identifies its lines in the raw stream, how the
 * values on a line are separated, and how the line should be rendered.
 */
class PlotConfigPanel(
    initial: SerialPlotterSettings.PlotState? = null,
    private val onChange: () -> Unit = {},
) {
    enum class Separator(val symbol: String) {
        PIPE("|"),
        COMMA(","),
        SEMICOLON(";"),
    }

    // A flat list of the valid combinations, rather than a "line style" + "show dots" toggle pair,
    // so there is no way to end up with neither a line nor dots (nothing rendered) in the first place.
    enum class RenderStyle(private val bundleKey: String, val hasLine: Boolean, val hasFill: Boolean, val hasDots: Boolean) {
        DOTS("toolwindow.SerialPlotter.plots.config.renderStyle.dots", hasLine = false, hasFill = false, hasDots = true),
        LINE("toolwindow.SerialPlotter.plots.config.renderStyle.line", hasLine = true, hasFill = false, hasDots = false),
        FILLED("toolwindow.SerialPlotter.plots.config.renderStyle.filled", hasLine = true, hasFill = true, hasDots = false),
        LINE_AND_DOTS("toolwindow.SerialPlotter.plots.config.renderStyle.lineAndDots", hasLine = true, hasFill = false, hasDots = true),
        FILLED_AND_DOTS("toolwindow.SerialPlotter.plots.config.renderStyle.filledAndDots", hasLine = true, hasFill = true, hasDots = true);

        override fun toString() = SerialPlotterBundle.message(bundleKey)
    }

    private val prefixField = JBTextField(initial?.prefix ?: "", 20).apply {
        document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = onChange()
            override fun removeUpdate(e: DocumentEvent) = onChange()
            override fun changedUpdate(e: DocumentEvent) = onChange()
        })
    }

    // JTextComponent.getText() is safe to call off the EDT (it goes through the document's read
    // lock), which matters here since the router reads prefix/separator/renderStyle from the
    // PortConnection reader thread while the user can edit them on the EDT at the same time.
    val prefix: String get() = prefixField.text

    @Volatile
    var separator: Separator = initial?.separator ?: Separator.PIPE
        private set

    @Volatile
    var renderStyle: RenderStyle = initial?.renderStyle ?: RenderStyle.LINE
        private set

    val component: JComponent = JBPanel<JBPanel<*>>(GridBagLayout()).also { panel ->
        var row = 0
        addRow(panel, row++, SerialPlotterBundle.message("toolwindow.SerialPlotter.plots.config.prefix.label"), prefixField)
        addRow(panel, row++, SerialPlotterBundle.message("toolwindow.SerialPlotter.plots.config.separator.label"), createSeparatorButtons())
        addRow(panel, row++, SerialPlotterBundle.message("toolwindow.SerialPlotter.plots.config.renderStyle.label"), createRenderStyleComboBox())

        // Absorbs any extra vertical space the tab gives this panel, so the rows above stay
        // compact at the top instead of spreading out to fill the whole height.
        panel.add(
            JBPanel<JBPanel<*>>(),
            GridBagConstraints().apply {
                gridx = 0
                gridy = row
                gridwidth = 2
                weighty = 1.0
                fill = GridBagConstraints.VERTICAL
            }
        )
    }

    private fun addRow(panel: JComponent, rowIndex: Int, label: String, content: JComponent) {
        panel.add(
            JBLabel(label),
            GridBagConstraints().apply {
                gridx = 0
                gridy = rowIndex
                anchor = GridBagConstraints.EAST
                insets = JBUI.insets(4, 0, 4, 8)
            }
        )
        panel.add(
            content,
            GridBagConstraints().apply {
                gridx = 1
                gridy = rowIndex
                anchor = GridBagConstraints.WEST
                fill = GridBagConstraints.HORIZONTAL
                weightx = 1.0
                insets = JBUI.insets(4, 0)
            }
        )
    }

    private fun createSeparatorButtons(): JComponent {
        val group = ButtonGroup()
        return JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 15, 0)).apply {
            Separator.entries.forEach { option ->
                add(JBRadioButton(option.symbol, option == separator).apply {
                    addActionListener { separator = option; onChange() }
                    group.add(this)
                })
            }
        }
    }

    private fun createRenderStyleComboBox(): JComponent {
        return ComboBox(RenderStyle.entries.toTypedArray()).apply {
            selectedItem = renderStyle
            addActionListener { renderStyle = selectedItem as RenderStyle; onChange() }
        }
    }

    fun toState(title: String): SerialPlotterSettings.PlotState = SerialPlotterSettings.PlotState().also {
        it.title = title
        it.prefix = prefix
        it.separator = separator
        it.renderStyle = renderStyle
    }
}
