package dev.airhealth.hc

import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import dev.airhealth.model.Sample
import dev.airhealth.model.SleepSession

/**
 * Health Connect records to this app's model.
 *
 * Separate from [HealthConnectSource] so the mapping can be tested on the JVM: the
 * source needs a `Context` and a live provider, while these are pure functions over
 * data classes. The traps they exist to absorb are all here.
 */

/**
 * Flattens a heart-rate record into its samples.
 *
 * The one mapping where record count and sample count differ wildly: a single
 * [HeartRateRecord] carries a *list* of timestamped values, so a day can arrive as a
 * handful of records holding thousands of readings. Treating a record as one reading
 * would silently discard almost all of the data — and would make the cadence look far
 * coarser than it is.
 *
 * Each sample carries its own instant, so the record's own start and end are ignored
 * rather than used to interpolate.
 */
fun HeartRateRecord.toSamples(): List<Sample> {
    val origin = originLabel()
    return samples.map { sample ->
        Sample(time = sample.time, value = sample.beatsPerMinute.toDouble(), device = origin)
    }
}

/**
 * SpO2, already a percentage.
 *
 * Health Connect stores `percentage` as 0..100 while the Google Health API returns a
 * 0..1 fraction for the same metric. Both paths therefore convert at their own
 * boundary and the shared model holds one convention; moving this into the model
 * would force every caller to know which source answered.
 */
fun OxygenSaturationRecord.toSample(): Sample =
    Sample(time = time, value = percentage.value, device = originLabel())

/** RMSSD in milliseconds. */
fun HeartRateVariabilityRmssdRecord.toSample(): Sample =
    Sample(time = time, value = heartRateVariabilityMillis, device = originLabel())

/**
 * A step count, timestamped at the interval start.
 *
 * Attributing a span's count to one instant is a simplification either way; the start
 * is chosen so this path and the Google Health API path agree, since that one filters
 * on interval start.
 */
fun StepsRecord.toSample(): Sample =
    Sample(time = startTime, value = count.toDouble(), device = originLabel())

fun SleepSessionRecord.toSleepSession(): SleepSession = SleepSession(
    start = startTime,
    end = endTime,
    stages = stages.map { stage ->
        SleepSession.Stage(
            name = sleepStageName(stage.stage),
            start = stage.startTime,
            end = stage.endTime,
        )
    },
)

/**
 * Health Connect's integer stage codes, named.
 *
 * Written out rather than read from the library's own int-to-string map, because that
 * map is not part of the stable surface and an unrecognised code must degrade to
 * "UNKNOWN" instead of throwing on a provider that adds a stage.
 */
fun sleepStageName(stage: Int): String = when (stage) {
    SleepSessionRecord.STAGE_TYPE_AWAKE -> "AWAKE"
    SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED -> "AWAKE_IN_BED"
    SleepSessionRecord.STAGE_TYPE_SLEEPING -> "SLEEPING"
    SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> "OUT_OF_BED"
    SleepSessionRecord.STAGE_TYPE_LIGHT -> "LIGHT"
    SleepSessionRecord.STAGE_TYPE_DEEP -> "DEEP"
    SleepSessionRecord.STAGE_TYPE_REM -> "REM"
    else -> "UNKNOWN"
}

/**
 * Which app or device wrote a record.
 *
 * Health Connect is a shared store: a phone pedometer, a scale and the Google Health
 * app all write into it, and unlike the Google Health API there is no server-side
 * "wearables only" filter to ask for. So the origin is surfaced rather than hidden —
 * when a step count looks doubled, this is the column that explains why.
 */
fun Record.originLabel(): String =
    metadata.device?.model
        ?: metadata.dataOrigin.packageName.takeIf { it.isNotBlank() }
        ?: "unknown"
