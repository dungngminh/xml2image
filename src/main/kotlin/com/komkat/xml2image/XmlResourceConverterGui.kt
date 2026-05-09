package com.komkat.xml2image

import org.w3c.dom.Element
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics2D
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.RenderingHints
import java.awt.Shape
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDropEvent
import java.awt.geom.AffineTransform
import java.awt.geom.GeneralPath
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.ImageWriter
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.JScrollPane
import javax.swing.JSpinner
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.SpinnerNumberModel
import javax.swing.SwingUtilities
import javax.swing.SwingWorker
import javax.swing.UIManager
import javax.swing.WindowConstants
import javax.swing.border.EmptyBorder
import javax.swing.filechooser.FileNameExtensionFilter
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

fun main() {
    SwingUtilities.invokeLater {
        runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
        XmlResourceConverterGui().isVisible = true
    }
}

private class XmlResourceConverterGui : JFrame("XML Resource to Image Converter") {
    private val fileModel = DefaultListModel<File>()
    private val logArea = JTextArea(8, 60)
    private val outputDir = JTextField()
    private val formatBox = JComboBox(arrayOf("png", "jpg", "webp"))
    private val scaleSpinner = JSpinner(SpinnerNumberModel(1.0, 0.1, 20.0, 0.25))
    private val widthSpinner = JSpinner(SpinnerNumberModel(0, 0, 8192, 1))
    private val heightSpinner = JSpinner(SpinnerNumberModel(0, 0, 8192, 1))
    private val keepAlpha = JCheckBox("Keep alpha", true)
    private val progress = JProgressBar()
    private val convertButton = JButton("Convert")

    init {
        defaultCloseOperation = WindowConstants.EXIT_ON_CLOSE
        minimumSize = Dimension(820, 560)
        contentPane = buildUi()
        installDropTarget()
        pack()
        setLocationRelativeTo(null)
    }

    private fun buildUi(): JPanel {
        val root = JPanel(BorderLayout(12, 12)).apply { border = EmptyBorder(14, 14, 14, 14) }
        val fileList = JList(fileModel).apply {
            cellRenderer = javax.swing.ListCellRenderer { list, value, _, selected, _ ->
                JLabel(value.absolutePath).apply {
                    isOpaque = true
                    border = EmptyBorder(4, 6, 4, 6)
                    background = if (selected) list.selectionBackground else list.background
                    foreground = if (selected) list.selectionForeground else list.foreground
                }
            }
        }

        val fileButtons = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
            add(JButton("Add XML files").apply { addActionListener { chooseFiles() } })
            add(JButton("Add folder").apply { addActionListener { chooseFolder() } })
            add(JButton("Remove selected").apply {
                addActionListener { fileList.selectedValuesList.forEach(fileModel::removeElement) }
            })
            add(JButton("Clear").apply { addActionListener { fileModel.clear() } })
        }

        val filesPanel = JPanel(BorderLayout(8, 8)).apply {
            add(JLabel("Input Android vector XML files"), BorderLayout.NORTH)
            add(JScrollPane(fileList), BorderLayout.CENTER)
            add(fileButtons, BorderLayout.SOUTH)
        }

        val options = JPanel(GridBagLayout())
        val c = GridBagConstraints().apply {
            insets = Insets(4, 4, 4, 4)
            anchor = GridBagConstraints.WEST
            fill = GridBagConstraints.HORIZONTAL
        }
        fun row(label: String, component: java.awt.Component) {
            c.gridx = 0
            c.weightx = 0.0
            options.add(JLabel(label), c)
            c.gridx = 1
            c.weightx = 1.0
            options.add(component, c)
            c.gridy++
        }

        row("Output folder", outputDir)
        c.gridx = 2
        c.gridy = 0
        c.weightx = 0.0
        options.add(JButton("Browse").apply { addActionListener { chooseOutputFolder() } }, c)
        c.gridy = 1
        row("Format", formatBox)
        row("Scale", scaleSpinner)
        row("Override width", widthSpinner)
        row("Override height", heightSpinner)
        c.gridx = 1
        options.add(keepAlpha, c)

        convertButton.addActionListener { convert() }
        val actions = JPanel(BorderLayout(8, 8)).apply {
            add(options, BorderLayout.CENTER)
            add(convertButton, BorderLayout.EAST)
        }

        logArea.isEditable = false
        logArea.font = Font(Font.MONOSPACED, Font.PLAIN, 12)

        root.add(actions, BorderLayout.NORTH)
        root.add(filesPanel, BorderLayout.CENTER)
        root.add(JScrollPane(logArea), BorderLayout.SOUTH)
        root.add(progress, BorderLayout.PAGE_END)
        return root
    }

    private fun installDropTarget() {
        DropTarget(this, object : DropTargetAdapter() {
            override fun drop(event: DropTargetDropEvent) {
                try {
                    event.acceptDrop(DnDConstants.ACTION_COPY)
                    val dropped = event.transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<*>
                    dropped.filterIsInstance<File>().forEach { addPath(it.toPath()) }
                    event.dropComplete(true)
                } catch (ex: Exception) {
                    event.dropComplete(false)
                    log("Drop failed: ${ex.message}")
                }
            }
        })
    }

    private fun chooseFiles() {
        JFileChooser().apply {
            isMultiSelectionEnabled = true
            fileFilter = FileNameExtensionFilter("XML files", "xml")
            if (showOpenDialog(this@XmlResourceConverterGui) == JFileChooser.APPROVE_OPTION) {
                selectedFiles.forEach(::addXmlFile)
            }
        }
    }

    private fun chooseFolder() {
        JFileChooser().apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            if (showOpenDialog(this@XmlResourceConverterGui) == JFileChooser.APPROVE_OPTION) {
                addPath(selectedFile.toPath())
            }
        }
    }

    private fun chooseOutputFolder() {
        JFileChooser().apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            if (showOpenDialog(this@XmlResourceConverterGui) == JFileChooser.APPROVE_OPTION) {
                outputDir.text = selectedFile.absolutePath
            }
        }
    }

    private fun addPath(path: Path) {
        if (Files.isDirectory(path)) {
            Files.walk(path).use { stream ->
                stream.filter { it.toString().lowercase(Locale.ROOT).endsWith(".xml") }
                    .forEach { addXmlFile(it.toFile()) }
            }
        } else {
            addXmlFile(path.toFile())
        }
    }

    private fun addXmlFile(file: File) {
        if (file.name.lowercase(Locale.ROOT).endsWith(".xml") && !fileModel.contains(file)) {
            fileModel.addElement(file)
        }
    }

    private fun convert() {
        if (fileModel.isEmpty) {
            JOptionPane.showMessageDialog(this, "Add one or more XML files first.")
            return
        }
        val outDir = File(outputDir.text.trim())
        if (outputDir.text.isBlank()) {
            JOptionPane.showMessageDialog(this, "Choose an output folder.")
            return
        }
        if (!outDir.exists() && !outDir.mkdirs()) {
            JOptionPane.showMessageDialog(this, "Cannot create output folder.")
            return
        }

        val files = (0 until fileModel.size()).map(fileModel::get)
        val format = formatBox.selectedItem as String
        val scale = (scaleSpinner.value as Number).toDouble()
        val width = (widthSpinner.value as Number).toInt()
        val height = (heightSpinner.value as Number).toInt()
        val alpha = keepAlpha.isSelected

        convertButton.isEnabled = false
        progress.minimum = 0
        progress.maximum = files.size
        progress.value = 0
        logArea.text = ""

        object : SwingWorker<ConversionSummary, String>() {
            override fun doInBackground(): ConversionSummary {
                var successCount = 0
                val failures = mutableListOf<String>()
                files.forEachIndexed { index, file ->
                    try {
                        val drawable = VectorDrawable.read(file)
                        val outWidth = if (width > 0) width else max(1, (drawable.width * scale).roundToInt())
                        val outHeight = if (height > 0) height else max(1, (drawable.height * scale).roundToInt())
                        val image = drawable.render(outWidth, outHeight, alpha)
                        val target = uniqueOutputFile(outDir, file.nameWithoutExtension, format)
                        ImageExporter.write(image, target, format, alpha)
                        successCount++
                        publish("OK  ${file.name} -> ${target.name}")
                    } catch (ex: Exception) {
                        val reason = "${file.name}: ${ex.message}"
                        failures += reason
                        publish("ERR $reason")
                    }
                    setProgress(index + 1)
                }
                return ConversionSummary(successCount, failures)
            }

            override fun process(chunks: List<String>) {
                chunks.forEach(::log)
                this@XmlResourceConverterGui.progress.value = getProgress()
            }

            override fun done() {
                convertButton.isEnabled = true
                this@XmlResourceConverterGui.progress.value = files.size
                val summary = runCatching { get() }.getOrElse { ex ->
                    ConversionSummary(0, listOf(ex.message ?: "Unknown conversion error"))
                }
                log("Done. ${summary.successCount} converted, ${summary.failures.size} failed.")
                showConversionDialog(summary)
            }
        }.execute()
    }

    private fun showConversionDialog(summary: ConversionSummary) {
        if (summary.failures.isEmpty()) {
            JOptionPane.showMessageDialog(
                this,
                "Converted ${summary.successCount} file(s) successfully.",
                "Conversion complete",
                JOptionPane.INFORMATION_MESSAGE,
            )
            return
        }

        val details = summary.failures.take(8).joinToString(System.lineSeparator())
        val extra = if (summary.failures.size > 8) {
            "${System.lineSeparator()}...and ${summary.failures.size - 8} more. See the log for details."
        } else {
            ""
        }
        JOptionPane.showMessageDialog(
            this,
            "Converted ${summary.successCount} file(s), failed ${summary.failures.size}.${System.lineSeparator()}$details$extra",
            "Conversion finished with errors",
            JOptionPane.ERROR_MESSAGE,
        )
    }

    private fun log(message: String) {
        logArea.append(message + System.lineSeparator())
        logArea.caretPosition = logArea.document.length
    }
}

private data class ConversionSummary(
    val successCount: Int,
    val failures: List<String>,
)

private data class VectorDrawable(
    val width: Double,
    val height: Double,
    val viewportWidth: Double,
    val viewportHeight: Double,
    val commands: List<DrawCommand>,
) {
    fun render(outWidth: Int, outHeight: Int, alpha: Boolean): BufferedImage {
        val image = BufferedImage(outWidth, outHeight, if (alpha) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        if (!alpha) {
            g.color = Color.WHITE
            g.fillRect(0, 0, outWidth, outHeight)
        }
        g.scale(outWidth / viewportWidth, outHeight / viewportHeight)
        commands.forEach { it.draw(g) }
        g.dispose()
        return image
    }

    companion object {
        fun read(file: File): VectorDrawable {
            val factory = DocumentBuilderFactory.newInstance().apply {
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                isNamespaceAware = false
            }
            val root = factory.newDocumentBuilder().parse(file).documentElement
            require(root.tagName == "vector") { "root tag is not <vector>" }
            val width = dimension(root.attr("width"), 24.0)
            val height = dimension(root.attr("height"), 24.0)
            val viewportWidth = number(root.attr("viewportWidth"), width)
            val viewportHeight = number(root.attr("viewportHeight"), height)
            return VectorDrawable(width, height, viewportWidth, viewportHeight, readChildren(root, AffineTransform()))
        }

        private fun readChildren(element: Element, transform: AffineTransform): List<DrawCommand> {
            val commands = mutableListOf<DrawCommand>()
            val nodes = element.childNodes
            for (i in 0 until nodes.length) {
                val child = nodes.item(i) as? Element ?: continue
                when (child.tagName) {
                    "group" -> {
                        val next = AffineTransform(transform)
                        val pivotX = number(child.attr("pivotX"), 0.0)
                        val pivotY = number(child.attr("pivotY"), 0.0)
                        next.translate(number(child.attr("translateX"), 0.0), number(child.attr("translateY"), 0.0))
                        next.rotate(Math.toRadians(number(child.attr("rotation"), 0.0)), pivotX, pivotY)
                        next.translate(pivotX, pivotY)
                        next.scale(number(child.attr("scaleX"), 1.0), number(child.attr("scaleY"), 1.0))
                        next.translate(-pivotX, -pivotY)
                        commands += readChildren(child, next)
                    }
                    "path" -> {
                        val pathData = child.attr("pathData")
                        if (!pathData.isNullOrBlank()) commands += DrawCommand.from(child, transform, PathData.parse(pathData))
                    }
                }
            }
            return commands
        }
    }
}

private data class DrawCommand(
    val shape: Shape,
    val fillColor: Color?,
    val strokeColor: Color?,
    val strokeWidth: Float,
    val cap: Int,
    val join: Int,
) {
    fun draw(g: Graphics2D) {
        fillColor?.let {
            g.color = it
            g.fill(shape)
        }
        if (strokeColor != null && strokeWidth > 0f) {
            g.color = strokeColor
            g.stroke = BasicStroke(strokeWidth, cap, join)
            g.draw(shape)
        }
    }

    companion object {
        fun from(element: Element, transform: AffineTransform, path: GeneralPath): DrawCommand {
            path.windingRule = if (element.attr("fillType") == "evenOdd") GeneralPath.WIND_EVEN_ODD else GeneralPath.WIND_NON_ZERO
            val cap = when (element.attr("strokeLineCap")) {
                "round" -> BasicStroke.CAP_ROUND
                "square" -> BasicStroke.CAP_SQUARE
                else -> BasicStroke.CAP_BUTT
            }
            val join = when (element.attr("strokeLineJoin")) {
                "round" -> BasicStroke.JOIN_ROUND
                "bevel" -> BasicStroke.JOIN_BEVEL
                else -> BasicStroke.JOIN_MITER
            }
            return DrawCommand(
                shape = transform.createTransformedShape(path),
                fillColor = parseColor(element.attr("fillColor"), number(element.attr("fillAlpha"), 1.0)),
                strokeColor = parseColor(element.attr("strokeColor"), number(element.attr("strokeAlpha"), 1.0)),
                strokeWidth = number(element.attr("strokeWidth"), 0.0).toFloat(),
                cap = cap,
                join = join,
            )
        }
    }
}

private object PathData {
    fun parse(data: String): GeneralPath {
        val parser = PathParser(data)
        val path = GeneralPath()
        var command = '\u0000'
        var x = 0.0
        var y = 0.0
        var startX = 0.0
        var startY = 0.0
        var lastCx = 0.0
        var lastCy = 0.0
        var lastCommand = '\u0000'

        while (parser.hasMore()) {
            if (parser.hasCommand()) command = parser.nextCommand()
            require(command != '\u0000') { "pathData starts without command" }
            val relative = command.isLowerCase()
            var upper = command.uppercaseChar()
            if (upper == 'Z') {
                path.closePath()
                x = startX
                y = startY
                lastCommand = command
                continue
            }

            var first = true
            while (parser.hasMore() && !parser.hasCommand()) {
                when (upper) {
                    'M' -> {
                        var nx = parser.number()
                        var ny = parser.number()
                        if (relative) {
                            nx += x
                            ny += y
                        }
                        if (first) {
                            path.moveTo(nx, ny)
                            startX = nx
                            startY = ny
                        } else {
                            path.lineTo(nx, ny)
                        }
                        x = nx
                        y = ny
                    }
                    'L' -> {
                        var nx = parser.number()
                        var ny = parser.number()
                        if (relative) {
                            nx += x
                            ny += y
                        }
                        path.lineTo(nx, ny)
                        x = nx
                        y = ny
                    }
                    'H' -> {
                        var nx = parser.number()
                        if (relative) nx += x
                        path.lineTo(nx, y)
                        x = nx
                    }
                    'V' -> {
                        var ny = parser.number()
                        if (relative) ny += y
                        path.lineTo(x, ny)
                        y = ny
                    }
                    'C' -> {
                        var x1 = parser.number(); var y1 = parser.number()
                        var x2 = parser.number(); var y2 = parser.number()
                        var nx = parser.number(); var ny = parser.number()
                        if (relative) {
                            x1 += x; y1 += y; x2 += x; y2 += y; nx += x; ny += y
                        }
                        path.curveTo(x1, y1, x2, y2, nx, ny)
                        lastCx = x2; lastCy = y2; x = nx; y = ny
                    }
                    'S' -> {
                        val x1 = if (lastCommand.uppercaseChar() in charArrayOf('C', 'S')) 2 * x - lastCx else x
                        val y1 = if (lastCommand.uppercaseChar() in charArrayOf('C', 'S')) 2 * y - lastCy else y
                        var x2 = parser.number(); var y2 = parser.number()
                        var nx = parser.number(); var ny = parser.number()
                        if (relative) {
                            x2 += x; y2 += y; nx += x; ny += y
                        }
                        path.curveTo(x1, y1, x2, y2, nx, ny)
                        lastCx = x2; lastCy = y2; x = nx; y = ny
                    }
                    'Q' -> {
                        var x1 = parser.number(); var y1 = parser.number()
                        var nx = parser.number(); var ny = parser.number()
                        if (relative) {
                            x1 += x; y1 += y; nx += x; ny += y
                        }
                        path.quadTo(x1, y1, nx, ny)
                        lastCx = x1; lastCy = y1; x = nx; y = ny
                    }
                    'T' -> {
                        val x1 = if (lastCommand.uppercaseChar() in charArrayOf('Q', 'T')) 2 * x - lastCx else x
                        val y1 = if (lastCommand.uppercaseChar() in charArrayOf('Q', 'T')) 2 * y - lastCy else y
                        var nx = parser.number(); var ny = parser.number()
                        if (relative) {
                            nx += x; ny += y
                        }
                        path.quadTo(x1, y1, nx, ny)
                        lastCx = x1; lastCy = y1; x = nx; y = ny
                    }
                    'A' -> {
                        val rx = parser.number()
                        val ry = parser.number()
                        val angle = parser.number()
                        val largeArc = parser.number() != 0.0
                        val sweep = parser.number() != 0.0
                        var nx = parser.number()
                        var ny = parser.number()
                        if (relative) {
                            nx += x; ny += y
                        }
                        appendArc(path, x, y, rx, ry, angle, largeArc, sweep, nx, ny)
                        x = nx; y = ny
                    }
                    else -> error("unsupported path command: $command")
                }
                first = false
                lastCommand = command
                parser.skipSeparators()
                if (upper == 'M') {
                    command = if (relative) 'l' else 'L'
                    upper = 'L'
                }
            }
        }
        return path
    }

    private fun appendArc(path: GeneralPath, x0: Double, y0: Double, rawRx: Double, rawRy: Double, angle: Double, largeArc: Boolean, sweep: Boolean, x: Double, y: Double) {
        var rx = abs(rawRx)
        var ry = abs(rawRy)
        if (rx == 0.0 || ry == 0.0 || (x0 == x && y0 == y)) {
            path.lineTo(x, y)
            return
        }
        val phi = Math.toRadians(angle)
        val cosPhi = cos(phi)
        val sinPhi = sin(phi)
        val dx = (x0 - x) / 2.0
        val dy = (y0 - y) / 2.0
        val x1p = cosPhi * dx + sinPhi * dy
        val y1p = -sinPhi * dx + cosPhi * dy
        val lambda = x1p * x1p / (rx * rx) + y1p * y1p / (ry * ry)
        if (lambda > 1.0) {
            val scale = sqrt(lambda)
            rx *= scale
            ry *= scale
        }
        val sign = if (largeArc == sweep) -1.0 else 1.0
        val numerator = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p
        val denominator = rx * rx * y1p * y1p + ry * ry * x1p * x1p
        val coef = sign * sqrt(max(0.0, numerator / denominator))
        val cxp = coef * (rx * y1p / ry)
        val cyp = coef * (-ry * x1p / rx)
        val cx = cosPhi * cxp - sinPhi * cyp + (x0 + x) / 2.0
        val cy = sinPhi * cxp + cosPhi * cyp + (y0 + y) / 2.0
        val theta1 = angleBetween(1.0, 0.0, (x1p - cxp) / rx, (y1p - cyp) / ry)
        var delta = angleBetween((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
        if (!sweep && delta > 0) delta -= Math.PI * 2 else if (sweep && delta < 0) delta += Math.PI * 2
        val segments = max(1, ceil(abs(delta) / (Math.PI / 2)).toInt())
        val step = delta / segments
        repeat(segments) { arcSegment(path, cx, cy, rx, ry, phi, theta1 + it * step, step) }
    }

    private fun angleBetween(ux: Double, uy: Double, vx: Double, vy: Double): Double {
        val dot = ux * vx + uy * vy
        val len = hypot(ux, uy) * hypot(vx, vy)
        val angle = acos(max(-1.0, min(1.0, dot / len)))
        return if (ux * vy - uy * vx < 0) -angle else angle
    }

    private fun arcSegment(path: GeneralPath, cx: Double, cy: Double, rx: Double, ry: Double, phi: Double, start: Double, sweep: Double) {
        val t = 4.0 / 3.0 * tan(sweep / 4.0)
        val x1 = cos(start)
        val y1 = sin(start)
        val x2 = cos(start + sweep)
        val y2 = sin(start + sweep)
        val p1 = mapArcPoint(cx, cy, rx, ry, phi, x1 - t * y1, y1 + t * x1)
        val p2 = mapArcPoint(cx, cy, rx, ry, phi, x2 + t * y2, y2 - t * x2)
        val p = mapArcPoint(cx, cy, rx, ry, phi, x2, y2)
        path.curveTo(p1[0], p1[1], p2[0], p2[1], p[0], p[1])
    }

    private fun mapArcPoint(cx: Double, cy: Double, rx: Double, ry: Double, phi: Double, x: Double, y: Double): DoubleArray {
        val cos = cos(phi)
        val sin = sin(phi)
        return doubleArrayOf(cx + rx * x * cos - ry * y * sin, cy + rx * x * sin + ry * y * cos)
    }
}

private class PathParser(private val data: String) {
    private var index = 0

    fun hasMore(): Boolean {
        skipSeparators()
        return index < data.length
    }

    fun hasCommand(): Boolean {
        skipSeparators()
        return index < data.length && data[index].isLetter()
    }

    fun nextCommand(): Char {
        skipSeparators()
        return data[index++]
    }

    fun number(): Double {
        skipSeparators()
        val start = index
        if (index < data.length && (data[index] == '-' || data[index] == '+')) index++
        while (index < data.length && data[index].isDigit()) index++
        if (index < data.length && data[index] == '.') {
            index++
            while (index < data.length && data[index].isDigit()) index++
        }
        if (index < data.length && (data[index] == 'e' || data[index] == 'E')) {
            index++
            if (index < data.length && (data[index] == '-' || data[index] == '+')) index++
            while (index < data.length && data[index].isDigit()) index++
        }
        require(start != index) { "number expected at pathData index $index" }
        return data.substring(start, index).toDouble()
    }

    fun skipSeparators() {
        while (index < data.length && (data[index].isWhitespace() || data[index] == ',')) index++
    }
}

private object ImageExporter {
    fun write(image: BufferedImage, file: File, format: String, alpha: Boolean) {
        when (format) {
            "jpg", "jpeg" -> writeJpeg(image, file)
            "webp" -> writeWebp(image, file, alpha)
            else -> ImageIO.write(image, "png", file)
        }
    }

    private fun writeJpeg(image: BufferedImage, file: File) {
        val rgb = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
        val g = rgb.createGraphics()
        g.color = Color.WHITE
        g.fillRect(0, 0, rgb.width, rgb.height)
        g.drawImage(image, 0, 0, null)
        g.dispose()
        ImageIO.write(rgb, "jpg", file)
    }

    private fun writeWebp(image: BufferedImage, file: File, alpha: Boolean) {
        val writers = ImageIO.getImageWritersByFormatName("webp")
        if (writers.hasNext()) {
            val writer: ImageWriter = writers.next()
            ImageIO.createImageOutputStream(file).use { out ->
                writer.output = out
                writer.write(null, IIOImage(image, null, null), writer.defaultWriteParam.apply {
                    if (canWriteCompressed()) {
                        compressionMode = ImageWriteParam.MODE_EXPLICIT
                        compressionQuality = 0.9f
                    }
                })
            }
            writer.dispose()
            return
        }

        val cwebp = findExecutable("cwebp") ?: error("WebP needs cwebp installed or an ImageIO WebP writer")
        val temp = Files.createTempFile("xml2image-", ".png").toFile()
        try {
            ImageIO.write(image, "png", temp)
            val command = buildList {
                add(cwebp.absolutePath)
                if (alpha) add("-lossless") else addAll(listOf("-q", "90"))
                add(temp.absolutePath)
                add("-o")
                add(file.absolutePath)
            }
            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            val output = ByteArrayOutputStream()
            process.inputStream.transferTo(output)
            check(process.waitFor() == 0) { "cwebp failed: ${output.toString().trim()}" }
        } finally {
            Files.deleteIfExists(temp.toPath())
        }
    }

    private fun findExecutable(name: String): File? {
        return System.getenv("PATH")
            ?.split(File.pathSeparator)
            ?.map { File(it, name) }
            ?.firstOrNull { it.isFile && it.canExecute() }
    }
}

private fun uniqueOutputFile(dir: File, base: String, format: String): File {
    var file = File(dir, "$base.$format")
    var index = 2
    while (file.exists()) {
        file = File(dir, "$base-$index.$format")
        index++
    }
    return file
}

private fun Element.attr(localName: String): String? {
    return when {
        hasAttribute(localName) -> getAttribute(localName)
        hasAttribute("android:$localName") -> getAttribute("android:$localName")
        else -> null
    }
}

private fun number(value: String?, fallback: Double): Double {
    if (value.isNullOrBlank()) return fallback
    return value.trim()
        .removeSuffix("dp")
        .removeSuffix("dip")
        .removeSuffix("px")
        .toDoubleOrNull() ?: fallback
}

private fun dimension(value: String?, fallback: Double): Double = number(value, fallback)

private fun parseColor(value: String?, alphaMultiplier: Double): Color? {
    if (value.isNullOrBlank() || value == "@android:color/transparent") return null
    var hex = value.trim()
    if (!hex.startsWith("#")) return null
    hex = hex.removePrefix("#")
    val (a, r, g, b) = when (hex.length) {
        3 -> listOf("ff", hex[0].toString().repeat(2), hex[1].toString().repeat(2), hex[2].toString().repeat(2))
        4 -> listOf(hex[0].toString().repeat(2), hex[1].toString().repeat(2), hex[2].toString().repeat(2), hex[3].toString().repeat(2))
        6 -> listOf("ff", hex.substring(0, 2), hex.substring(2, 4), hex.substring(4, 6))
        8 -> listOf(hex.substring(0, 2), hex.substring(2, 4), hex.substring(4, 6), hex.substring(6, 8))
        else -> return null
    }.map { it.toInt(16) }
    val alpha = (a * alphaMultiplier).roundToInt().coerceIn(0, 255)
    return Color(r, g, b, alpha)
}
