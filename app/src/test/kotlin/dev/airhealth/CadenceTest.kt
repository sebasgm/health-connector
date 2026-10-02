package dev.airhealth

import dev.airhealth.i18n.Strings
import dev.airhealth.model.Cadence
import dev.airhealth.model.Sample
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Cadence is how the app answers "does the Air actually write per-second data?"
 * without taking the documented storage resolution on trust.
 */
class CadenceTest {

    private val t0 = Instant.parse("2026-10-01T08:00:00Z")

    private fun series(stepSeconds: Long, count: Int, from: Instant = t0) =
        (0 until count).map { Sample(from.plusSeconds(it * stepSeconds), 60.0 + it) }

    @Test
    fun `per-second data reports a one second mode`() {
        val cadence = Cadence.of(series(stepSeconds = 1, count = 120))
        assertEquals(Duration.ofSeconds(1), cadence.mode)
        assertEquals(Duration.ofSeconds(1), cadence.median)
        assertEquals(120, cadence.sampleCount)
        assertEquals(0, cadence.gapCount)
    }

    @Test
    fun `five second data is not mistaken for per-second data`() {
        val cadence = Cadence.of(series(stepSeconds = 5, count = 60))
        assertEquals(Duration.ofSeconds(5), cadence.mode)
        assertTrue(Strings.English.cadence(cadence).contains("every 5s"))
        assertTrue(Strings.Spanish.cadence(cadence).contains("cada 5s"))
    }

    @Test
    fun `an idle stretch is counted as a gap, not folded into the median`() {
        // Ten minutes of per-second data, an hour of nothing, then more.
        val dense = series(stepSeconds = 1, count = 600)
        // The last dense sample sits at t0+599, so the resumption has to be offset
        // from there for the gap to be exactly an hour.
        val afterGap = series(stepSeconds = 1, count = 600, from = t0.plusSeconds(599 + 3600))
        val cadence = Cadence.of(dense + afterGap)

        assertEquals(Duration.ofSeconds(1), cadence.median)
        assertEquals(1, cadence.gapCount)
        assertEquals(Duration.ofSeconds(3600), cadence.longest)
    }

    @Test
    fun `jitter does not shatter the mode into one bucket per gap`() {
        // Real devices do not land exactly on the second.
        val times = listOf(0L, 1020L, 1980L, 3010L, 3990L, 5050L)
        val samples = times.map { Sample(t0.plusMillis(it), 70.0) }
        val cadence = Cadence.of(samples)
        assertEquals(Duration.ofSeconds(1), cadence.mode)
    }

    @Test
    fun `one sample cannot imply a cadence and says so`() {
        val cadence = Cadence.of(listOf(Sample(t0, 70.0)))
        assertNull(cadence.median)
        assertTrue(Strings.English.cadence(cadence).contains("not enough"))
    }

    @Test
    fun `unsorted input is handled`() {
        val cadence = Cadence.of(series(stepSeconds = 2, count = 50).reversed())
        assertEquals(Duration.ofSeconds(2), cadence.mode)
        assertEquals(0, cadence.gapCount)
    }
}
