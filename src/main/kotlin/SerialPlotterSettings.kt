package de.serup

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/**
 * Persists the tool window's state across IDE restarts: the last selected port, plus each port's own
 * baud rate and plot configuration - kept separate per port since different devices plugged into
 * different ports rarely share either.
 */
@Service(Service.Level.PROJECT)
@State(name = "SerialPlotterSettings", storages = [Storage("serial-plotter.xml")])
class SerialPlotterSettings : PersistentStateComponent<SerialPlotterSettings.State> {
    class State {
        var lastSelectedPort: String? = null
        var portConfigs: MutableMap<String, PortConfig> = mutableMapOf()
    }

    class PortConfig {
        var baudRate: Int = SerialConfigPanel.DEFAULT_BAUD_RATE
        var plots: MutableList<PlotState> = mutableListOf()
    }

    class PlotState {
        var title: String = ""
        var prefix: String = ""
        var ignoreChars: String = ""
        var separator: String = PlotConfigPanel.Separator.PIPE.name
        var renderStyle: String = PlotConfigPanel.RenderStyle.LINE.name
        var lowerBound: String = ""
        var upperBound: String = ""
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    companion object {
        fun getInstance(project: Project): SerialPlotterSettings = project.service()
    }
}
