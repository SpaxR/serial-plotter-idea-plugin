package de.serup

import com.intellij.icons.AllIcons
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBInsets
import java.awt.AWTEvent
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JTabbedPane
import javax.swing.SwingUtilities

/**
 * The header shown on a plot tab: an editable title (double-click to rename) and a close button.
 * Selection, renaming and removal are reported to the caller instead of being handled directly,
 * since only the caller knows this header's current index within the [com.intellij.ui.components.JBTabbedPane].
 */
class PlotTabHeader(
    initialTitle: String,
    private val onSelect: () -> Unit,
    private val onRename: (String) -> Unit,
    private val onClose: () -> Unit,
) : JBPanel<PlotTabHeader>(FlowLayout(FlowLayout.CENTER, 2, 0)) {

    private val titleLabel = JBLabel(initialTitle).apply {
        toolTipText = SerialPlotterBundle.message("toolwindow.SerialPlotter.plots.rename.tooltip")
    }

    val title: String get() = titleLabel.text

    init {
        isOpaque = false

        // A custom tab component intercepts its own clicks, so the tabbed pane no longer selects the
        // tab automatically - forward the click ourselves, on both the label and the header's own
        // background (the small gaps the label/button don't cover).
        val headerClickListener = object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                onSelect()
                if (e.clickCount == 2) {
                    startEditing()
                }
            }
        }

        val closeButton = JButton(AllIcons.Actions.Close).apply {
            rolloverIcon = AllIcons.Actions.CloseHovered
            preferredSize = Dimension(icon.iconWidth, icon.iconHeight)
            border = BorderFactory.createEmptyBorder()
            margin = JBInsets.emptyInsets()
            isBorderPainted = false
            isContentAreaFilled = false
            isOpaque = false
            isFocusable = false
            toolTipText = SerialPlotterBundle.message("toolwindow.SerialPlotter.plots.remove.tooltip")
            addActionListener { onClose() }
        }

        titleLabel.addMouseListener(headerClickListener)
        addMouseListener(headerClickListener)
        forwardHoverEvents(titleLabel)
        forwardHoverEvents(this)

        add(titleLabel)
        add(closeButton)
    }

    /**
     * Registering a MouseListener on [component] claims its whole mouse-event category, including
     * mouseEntered/mouseExited - so the tabbed pane's own rollover highlight never sees them and the
     * tab looks permanently un-hovered. Forward those events to the actual JTabbedPane ourselves.
     */
    private fun forwardHoverEvents(component: JComponent) {
        val forwarder = object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) = forward(e)
            override fun mouseExited(e: MouseEvent) = forward(e)
            override fun mouseMoved(e: MouseEvent) = forward(e)

            private fun forward(e: MouseEvent) {
                val tabbedPane = SwingUtilities.getAncestorOfClass(JTabbedPane::class.java, this@PlotTabHeader)
                    as? JTabbedPane ?: return
                tabbedPane.dispatchEvent(SwingUtilities.convertMouseEvent(component, e, tabbedPane))
            }
        }
        component.addMouseListener(forwarder)
        component.addMouseMotionListener(forwarder)
    }

    // Tracks the listener installed by the currently active edit session, if any, so it can be
    // torn down again in stopEditing().
    private var outsideClickListener: AWTEventListener? = null

    private fun startEditing() {
        val titleField = JBTextField(titleLabel.text).apply {
            addActionListener { stopEditing(this, commit = true) }
            addKeyListener(object : KeyAdapter() {
                override fun keyPressed(event: KeyEvent) {
                    if (event.keyCode == KeyEvent.VK_ESCAPE) stopEditing(this@apply, commit = false)
                }
            })
        }
        forwardHoverEvents(titleField)

        // Relying on Swing/IntelliJ's focus-transfer events proved unreliable here (both the
        // component-level FocusListener and the KeyboardFocusManager's "permanentFocusOwner" watch
        // missed real clicks-away in practice). Detecting the raw mouse press directly, application-wide,
        // sidesteps that machinery entirely: any press outside the field itself commits the edit.
        val listener = AWTEventListener { event ->
            if (event is MouseEvent && event.id == MouseEvent.MOUSE_PRESSED) {
                val pressedComponent = event.component
                if (pressedComponent !== titleField && !SwingUtilities.isDescendingFrom(pressedComponent, titleField)) {
                    stopEditing(titleField, commit = true)
                }
            }
        }
        outsideClickListener = listener
        Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.MOUSE_EVENT_MASK)

        val labelIndex = components.indexOf(titleLabel)
        remove(titleLabel)
        add(titleField, labelIndex)
        revalidate()
        repaint()
        titleField.requestFocusInWindow()
        titleField.selectAll()
    }

    private fun stopEditing(titleField: JBTextField, commit: Boolean) {
        outsideClickListener?.let {
            Toolkit.getDefaultToolkit().removeAWTEventListener(it)
            outsideClickListener = null
        }

        if (commit) {
            val newTitle = titleField.text.trim()
            if (newTitle.isNotEmpty()) {
                titleLabel.text = newTitle
                onRename(newTitle)
            }
        }
        val fieldIndex = components.indexOf(titleField)
        if (fieldIndex != -1) {
            remove(titleField)
            add(titleLabel, fieldIndex)
            revalidate()
            repaint()
        }
    }
}
