package dev.airhealth

import dev.airhealth.export.ExportData
import dev.airhealth.export.ExportFormat
import dev.airhealth.export.Exporter
import dev.airhealth.model.Cadence
import dev.airhealth.model.Sample
import dev.airhealth.model.SampleStats
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The text exporters.
 *
 * The locale tests are the point of this file. The app has a Spanish mode, and under a
 * Spanish locale `"%.1f".format(72.5)` is `"72,5"` — a comma decimal separator inside a
 * comma-separated file shifts every later column, and `JSON.parse` rejects it outright.
 * Each test here runs with the default locale set to Spanish so a regression shows up
 * as a failure rather than as a corrupt file on someone's phone.
 */
class ExporterTest {

    private lateinit var original: Locale

    @BeforeEach
    fun useSpanishLocale() {
        original = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("es-ES"))
    }

    @AfterEach
    fun restoreLocale() {
        Locale.setDefault(original)
    }

    private val t0 = Instant.parse("2026-10-01T08:00:00Z")

    private fun data(
        samples: List<Sample> = listOf(
            Sample(t0, 72.5, device = "Fitbit Air"),
            Sample(t0.plusSeconds(1), 73.0, device = "Fitbit Air"),
        ),
        metric: String = "Frecuencia cardíaca",
        unit: String = "bpm",
        decimals: Int = 1,
    ) = ExportData(
        metric = metric,
        unit = unit,
        decimals = decimals,
        samples = samples,
        stats = SampleStats.of(samples),
        cadence = Cadence.of(samples),
        generatedAt = t0.plusSeconds(60),
        zone = ZoneId.of("UTC"),
    )

    // -- the locale trap --------------------------------------------------

    @Test
    fun `CSV writes a dot decimal even under a Spanish locale`() {
        val csv = Exporter.toCsv(data())
        assertTrue(csv.contains("72.5"), csv)
        assertFalse(csv.contains("72,5"), "comma decimal would shift every later column")
    }

    @Test
    fun `JSON writes a dot decimal even under a Spanish locale`() {
        val json = Exporter.toJson(data())
        assertTrue(json.contains("72.5"), json)
        assertFalse(json.contains("72,5"), "JSON.parse rejects a comma decimal")
    }

    @Test
    fun `HTML writes a dot decimal even under a Spanish locale`() {
        assertFalse(Exporter.toHtml(data()).contains("72,5"))
    }

    // -- CSV --------------------------------------------------------------

    @Test
    fun `CSV has a header and one line per sample`() {
        val lines = Exporter.toCsv(data()).trim().lines()
        assertEquals("timestamp_utc,time_local,metric,value,unit,source", lines.first())
        assertEquals(3, lines.size)
        assertTrue(lines[1].startsWith("2026-10-01T08:00:00Z,2026-10-01 08:00:00,"), lines[1])
    }

    @Test
    fun `a metric name containing a comma is quoted`() {
        // "Frecuencia cardíaca" is safe, but a localized name is not guaranteed to be.
        val csv = Exporter.toCsv(data(metric = "Pasos, totales"))
        assertTrue(csv.contains("\"Pasos, totales\""), csv)
    }

    @Test
    fun `an inner quote is doubled rather than escaped with a backslash`() {
        assertEquals("\"say \"\"hi\"\"\"", Exporter.csvField("say \"hi\""))
    }

    @Test
    fun `a field needing no quoting is left bare`() {
        assertEquals("bpm", Exporter.csvField("bpm"))
    }

    @Test
    fun `integer metrics carry no decimal point`() {
        val steps = data(
            samples = listOf(Sample(t0, 47.0)),
            metric = "Pasos", unit = "", decimals = 0,
        )
        val row = Exporter.toCsv(steps).trim().lines()[1]
        assertTrue(row.contains(",47,"), row)
    }

    // -- JSON -------------------------------------------------------------

    @Test
    fun `JSON carries the summary, the cadence and every sample`() {
        val json = Exporter.toJson(data())
        assertTrue(json.contains("\"sampleCount\": 2"), json)
        assertTrue(json.contains("\"summary\""))
        assertTrue(json.contains("\"cadence\""))
        assertTrue(json.contains("\"medianSeconds\": 1"))
        assertEquals(2, Regex("\"t\":").findAll(json).count())
    }

    @Test
    fun `JSON braces and brackets balance`() {
        val json = Exporter.toJson(data())
        assertEquals(json.count { it == '{' }, json.count { it == '}' })
        assertEquals(json.count { it == '[' }, json.count { it == ']' })
    }

    @Test
    fun `JSON escapes a quote in a string`() {
        assertEquals("\"a\\\"b\"", Exporter.jsonString("a\"b"))
    }

    @Test
    fun `JSON omits the summary when there is nothing to summarise`() {
        val empty = data(samples = emptyList())
        val json = Exporter.toJson(empty)
        assertFalse(json.contains("\"summary\""))
        assertTrue(json.contains("\"samples\": ["), json)
    }

    // -- HTML -------------------------------------------------------------

    @Test
    fun `HTML is self-contained with no external asset`() {
        val html = Exporter.toHtml(data())
        assertTrue(html.startsWith("<!doctype html>"))
        assertFalse(html.contains("http://"))
        assertFalse(html.contains("https://"))
        assertFalse(html.contains("<script"))
    }

    @Test
    fun `HTML escapes angle brackets in a metric name`() {
        assertTrue(Exporter.toHtml(data(metric = "a<b")).contains("a&lt;b"))
    }

    @Test
    fun `HTML has one table row per sample`() {
        val html = Exporter.toHtml(data())
        assertEquals(2, Regex("<tr><td>").findAll(html).count())
    }

    // -- filenames and the PDF cap ---------------------------------------

    @Test
    fun `filename is slugged, dated and extensioned`() {
        assertEquals(
            "frecuencia-cardíaca-2026-10-01.csv".replace("í", "-"),
            Exporter.filename(data(), ExportFormat.CSV),
        )
    }

    @Test
    fun `filename never ends up empty`() {
        assertEquals("samples-2026-10-01.json", Exporter.filename(data(metric = "!!!"), ExportFormat.JSON))
    }

    @Test
    fun `the PDF row list is capped while the other formats are not`() {
        val many = (0 until ExportFormat.PDF_ROW_CAP + 500).map {
            Sample(t0.plusSeconds(it.toLong()), 60.0 + (it % 20))
        }
        val d = data(samples = many)
        assertEquals(ExportFormat.PDF_ROW_CAP, Exporter.pdfLines(d).size)
        // CSV keeps all of them: header plus every row.
        assertEquals(many.size + 1, Exporter.toCsv(d).trim().lines().size)
    }
}

/**
 * On-screen formatting, which is pinned to a dot as well.
 *
 * Separate class so it keeps its own locale fixture: these assertions are about what
 * the user reads on the screen, not about what lands in a file, and the two are only
 * worth testing together because they must now agree.
 */
class ScreenFormatTest {

    private lateinit var original: Locale

    @BeforeEach
    fun useSpanishLocale() {
        original = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("es-ES"))
    }

    @AfterEach
    fun restoreLocale() {
        Locale.setDefault(original)
    }

    @Test
    fun `a decimal stays a dot under a Spanish locale`() {
        assertEquals("72.5", dev.airhealth.ui.formatValue(72.5, 1))
        assertEquals("96.8", dev.airhealth.ui.formatValue(96.8, 1))
    }

    @Test
    fun `an integer metric shows no decimal point at all`() {
        assertEquals("47", dev.airhealth.ui.formatValue(47.0, 0))
        assertEquals("134", dev.airhealth.ui.formatValue(134.4, 0))
    }

    @Test
    fun `the screen and the export agree on the same value`() {
        // The whole reason the screen is pinned too: one of these reading "72,5" while
        // the other reads "72.5" would be a difference with no purpose.
        assertEquals(
            Exporter.number(72.5, 1),
            dev.airhealth.ui.formatValue(72.5, 1),
        )
    }

    @Test
    fun `rounding is applied, not truncation`() {
        assertEquals("72.6", dev.airhealth.ui.formatValue(72.55, 1))
    }
}
