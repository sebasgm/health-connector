package dev.airhealth.hc

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dev.airhealth.model.HealthSource
import dev.airhealth.model.Sample
import dev.airhealth.model.SleepSession
import java.time.Instant
import kotlin.reflect.KClass

/**
 * Reads Fitbit Air data out of Health Connect, on the device.
 *
 * Nothing here talks to Fitbit or to Google's servers. The chain is: the Air syncs
 * to the **Google Health app**, that app writes into **Health Connect**, and this
 * reads what is there. Each link is a place the data can stop:
 *
 *  1. Health Connect must exist — part of the OS from Android 14, a separate
 *     installable app before that ([availability]).
 *  2. The Google Health app must have been granted **write** access to Health
 *     Connect, per data category. This is off by default and is the step people
 *     miss; nothing this app can do will reveal it, which is why an empty read is
 *     reported as "nothing has been written" rather than as an error.
 *  3. This app must have been granted **read** access, which happens in Health
 *     Connect's own UI rather than a normal runtime prompt.
 *
 * ### On resolution
 *
 * One [HeartRateRecord] holds a *list* of samples, each with its own instant, so the
 * granularity here is whatever the Google Health app chose to write rather than one
 * value per record. That is worth measuring rather than assuming — see
 * [dev.airhealth.model.Cadence].
 */
class HealthConnectSource(private val context: Context) : HealthSource {

    private val client: HealthConnectClient? by lazy {
        if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) {
            runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull()
        } else {
            null
        }
    }

    /** Read permissions this app asks for. Must match the manifest, or they never appear. */
    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
        HealthPermission.getReadPermission(OxygenSaturationRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
    )

    override suspend fun availability(): HealthSource.Availability {
        when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_UNAVAILABLE -> return HealthSource.Availability.Unavailable(
                "Health Connect is not available on this device. It is part of Android " +
                    "14 and later; on Android 9–13 it installs from the Play Store.",
                canInstall = true,
            )

            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                return HealthSource.Availability.Unavailable(
                    "Health Connect needs updating before it can be used.",
                    canInstall = true,
                )
        }

        val hc = client ?: return HealthSource.Availability.Unavailable(
            "Health Connect reported available but could not be opened."
        )

        val granted = runCatching { hc.permissionController.getGrantedPermissions() }
            .getOrElse { return HealthSource.Availability.NeedsPermission(permissions) }

        val missing = permissions - granted
        return if (missing.isEmpty()) {
            HealthSource.Availability.Ready
        } else {
            HealthSource.Availability.NeedsPermission(missing)
        }
    }

    // -- reads ------------------------------------------------------------

    /**
     * Heart rate, flattened.
     *
     * A record is a container: `HeartRateRecord.samples` holds many timestamped
     * beats-per-minute values, so one record can become hundreds of [Sample]s. This
     * is the one mapping where record count and sample count differ wildly, and
     * treating a record as a single reading would throw away almost everything.
     */
    override suspend fun heartRate(start: Instant, end: Instant): List<Sample> =
        read(HeartRateRecord::class, start, end)
            .flatMap { it.toSamples() }
            .sortedBy { it.time }

    /**
     * SpO2 as a percentage.
     *
     * Health Connect stores this as a percentage already, unlike the Google Health
     * API, which returns a 0..1 fraction. Same metric, two conventions; the
     * conversion belongs at each boundary, not in the shared model.
     */
    override suspend fun oxygenSaturation(start: Instant, end: Instant): List<Sample> =
        read(OxygenSaturationRecord::class, start, end)
            .map { it.toSample() }
            .sortedBy { it.time }

    override suspend fun heartRateVariability(start: Instant, end: Instant): List<Sample> =
        read(HeartRateVariabilityRmssdRecord::class, start, end)
            .map { it.toSample() }
            .sortedBy { it.time }

    /** Steps, timestamped at the interval start to match the Google Health path. */
    override suspend fun steps(start: Instant, end: Instant): List<Sample> =
        read(StepsRecord::class, start, end)
            .map { it.toSample() }
            .sortedBy { it.time }

    override suspend fun sleep(start: Instant, end: Instant): List<SleepSession> =
        read(SleepSessionRecord::class, start, end)
            .map { it.toSleepSession() }
            .sortedBy { it.start }

    /**
     * Reads every record of [type] in `[start, end)`, following page tokens.
     *
     * Health Connect pages at 1000 records by default and will not hand over a whole
     * day of dense heart rate in one response, so the token has to be followed or
     * the morning silently goes missing.
     */
    private suspend fun <T : Record> read(
        type: KClass<T>,
        start: Instant,
        end: Instant,
    ): List<T> {
        val hc = client ?: throw HealthConnectUnavailable()
        val out = mutableListOf<T>()
        var token: String? = null
        var pages = 0

        do {
            val response = hc.readRecords(
                ReadRecordsRequest(
                    recordType = type,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    pageSize = PAGE_SIZE,
                    pageToken = token,
                )
            )
            out += response.records
            token = response.pageToken
        } while (token != null && ++pages < MAX_PAGES)

        return out
    }

    private companion object {
        const val PAGE_SIZE = 1000

        /**
         * A day of per-second heart rate arrives as far fewer than 86 400 records,
         * because each one carries many samples. Fifty pages is generous headroom
         * while still bounding a provider that keeps handing back tokens.
         */
        const val MAX_PAGES = 50

    }
}


/** Thrown when a read is attempted with no usable Health Connect. */
class HealthConnectUnavailable :
    Exception("Health Connect is not available on this device")
