package dev.airhealth.model

import java.time.Instant

/**
 * A single measurement, after the source's quirks have been dealt with.
 *
 * The UI and [Cadence] both work on this rather than on a Health Connect record, so
 * the record-is-a-container and percentage-versus-fraction traps are handled exactly
 * once, in [dev.airhealth.hc.Mapping], instead of at every call site.
 */
data class Sample(
    val time: Instant,
    val value: Double,
    /** e.g. SEDENTARY or ACTIVE, where the device reported it. */
    val motionContext: String? = null,
    /**
     * Which device or app wrote the value.
     *
     * Health Connect is a shared store, so this is the only way to tell a Fitbit Air's
     * numbers from a watch's when both are writing the same metric.
     */
    val device: String? = null,
)

/** A sleep session with its stages flattened to spans. */
data class SleepSession(
    val start: Instant,
    val end: Instant,
    val stages: List<Stage>,
) {
    data class Stage(val name: String, val start: Instant, val end: Instant)
}
