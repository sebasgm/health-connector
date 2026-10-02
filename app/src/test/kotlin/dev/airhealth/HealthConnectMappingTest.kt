package dev.airhealth

import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Percentage
import dev.airhealth.hc.originLabel
import dev.airhealth.hc.sleepStageName
import dev.airhealth.hc.toSample
import dev.airhealth.hc.toSamples
import dev.airhealth.hc.toSleepSession
import dev.airhealth.model.Cadence
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Health Connect mapping, exercised without a device.
 *
 * Records are plain data classes, so they can be built here and run through the real
 * mappers. This is the path that actually returns data for a Fitbit Air today, which
 * makes these the tests that matter most.
 */
class HealthConnectMappingTest {

    private val t0 = Instant.parse("2026-10-01T08:00:00Z")
    private val air = Device(manufacturer = "Google", model = "Fitbit Air", type = Device.TYPE_WATCH)
    private fun meta() = Metadata.autoRecorded(device = air)

    private fun hrRecord(from: Instant, stepSeconds: Long, count: Int): HeartRateRecord {
        val samples = (0 until count).map {
            HeartRateRecord.Sample(
                time = from.plusSeconds(it * stepSeconds),
                beatsPerMinute = 60L + it,
            )
        }
        return HeartRateRecord(
            startTime = samples.first().time,
            startZoneOffset = null,
            endTime = samples.last().time.plusSeconds(1),
            endZoneOffset = null,
            samples = samples,
            metadata = meta(),
        )
    }

    // -- the big one ------------------------------------------------------

    @Test
    fun `one heart rate record flattens to all of its samples`() {
        // The trap: a record is a container. Mapping it to a single reading would
        // throw away 59 of these 60 values and make the cadence look 60x coarser.
        val samples = hrRecord(t0, stepSeconds = 1, count = 60).toSamples()

        assertEquals(60, samples.size)
        assertEquals(t0, samples.first().time)
        assertEquals(60.0, samples.first().value)
        assertEquals(t0.plusSeconds(59), samples.last().time)
        assertEquals(119.0, samples.last().value)
    }

    @Test
    fun `samples keep their own instants rather than the record's span`() {
        val record = hrRecord(t0, stepSeconds = 5, count = 4)
        val times = record.toSamples().map { it.time }
        assertEquals(
            listOf(t0, t0.plusSeconds(5), t0.plusSeconds(10), t0.plusSeconds(15)),
            times,
        )
    }

    @Test
    fun `cadence across several records reflects the samples, not the record count`() {
        // Three records, each a minute of per-second data: 180 samples at 1s, not
        // 3 samples a minute apart.
        val records = listOf(
            hrRecord(t0, 1, 60),
            hrRecord(t0.plusSeconds(60), 1, 60),
            hrRecord(t0.plusSeconds(120), 1, 60),
        )
        val samples = records.flatMap { it.toSamples() }.sortedBy { it.time }
        val cadence = Cadence.of(samples)

        assertEquals(180, cadence.sampleCount)
        assertEquals(Duration.ofSeconds(1), cadence.mode)
        assertEquals(0, cadence.gapCount)
    }

    @Test
    fun `an empty record maps to no samples rather than failing`() {
        val record = HeartRateRecord(
            startTime = t0,
            startZoneOffset = null,
            endTime = t0.plusSeconds(60),
            endZoneOffset = null,
            samples = emptyList(),
            metadata = meta(),
        )
        assertTrue(record.toSamples().isEmpty())
    }

    // -- unit conventions -------------------------------------------------

    @Test
    fun `SpO2 is already a percentage here, unlike the cloud API's fraction`() {
        val record = OxygenSaturationRecord(
            time = t0,
            zoneOffset = null,
            percentage = Percentage(96.8),
            metadata = meta(),
        )
        // 96.8, not 0.968: the two sources disagree about this and each converts at
        // its own boundary.
        assertEquals(96.8, record.toSample().value)
    }

    @Test
    fun `HRV maps RMSSD milliseconds straight through`() {
        val record = HeartRateVariabilityRmssdRecord(
            time = t0,
            zoneOffset = null,
            heartRateVariabilityMillis = 42.5,
            metadata = meta(),
        )
        assertEquals(42.5, record.toSample().value)
    }

    @Test
    fun `steps are timestamped at the interval start`() {
        val record = StepsRecord(
            startTime = t0,
            startZoneOffset = null,
            endTime = t0.plusSeconds(60),
            endZoneOffset = null,
            count = 47,
            metadata = meta(),
        )
        val sample = record.toSample()
        assertEquals(t0, sample.time)
        assertEquals(47.0, sample.value)
    }

    // -- sleep ------------------------------------------------------------

    @Test
    fun `sleep stages are named and keep their spans`() {
        val start = Instant.parse("2026-09-30T23:40:00Z")
        val end = Instant.parse("2026-10-01T07:12:00Z")
        val record = SleepSessionRecord(
            startTime = start,
            startZoneOffset = null,
            endTime = end,
            endZoneOffset = null,
            stages = listOf(
                SleepSessionRecord.Stage(
                    startTime = start,
                    endTime = start.plusSeconds(900),
                    stage = SleepSessionRecord.STAGE_TYPE_LIGHT,
                ),
                SleepSessionRecord.Stage(
                    startTime = start.plusSeconds(900),
                    endTime = start.plusSeconds(3600),
                    stage = SleepSessionRecord.STAGE_TYPE_DEEP,
                ),
            ),
            metadata = meta(),
        )

        val session = record.toSleepSession()
        assertEquals(start, session.start)
        assertEquals(end, session.end)
        assertEquals(listOf("LIGHT", "DEEP"), session.stages.map { it.name })
        assertEquals(start.plusSeconds(900), session.stages[1].start)
    }

    @Test
    fun `an unrecognised stage code degrades to UNKNOWN instead of throwing`() {
        // A provider that adds a stage must not take the whole night down.
        assertEquals("UNKNOWN", sleepStageName(9999))
        assertEquals("REM", sleepStageName(SleepSessionRecord.STAGE_TYPE_REM))
    }

    // -- provenance -------------------------------------------------------

    @Test
    fun `the writing device is surfaced, because Health Connect is a shared store`() {
        // No server-side "wearables only" filter exists on this path, so the only way
        // to spot a phone pedometer double-counting is to show who wrote the row.
        assertEquals("Fitbit Air", hrRecord(t0, 1, 1).toSamples().single().device)
    }

    @Test
    fun `a record with no device still produces a usable label`() {
        val record = HeartRateRecord(
            startTime = t0,
            startZoneOffset = null,
            endTime = t0.plusSeconds(1),
            endZoneOffset = null,
            samples = listOf(HeartRateRecord.Sample(t0, 70L)),
            metadata = Metadata.manualEntry(),
        )
        assertEquals("unknown", record.originLabel())
    }
}
