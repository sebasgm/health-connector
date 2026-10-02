package dev.airhealth.model

import java.time.Duration
import java.time.Instant

/**
 * What sampling interval a series actually arrived at.
 *
 * This exists because of a gap between two true statements. The Google Health API
 * documents heart rate with a **storage resolution of 1 second** — that is the
 * resolution the store can hold. It is not a promise that any given device writes
 * at that rate, and Fitbit hardware has historically sampled densely during
 * exercise and sparsely at rest to save battery. A screenless tracker on a
 * multi-day battery has every reason to do the same.
 *
 * So rather than assume a cadence, the app measures it and shows the answer. A
 * [median] of one second means the Air really is writing per-second data; five
 * seconds or sixty means the storage resolution was never the binding constraint
 * and no amount of API work will change it.
 */
data class Cadence(
    val sampleCount: Int,
    val median: Duration?,
    val shortest: Duration?,
    val longest: Duration?,
    /** The most common rounded-to-the-second gap, which is the device's real rate. */
    val mode: Duration?,
    /**
     * Gaps far above the median: the device not sampling, rather than sampling
     * slowly. Counted separately so a night of no data does not drag the median.
     */
    val gapCount: Int,
) {
    companion object {
        /**
         * A gap counts as the device being idle rather than slow once it exceeds
         * this multiple of the median. Four is arbitrary but stable: it tolerates
         * ordinary jitter while still separating "every 5s" from "nothing for an
         * hour", which are the two cases worth telling apart.
         */
        const val GAP_FACTOR = 4

        /**
         * Measures the cadence of [samples], which need not be sorted.
         *
         * Duplicate timestamps are kept rather than collapsed: two points at the
         * same instant mean two sources are writing the same metric, and hiding
         * that would make the result look cleaner than the data is.
         */
        fun of(samples: List<Sample>): Cadence {
            if (samples.size < 2) {
                return Cadence(samples.size, null, null, null, null, 0)
            }
            val times = samples.map(Sample::time).sorted()
            val gaps = times.zipWithNext { a, b -> Duration.between(a, b) }

            val sortedGaps = gaps.sorted()
            val median = sortedGaps[sortedGaps.size / 2]

            // Rounded to whole seconds before counting: the device's nominal rate is
            // a round number, and raw millisecond jitter would give every gap its
            // own bucket and a meaningless mode.
            val mode = gaps
                .groupingBy { Duration.ofSeconds(it.toSeconds()) }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key

            val gapThreshold = median.multipliedBy(GAP_FACTOR.toLong())
            return Cadence(
                sampleCount = samples.size,
                median = median,
                shortest = sortedGaps.first(),
                longest = sortedGaps.last(),
                mode = mode,
                gapCount = gaps.count { it > gapThreshold && it > Duration.ofSeconds(1) },
            )
        }

    }
}

/** Min, max and mean of the measured values, for the summary row. */
data class SampleStats(val count: Int, val min: Double, val max: Double, val mean: Double) {
    companion object {
        fun of(samples: List<Sample>): SampleStats? {
            if (samples.isEmpty()) return null
            val values = samples.map(Sample::value)
            return SampleStats(
                count = values.size,
                min = values.min(),
                max = values.max(),
                mean = values.average(),
            )
        }
    }
}
