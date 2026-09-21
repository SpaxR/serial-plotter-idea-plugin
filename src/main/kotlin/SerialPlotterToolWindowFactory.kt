package de.serup

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.content.ContentFactory

class SerialPlotterToolWindowFactory : ToolWindowFactory {
    override fun shouldBeAvailable(project: Project) = true

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = SerialPlotterPanel(project)
        Disposer.register(toolWindow.disposable, panel)
        val content = ContentFactory.getInstance().createContent(panel.component, null, false)
        toolWindow.contentManager.addContent(content)

        // Closing the tool window only hides it - the content created above stays alive - so without
        // this the port would stay held open in the background for as long as the project is open.
        project.messageBus.connect(panel).subscribe(
            ToolWindowManagerListener.TOPIC,
            object : ToolWindowManagerListener {
                override fun stateChanged(
                    toolWindowManager: ToolWindowManager,
                    changedToolWindow: ToolWindow,
                    changeType: ToolWindowManagerListener.ToolWindowManagerEventType,
                ) {
                    if (changedToolWindow.id == toolWindow.id &&
                        changeType == ToolWindowManagerListener.ToolWindowManagerEventType.HideToolWindow
                    ) {
                        panel.stopConnection()
                    }
                }
            },
        )
    }
}
