package dev.airhealth.model

import java.time.Instant

/**
 * Where samples come from.
 *
 * One implementation, [dev.airhealth.hc.HealthConnectSource]. The interface stays
 * because it is what the UI codes against and because it carries [Availability],
 * which is the part with real logic in it: the app has to tell "Health Connect is
 * missing" from "you have not granted access" from "nothing has been written", and
 * those three need completely different things from the user.
 */
interface HealthSource {

    /**
     * Whether this source can be read right now, and if not, why.
     *
     * Checked before reading rather than discovered through a failed read.
     */
    suspend fun availability(): Availability

    suspend fun heartRate(start: Instant, end: Instant): List<Sample>

    suspend fun oxygenSaturation(start: Instant, end: Instant): List<Sample>

    suspend fun heartRateVariability(start: Instant, end: Instant): List<Sample>

    suspend fun steps(start: Instant, end: Instant): List<Sample>

    suspend fun sleep(start: Instant, end: Instant): List<SleepSession>

    sealed interface Availability {
        /** Ready to read. */
        data object Ready : Availability

        /**
         * Health Connect is missing or too old. [canInstall] is true only when sending
         * the user to the Play Store would actually fix it, which means Android 13 or
         * below — from 14 it is part of the OS and cannot be installed.
         */
        data class Unavailable(val reason: String, val canInstall: Boolean = false) : Availability

        /** Reachable, but this app has not been granted read access. */
        data class NeedsPermission(val missing: Set<String>) : Availability
    }
}
