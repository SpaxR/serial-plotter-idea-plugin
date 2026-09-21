package de.serup

import java.awt.Container
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.JScrollPane
import javax.swing.SwingUtilities

/**
 * A [FlowLayout] that actually reports a preferred size accounting for wrapped rows.
 *
 * Plain [FlowLayout] already wraps components onto additional rows when a row runs out of
 * horizontal space, but [FlowLayout.preferredLayoutSize] ignores that wrapping and always reports
 * the size of a single row. A container sized from that (e.g. a panel placed in
 * [java.awt.BorderLayout.NORTH]) then never grows tall enough to show the wrapped rows, so
 * anything past the first row is clipped instead of becoming visible on the next line.
 */
class WrapLayout(align: Int, hgap: Int, vgap: Int) : FlowLayout(align, hgap, vgap) {
    override fun preferredLayoutSize(target: Container): Dimension = layoutSize(target, true)

    override fun minimumLayoutSize(target: Container): Dimension {
        val minimum = layoutSize(target, false)
        minimum.width -= hgap + 1
        return minimum
    }

    private fun layoutSize(target: Container, preferred: Boolean): Dimension {
        synchronized(target.treeLock) {
            // Walks up to the nearest ancestor with a known width - the target itself may not have
            // been sized yet (e.g. during the initial layout pass) - falling back to "unlimited"
            // width so components simply lay out in a single row until a real width is known.
            var container: Container = target
            while (container.size.width == 0 && container.parent != null) {
                container = container.parent
            }
            val targetWidth = container.size.width.takeIf { it != 0 } ?: Int.MAX_VALUE

            val insets = target.insets
            val horizontalInsetsAndGap = insets.left + insets.right + hgap * 2
            val maxWidth = targetWidth - horizontalInsetsAndGap

            val dim = Dimension(0, 0)
            var rowWidth = 0
            var rowHeight = 0

            for (i in 0 until target.componentCount) {
                val component = target.getComponent(i)
                if (!component.isVisible) continue

                val size = if (preferred) component.preferredSize else component.minimumSize
                if (rowWidth + size.width > maxWidth && rowWidth > 0) {
                    dim.width = maxOf(dim.width, rowWidth)
                    if (dim.height > 0) dim.height += vgap
                    dim.height += rowHeight
                    rowWidth = 0
                    rowHeight = 0
                }
                if (rowWidth != 0) rowWidth += hgap
                rowWidth += size.width
                rowHeight = maxOf(rowHeight, size.height)
            }
            dim.width = maxOf(dim.width, rowWidth)
            if (dim.height > 0) dim.height += vgap
            dim.height += rowHeight

            dim.width += horizontalInsetsAndGap
            dim.height += insets.top + insets.bottom + vgap * 2

            val scrollPane = SwingUtilities.getAncestorOfClass(JScrollPane::class.java, target)
            if (scrollPane != null && target.isValid) {
                dim.width -= hgap + 1
            }
            return dim
        }
    }
}
