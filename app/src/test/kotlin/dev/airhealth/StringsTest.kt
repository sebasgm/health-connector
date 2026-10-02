package dev.airhealth

import dev.airhealth.i18n.Lang
import dev.airhealth.i18n.Strings
import dev.airhealth.model.Metric
import dev.airhealth.model.Sample
import dev.airhealth.model.Cadence
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Completeness of the two language bundles.
 *
 * This is the pay-off for keeping the strings as Kotlin data rather than a resource
 * bundle: a missing or untranslated entry is a test failure here, whereas a missing
 * `values-es` entry silently falls back to English at runtime.
 */
class StringsTest {

    private val bundles = listOf(Strings.English, Strings.Spanish)

    @Test
    fun `every plain string is non-blank in both languages`() {
        // Java reflection, not `Strings::class.members`: that needs kotlin-reflect on
        // the classpath, which is a dependency this does not justify. A data class's
        // properties are plain fields, so declaredFields finds all of them.
        val textFields = Strings::class.java.declaredFields
            .filter { it.type == String::class.java }
        assertTrue(textFields.size > 15, "expected many text fields, found ${textFields.size}")

        for (bundle in bundles) {
            for (field in textFields) {
                field.isAccessible = true
                val value = field.get(bundle) as String?
                assertTrue(
                    value != null && value.isNotBlank(),
                    "${bundle.lang}: ${field.name} is blank",
                )
            }
        }
    }

    @Test
    fun `Spanish actually differs from English`() {
        // Guards against a bundle copied and left untranslated.
        assertNotEquals(Strings.English.heartRate, Strings.Spanish.heartRate)
        assertNotEquals(Strings.English.export, Strings.Spanish.export)
        assertNotEquals(Strings.English.tryAgain, Strings.Spanish.tryAgain)
        assertNotEquals(Strings.English.nothingWrittenTitle, Strings.Spanish.nothingWrittenTitle)
    }

    @Test
    fun `every metric has a label in both languages`() {
        for (bundle in bundles) {
            for (metric in Metric.entries) {
                assertTrue(bundle.label(metric).isNotBlank(), "${bundle.lang}: $metric")
            }
        }
    }

    @Test
    fun `the counted strings produce text in both languages`() {
        for (bundle in bundles) {
            assertTrue(bundle.cadenceNotEnough(1).isNotBlank())
            assertTrue(bundle.permissionNeededBody(5).isNotBlank())
            assertTrue(bundle.newestFirst(120, 6000).isNotBlank())
            assertTrue(bundle.exportDone("CSV").isNotBlank())
            assertTrue(bundle.nothingWrittenBody(bundle.heartRate).isNotBlank())
        }
    }

    @Test
    fun `singular and plural differ where the language needs them to`() {
        assertTrue(Strings.English.cadenceNotEnough(1).contains("sample "))
        assertTrue(Strings.English.cadenceNotEnough(2).contains("samples"))
        assertTrue(Strings.Spanish.cadenceNotEnough(1).contains("muestra "))
        assertTrue(Strings.Spanish.cadenceNotEnough(2).contains("muestras"))
    }

    @Test
    fun `the cadence sentence reports the measured figures`() {
        val t0 = Instant.parse("2026-10-01T08:00:00Z")
        val samples = (0 until 30).map { Sample(t0.plusSeconds(it * 5L), 60.0) }
        val cadence = Cadence.of(samples)

        val en = Strings.English.cadence(cadence)
        assertTrue(en.contains("30"), en)
        assertTrue(en.contains("every 5s"), en)

        val es = Strings.Spanish.cadence(cadence)
        assertTrue(es.contains("30"), es)
        assertTrue(es.contains("cada 5s"), es)
        assertFalse(es.contains("every"), "English leaked into the Spanish bundle")
    }

    @Test
    fun `language is resolved from a code and falls back rather than throwing`() {
        assertEquals(Lang.ES, Lang.ofCode("es"))
        assertEquals(Lang.EN, Lang.ofCode("en"))
        // An unknown code must not crash; it falls back to the system language.
        assertTrue(Lang.ofCode("klingon") in Lang.entries)
        assertTrue(Lang.ofCode(null) in Lang.entries)
    }
}
