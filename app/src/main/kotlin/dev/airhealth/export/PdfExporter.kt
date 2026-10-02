package dev.airhealth.export

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Renders [ExportData] as a PDF using the platform's own [PdfDocument].
 *
 * No dependency and no WebView: the document is a summary block and a two-column
 * table, which `Canvas.drawText` handles directly. A WebView print would mean waiting
 * on a page load and a print adapter for a layout this simple.
 *
 * Page geometry is A4 at 72 dpi (595 × 842 points), which is what `PdfDocument`'s
 * canvas units are, so no scaling is involved.
 */
object PdfExporter {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 42f
    private const val LINE = 14f

    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)

    fun render(data: ExportData): ByteArray {
        val document = PdfDocument()

        val title = Paint().apply { textSize = 18f; isFakeBoldText = true; isAntiAlias = true }
        val body = Paint().apply { textSize = 10f; isAntiAlias = true }
        val bodyBold = Paint().apply { textSize = 10f; isFakeBoldText = true; isAntiAlias = true }
        val muted = Paint().apply { textSize = 9f; color = 0xFF6B7280.toInt(); isAntiAlias = true }
        val rule = Paint().apply { color = 0xFFD5D9E0.toInt(); strokeWidth = 0.7f }

        val rows = Exporter.pdfLines(data)
        val truncated = data.samples.size > rows.size

        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas = page.canvas
        var y = MARGIN + 10f

        // -- header ------------------------------------------------------
        canvas.drawText(data.metric, MARGIN, y, title)
        y += LINE + 6f
        canvas.drawText(
            "${data.samples.size} samples · generated ${stamp.format(data.generatedAt.atZone(data.zone))} (${data.zone.id})",
            MARGIN, y, muted,
        )
        y += LINE + 6f

        val unit = if (data.unit.isBlank()) "" else " ${data.unit}"
        data.stats?.let { s ->
            canvas.drawText(
                "min ${Exporter.number(s.min, data.decimals)}$unit" +
                    "   mean ${Exporter.number(s.mean, 2)}$unit" +
                    "   max ${Exporter.number(s.max, data.decimals)}$unit",
                MARGIN, y, body,
            )
            y += LINE
        }
        data.cadence?.let { c ->
            val typical = c.mode?.let { "${it.seconds}s" } ?: "—"
            val median = c.median?.let { "${it.seconds}s" } ?: "—"
            canvas.drawText(
                "typical interval $typical   median $median   gaps ${c.gapCount}",
                MARGIN, y, body,
            )
            y += LINE
        }
        if (truncated) {
            y += 2f
            canvas.drawText(
                "Table shows the first ${rows.size} of ${data.samples.size} rows. " +
                    "Export CSV or JSON for the complete series.",
                MARGIN, y, muted,
            )
            y += LINE
        }

        y += 8f
        canvas.drawText("Time", MARGIN, y, bodyBold)
        canvas.drawText("Value", MARGIN + 180f, y, bodyBold)
        y += 4f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, rule)
        y += LINE

        // -- table, paginated -------------------------------------------
        for ((time, value) in rows) {
            if (y > PAGE_HEIGHT - MARGIN) {
                canvas.drawText("Page $pageNumber", MARGIN, PAGE_HEIGHT - MARGIN + 14f, muted)
                document.finishPage(page)

                pageNumber++
                page = document.startPage(
                    PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                )
                canvas = page.canvas
                y = MARGIN + 10f
                // Repeat the header on every page; a table whose columns are only
                // labelled on page one is unreadable once printed.
                canvas.drawText("Time", MARGIN, y, bodyBold)
                canvas.drawText("Value", MARGIN + 180f, y, bodyBold)
                y += 4f
                canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, rule)
                y += LINE
            }
            canvas.drawText(time, MARGIN, y, body)
            canvas.drawText(value, MARGIN + 180f, y, body)
            y += LINE
        }

        canvas.drawText("Page $pageNumber", MARGIN, PAGE_HEIGHT - MARGIN + 14f, muted)
        document.finishPage(page)

        val out = ByteArrayOutputStream()
        document.writeTo(out)
        document.close()
        return out.toByteArray()
    }
}
