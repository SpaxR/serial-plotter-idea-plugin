package de.serup

import com.fazecast.jSerialComm.SerialPort
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.Box
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

class SerialPlotterPanel(private val project: Project) : Disposable {
    private val portComboBox = ComboBox<String>()
    private val graphPanel = GraphPanel(project, onBaudRateChanged = { openConnection() })
    private val plotsPanel = PlotsPanel(project, graphPanel)

    private var portConnection: PortConnection? = null
    private var connectedPortName: String? = null

    // Lets the user stop the connection on demand - e.g. to free the port for another program to
    // use, since a real serial port can only be held open by one process at a time - without losing
    // the selected port or baud rate.
    private var connectionEnabled = false
    private lateinit var connectionToggleButton: JButton

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

        connectionToggleButton = JButton().apply {
            addActionListener { setConnectionEnabled(!connectionEnabled) }
        }
        updateConnectionToggleButton()

        return JBPanel<JBPanel<*>>().apply {
            add(JBLabel(SerialPlotterBundle.message("toolwindow.SerialPlotter.port.label")))
            add(portComboBox)
            add(refreshButton)
            add(Box.createHorizontalStrut(JBUI.scale(12)))
            add(connectionToggleButton)
        }
    }

    private fun createMainSplitter(): JComponent {
        return JBSplitter(true, 0.7f).apply {
            firstComponent = graphPanel.component
            secondComponent = plotsPanel.component
        }
    }

    private fun refreshPorts() {
        // Falls back to the persisted port on the very first call (before anything has been selected
        // in this session), so a previously connected port is restored automatically on startup.
        val previouslySelected = (portComboBox.selectedItem as String?)
            ?: SerialPlotterSettings.getInstance(project).state.lastSelectedPort
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

        connectedPortName = portName
        openConnection()

        // Only persist an actual port, not a temporary "no ports found" state, so unplugging the
        // device doesn't erase the last known port before it gets plugged back in.
        if (portName != null) {
            SerialPlotterSettings.getInstance(project).state.lastSelectedPort = portName
        }
    }

    /** Stops the connection - e.g. because the tool window was just closed - without forgetting the
     * selected port or baud rate, so a later Start reconnects with the same settings. */
    fun stopConnection() {
        setConnectionEnabled(false)
    }

    private fun setConnectionEnabled(enabled: Boolean) {
        connectionEnabled = enabled
        updateConnectionToggleButton()
        openConnection()
    }

    private fun updateConnectionToggleButton() {
        connectionToggleButton.text = SerialPlotterBundle.message(
            if (connectionEnabled) "toolwindow.SerialPlotter.port.connection.stop.label"
            else "toolwindow.SerialPlotter.port.connection.start.label"
        )
        connectionToggleButton.toolTipText = SerialPlotterBundle.message(
            if (connectionEnabled) "toolwindow.SerialPlotter.port.connection.stop.tooltip"
            else "toolwindow.SerialPlotter.port.connection.start.tooltip"
        )
    }

    /**
     * (Re)opens the connection to [connectedPortName] with the currently configured baud rate,
     * closing any previous one first, unless [connectionEnabled] is false. Also called when the
     * baud rate changes, to apply it to the port already in use.
     */
    private fun openConnection() {
        // Whatever was running is being interrupted - mark the break so the graph doesn't draw a
        // misleading line straight across however long it takes the replacement connection (if any)
        // to deliver its first line.
        if (portConnection != null) plotsPanel.markDisconnected()

        portConnection?.close()
        portConnection = if (connectionEnabled) connectedPortName?.let { name ->
            PortConnection(name, graphPanel.baudRate) { line ->
                graphPanel.appendLogLine(line)
                plotsPanel.route(line)
            }
        } else null
    }

    override fun dispose() {
        portConnection?.close()
        graphPanel.dispose()
    }
}
