package de.serup

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import javax.swing.JTextField

/**
 * Regression test for a reentrancy bug: [SerialPlotterPanel.loadPortConfig] used to call
 * `graphPanel.setBaudRate(...)` before `plotsPanel.loadPlots(...)`, but `setBaudRate` re-fires
 * `onBaudRateChanged` synchronously (even for a purely programmatic value), which saved
 * whatever `plotsPanel` still displayed from *before* the load - overwriting the config being
 * loaded with stale/empty data before it was ever read.
 *
 * This reproduces it with the exact locally-observed repro: no real serial ports attached, so
 * FakeSerialPort is auto-selected as the default port, and the bug fires on every plugin/session
 * start.
 */
class FakePortPersistenceTest : BasePlatformTestCase() {
    fun `test fake port config survives editing a plot field and reopening the tool window`() {
        System.setProperty("de.serup.devMode", "true")
        check(DevMode.isActive) { "devMode system property must be set before DevMode is touched" }

        val panel = SerialPlotterPanel(project)
        try {
            val settings = SerialPlotterSettings.getInstance(project)

            val plotsPanelField = SerialPlotterPanel::class.java.getDeclaredField("plotsPanel").apply { isAccessible = true }
            val plotsByComponentField = PlotsPanel::class.java.getDeclaredField("plotsByComponent").apply { isAccessible = true }
            val prefixFieldField = PlotConfigPanel::class.java.getDeclaredField("prefixField").apply { isAccessible = true }

            fun firstPlotOf(p: SerialPlotterPanel): Plot {
                val plotsPanel = plotsPanelField.get(p) as PlotsPanel
                @Suppress("UNCHECKED_CAST")
                val plotsByComponent = plotsByComponentField.get(plotsPanel) as Map<Any, Plot>
                return plotsByComponent.values.first()
            }

            val prefixField = prefixFieldField.get(firstPlotOf(panel).config) as JTextField
            prefixField.text = "temp:"

            val savedPlots = settings.state.portConfigs[FakeSerialPort.DISPLAY_NAME]?.plots
            assertEquals("temp:", savedPlots?.firstOrNull()?.prefix)

            // Simulate reopening the tool window (or restarting the sandbox against the same
            // project) - a fresh SerialPlotterPanel should load the persisted prefix back, not a
            // blank default.
            panel.dispose()
            val reopened = SerialPlotterPanel(project)
            try {
                assertEquals("temp:", firstPlotOf(reopened).config.prefix)
            } finally {
                reopened.dispose()
            }
        } finally {
            panel.dispose()
        }
    }
}
