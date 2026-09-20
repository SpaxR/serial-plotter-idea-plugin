package de.serup

import com.fazecast.jSerialComm.SerialPort
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import java.awt.BorderLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel

class SerialPlotterPanel : Disposable {
    private val portComboBox = JComboBox<String>()
    private val graphPanel = GraphPanel()

    private var portConnection: PortConnection? = null
    private var connectedPortName: String? = null

    val component: JPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
        add(createPortSelectionPanel(), BorderLayout.NORTH)
        add(createMainSplitter(), BorderLayout.CENTER)
    }

    init {
        refreshPorts()
    }

    private fun createPortSelectionPanel(): JPanel {
        val refreshButton = JButton(AllIcons.Actions.Refresh).apply {
            toolTipText = SerialPlotterBundle.message("toolwindow.SerialPlotter.port.refresh.tooltip")
            addActionListener { refreshPorts() }
        }

        portComboBox.addActionListener { connectToSelectedPort() }

        return JBPanel<JBPanel<*>>().apply {
            add(JBLabel(SerialPlotterBundle.message("toolwindow.SerialPlotter.port.label")))
            add(portComboBox)
            add(refreshButton)
        }
    }

    private fun createMainSplitter(): JComponent {
        return JBSplitter(true, 0.7f).apply {
            firstComponent = graphPanel.component
            secondComponent = PlotsPanel().component
        }
    }

    private fun refreshPorts() {
        val previouslySelected = portComboBox.selectedItem as String?
        val portNames = SerialPort.getCommPorts().map { it.systemPortName } +
            listOfNotNull(FakeSerialPort.DISPLAY_NAME.takeIf { DevMode.isActive })
        if (portNames.isEmpty()) {
            portComboBox.model = DefaultComboBoxModel(arrayOf(SerialPlotterBundle.message("toolwindow.SerialPlotter.port.none")))
            portComboBox.isEnabled = false
        } else {
            portComboBox.model = DefaultComboBoxModel(portNames.toTypedArray())
            portComboBox.isEnabled = true
            if (previouslySelected != null && portNames.contains(previouslySelected)) {
                portComboBox.selectedItem = previouslySelected
            }
        }
        connectToSelectedPort()
    }

    private fun connectToSelectedPort() {
        val portName = (portComboBox.selectedItem as String?).takeIf { portComboBox.isEnabled }
        if (portName == connectedPortName) return

        portConnection?.close()
        connectedPortName = portName
        portConnection = portName?.let { name -> PortConnection(name) { line -> graphPanel.appendLogLine(line) } }
    }

    override fun dispose() {
        portConnection?.close()
    }
}
