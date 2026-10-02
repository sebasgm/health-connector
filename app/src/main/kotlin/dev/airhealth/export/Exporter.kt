package dev.airhealth.export

import dev.airhealth.model.Cadence
import dev.airhealth.model.Sample
import dev.airhealth.model.SampleStats
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * What is being exported: the rows plus everything needed to describe them.
 *
 * Passed as one object so each format renders the same facts and they cannot drift.
 */
data class ExportData(
    val metric: String,
    val unit: String,
    val decimals: Int,
    val samples: List<Sample>,
    val stats: SampleStats?,
    val cadence: Cadence?,
    val generatedAt: Instant,
    val zone: ZoneId,
)

/**
 * Renders [ExportData] as text.
 *
 * ## Why every number goes through [Locale.ROOT]
 *
 * `"%.1f".format(72.5)` yields `"72,5"` under a Spanish locale, and this app has a
 * Spanish mode. A comma decimal separator inside a comma-separated file silently
 * shifts every column after it, and `JSON.parse` rejects `72,5` outright. So numbers
 * destined for a file are formatted against a fixed locale, never the device's — the
 * on-screen numbers are the only place the user's locale should show through.
 *
 * Timestamps are written twice for the same reason: a machine-readable UTC instant
 * that nothing can misread, and a local wall-clock string for a human skimming the
 * file.
 */
object Exporter {

    private val localFormat: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)

    /** A decimal with a dot, whatever the device's language is set to. */
    fun number(value: Double, decimals: Int): String =
        String.format(Locale.ROOT, "%.${decimals}f", value)

    fun filename(data: ExportData, format: ExportFormat): String {
        val slug = data.metric.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifEmpty { "samples" }
        val day = data.generatedAt.atZone(data.zone).toLocalDate()
        return "$slug-$day.${format.extension}"
    }

    // -- CSV ---------------------------------------------------------------

    /**
     * One row per sample, with a header.
     *
     * `source` is kept here even though the screen no longer shows it: in a file that
     * will be analysed, which device wrote a value is the difference between a real
     * trend and two devices double-counting.
     */
    fun toCsv(data: ExportData): String = buildString {
        appendLine("timestamp_utc,time_local,metric,value,unit,source")
        val zone = data.zone
        for (sample in data.samples) {
            append(sample.time)
            append(',')
            append(localFormat.format(sample.time.atZone(zone)))
            append(',')
            append(csvField(data.metric))
            append(',')
            append(number(sample.value, data.decimals))
            append(',')
            append(csvField(data.unit))
            append(',')
            append(csvField(sample.device ?: ""))
            append('\n')
        }
    }

    /** Quotes only when it has to, and doubles any inner quote. */
    internal fun csvField(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    // -- JSON --------------------------------------------------------------

    /**
     * Hand-rolled rather than serialized.
     *
     * Removing the cloud path took kotlinx-serialization with it, and adding a
     * dependency back for one flat document would be a poor trade. The only escaping
     * this needs is for strings the app itself produced.
     */
    fun toJson(data: ExportData): String = buildString {
        appendLine("{")
        appendLine("""  "metric": ${jsonString(data.metric)},""")
        appendLine("""  "unit": ${jsonString(data.unit)},""")
        appendLine("""  "generatedAt": ${jsonString(data.generatedAt.toString())},""")
        appendLine("""  "timeZone": ${jsonString(data.zone.id)},""")
        appendLine("""  "sampleCount": ${data.samples.size},""")

        data.stats?.let { s ->
            appendLine("""  "summary": {""")
            appendLine("""    "min": ${number(s.min, data.decimals)},""")
            appendLine("""    "mean": ${number(s.mean, 2)},""")
            appendLine("""    "max": ${number(s.max, data.decimals)}""")
            appendLine("  },")
        }

        data.cadence?.let { c ->
            appendLine("""  "cadence": {""")
            appendLine("""      "modeSeconds": ${c.mode?.seconds ?: "null"},""")
            appendLine("""      "medianSeconds": ${c.median?.seconds ?: "null"},""")
            appendLine("""      "longestGapSeconds": ${c.longest?.seconds ?: "null"},""")
            appendLine("""      "gapCount": ${c.gapCount}""")
            appendLine("  },")
        }

        appendLine("""  "samples": [""")
        data.samples.forEachIndexed { index, sample ->
            append("    {")
            append(""""t": ${jsonString(sample.time.toString())}, """)
            append(""""v": ${number(sample.value, data.decimals)}""")
            sample.device?.takeIf { it.isNotBlank() }?.let {
                append(""", "source": ${jsonString(it)}""")
            }
            append("}")
            if (index != data.samples.lastIndex) append(",")
            append('\n')
        }
        appendLine("  ]")
        append("}")
    }

    internal fun jsonString(value: String): String = buildString {
        append('"')
        for (ch in value) {
            when (ch) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (ch < ' ') append("\\u%04x".format(Locale.ROOT, ch.code)) else append(ch)
            }
        }
        append('"')
    }

    // -- HTML --------------------------------------------------------------

    /**
     * A self-contained page: inline CSS, no scripts, no external assets.
     *
     * It has to open from a file:// URL on a phone with no network, so anything
     * fetched would simply be missing.
     */
    fun toHtml(data: ExportData): String = buildString {
        val unit = if (data.unit.isBlank()) "" else " ${escapeHtml(data.unit)}"
        appendLine("<!doctype html>")
        appendLine("""<html lang="en"><head><meta charset="utf-8">""")
        appendLine("""<meta name="viewport" content="width=device-width,initial-scale=1">""")
        appendLine("<title>${escapeHtml(data.metric)} — ${data.generatedAt.atZone(data.zone).toLocalDate()}</title>")
        appendLine(
            "<style>" +
                "body{font:14px/1.5 system-ui,sans-serif;margin:0;padding:24px 16px;" +
                "color:#11151b;background:#fff}" +
                "h1{font-size:22px;margin:0 0 4px}" +
                "p.meta{color:#4d5663;margin:0 0 20px;font-size:13px}" +
                "dl{display:grid;grid-template-columns:auto auto;gap:4px 18px;margin:0 0 22px;" +
                "font-variant-numeric:tabular-nums}" +
                "dt{color:#4d5663}dd{margin:0;font-weight:600}" +
                "table{border-collapse:collapse;width:100%;max-width:620px;" +
                "font-variant-numeric:tabular-nums}" +
                "th,td{text-align:left;padding:6px 10px 6px 0;border-bottom:1px solid #e3e6ea}" +
                "th{font-size:11px;text-transform:uppercase;letter-spacing:.07em;color:#78818f}" +
                "td.v{font-weight:600}" +
                "@media(prefers-color-scheme:dark){body{background:#101318;color:#f3f5f8}" +
                "p.meta,dt,th{color:#adb6c2}th,td{border-bottom-color:#2b313a}}" +
                "</style>"
        )
        appendLine("</head><body>")
        appendLine("<h1>${escapeHtml(data.metric)}</h1>")
        appendLine(
            """<p class="meta">${data.samples.size} samples · """ +
                "generated ${localFormat.format(data.generatedAt.atZone(data.zone))} " +
                "(${escapeHtml(data.zone.id)})</p>"
        )

        if (data.stats != null || data.cadence != null) {
            appendLine("<dl>")
            data.stats?.let { s ->
                appendLine("<dt>Minimum</dt><dd>${number(s.min, data.decimals)}$unit</dd>")
                appendLine("<dt>Mean</dt><dd>${number(s.mean, 2)}$unit</dd>")
                appendLine("<dt>Maximum</dt><dd>${number(s.max, data.decimals)}$unit</dd>")
            }
            data.cadence?.let { c ->
                c.mode?.let { appendLine("<dt>Typical interval</dt><dd>${it.seconds}s</dd>") }
                c.median?.let { appendLine("<dt>Median interval</dt><dd>${it.seconds}s</dd>") }
                appendLine("<dt>Gaps</dt><dd>${c.gapCount}</dd>")
            }
            appendLine("</dl>")
        }

        appendLine("<table><thead><tr><th>Time</th><th>Value</th></tr></thead><tbody>")
        for (sample in data.samples) {
            appendLine(
                "<tr><td>${localFormat.format(sample.time.atZone(data.zone))}</td>" +
                    """<td class="v">${number(sample.value, data.decimals)}$unit</td></tr>"""
            )
        }
        appendLine("</tbody></table>")
        append("</body></html>")
    }

    internal fun escapeHtml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    /** The lines the PDF renderer prints. Here so its layout holds no formatting logic. */
    fun pdfLines(data: ExportData): List<Pair<String, String>> =
        data.samples.take(ExportFormat.PDF_ROW_CAP).map { sample ->
            localFormat.format(sample.time.atZone(data.zone)) to
                number(sample.value, data.decimals) +
                if (data.unit.isBlank()) "" else " ${data.unit}"
        }
}
