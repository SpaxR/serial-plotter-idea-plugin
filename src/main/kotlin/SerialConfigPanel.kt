package de.serup

import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JTextField
import javax.swing.text.AbstractDocument
import javax.swing.text.AttributeSet
import javax.swing.text.DocumentFilter

/** Configuration for the serial connection itself. */
class SerialConfigPanel(
    initialBaudRate: Int = DEFAULT_BAUD_RATE,
    private val onChange: (Int) -> Unit = {},
) {
    @Volatile
    var baudRate: Int = initialBaudRate
        private set

    private val baudRateComboBox = ComboBox(COMMON_BAUD_RATES.map { it.toString() }.toTypedArray()).apply {
        isEditable = true
        selectedItem = baudRate.toString()
        restrictEditorToDigitsOnly()
        addActionListener {
            (selectedItem as? String)?.toIntOrNull()?.let {
                baudRate = it
                onChange(it)
            }
        }
    }

    /** Programmatically selects [rate], as if the user had entered it - also invoking [onChange]. */
    fun setBaudRate(rate: Int) {
        baudRateComboBox.selectedItem = rate.toString()
    }

    val component: JComponent = JBPanel<JBPanel<*>>(GridBagLayout()).also { panel ->
        var row = 0
        addRow(panel, row++, SerialPlotterBundle.message("toolwindow.SerialPlotter.config.baudRate.label"), baudRateComboBox)

        // Absorbs any extra vertical space the tab gives this panel, so the row above stays compact
        // at the top instead of spreading out to fill the whole height.
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

    // Restricts manual edits in the combo box's text field to digits, since a baud rate is never
    // anything else - rejecting bad input here is simpler than validating a free-form string later.
    private fun ComboBox<String>.restrictEditorToDigitsOnly() {
        val textField = editor.editorComponent as? JTextField ?: return
        (textField.document as? AbstractDocument)?.documentFilter = object : DocumentFilter() {
            override fun insertString(fb: FilterBypass, offset: Int, string: String, attr: AttributeSet?) {
                if (string.all { it.isDigit() }) super.insertString(fb, offset, string, attr)
            }

            override fun replace(fb: FilterBypass, offset: Int, length: Int, text: String, attrs: AttributeSet?) {
                if (text.all { it.isDigit() }) super.replace(fb, offset, length, text, attrs)
            }
        }
    }

    companion object {
        const val DEFAULT_BAUD_RATE = 9600

        private val COMMON_BAUD_RATES = listOf(
            300, 1200, 2400, 4800, 9600, 19200, 38400, 57600, 74880,
            115200, 230400, 250000, 500000, 1000000, 2000000,
        )
    }
}
