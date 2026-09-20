package de.serup

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/** Persists the tool window's state (selected port, plot configurations) across IDE restarts. */
@Service(Service.Level.PROJECT)
@State(name = "SerialPlotterSettings", storages = [Storage("serial-plotter.xml")])
class SerialPlotterSettings : PersistentStateComponent<SerialPlotterSettings.State> {
    class State {
        var lastSelectedPort: String? = null
        var plots: MutableList<PlotState> = mutableListOf()
    }

    class PlotState {
        var title: String = ""
        var prefix: String = ""
        var separator: PlotConfigPanel.Separator = PlotConfigPanel.Separator.PIPE
        var renderStyle: PlotConfigPanel.RenderStyle = PlotConfigPanel.RenderStyle.LINE
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
