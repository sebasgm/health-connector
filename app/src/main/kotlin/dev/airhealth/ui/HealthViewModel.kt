package dev.airhealth.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.airhealth.export.ExportData
import dev.airhealth.export.ExportFormat
import dev.airhealth.export.Exporter
import dev.airhealth.export.PdfExporter
import dev.airhealth.hc.HealthConnectSource
import dev.airhealth.i18n.Lang
import dev.airhealth.i18n.Strings
import dev.airhealth.model.Cadence
import dev.airhealth.model.HealthSource
import dev.airhealth.model.Metric
import dev.airhealth.model.Sample
import dev.airhealth.model.SampleStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class HealthUiState(
    val lang: Lang = Lang.fromSystem(),
    val metric: Metric = Metric.HEART_RATE,
    val availability: HealthSource.Availability? = null,
    val loading: Boolean = false,
    val samples: List<Sample> = emptyList(),
    val stats: SampleStats? = null,
    val cadence: Cadence? = null,
    val message: Message? = null,
    /** One-shot text for the snackbar, cleared once shown. */
    val toast: String? = null,
) {
    val canExport: Boolean get() = samples.isNotEmpty()

    data class Message(val title: String, val body: String, val remedy: Remedy)

    enum class Remedy { NONE, GRANT, INSTALL, RETRY }
}

class HealthViewModel(app: Application) : AndroidViewModel(app) {

    private val source: HealthSource = HealthConnectSource(app)

    private val prefs =
        app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        HealthUiState(lang = Lang.ofCode(prefs.getString(KEY_LANG, null)))
    )
    val state: StateFlow<HealthUiState> = _state.asStateFlow()

    /** Which format the file picker was opened for. */
    var pendingFormat: ExportFormat? = null
        private set

    val permissions: Set<String> get() = (source as HealthConnectSource).permissions

    private val strings: Strings get() = Strings.of(_state.value.lang)

    init {
        refresh()
    }

    // -- settings ---------------------------------------------------------

    fun setLang(lang: Lang) {
        prefs.edit().putString(KEY_LANG, lang.code).apply()
        // Re-derive the notice text: it is already-rendered prose, so switching
        // language has to rebuild it or the card stays in the old one.
        _state.value = _state.value.copy(lang = lang)
        refresh()
    }

    fun selectMetric(metric: Metric) {
        _state.value = _state.value.copy(metric = metric)
        load()
    }

    fun toastShown() {
        _state.value = _state.value.copy(toast = null)
    }

    // -- reading ----------------------------------------------------------

    /**
     * Re-checks availability, then loads if nothing is in the way.
     *
     * Also called after returning from Health Connect's permission screen, because a
     * grant made there produces no callback — asking again is the only way to learn it.
     */
    fun refresh() {
        viewModelScope.launch {
            val availability = source.availability()
            _state.value = _state.value.copy(
                availability = availability,
                message = noticeFor(availability),
            )
            if (availability is HealthSource.Availability.Ready) load()
        }
    }

    /**
     * Loads today's samples for the selected metric.
     *
     * Local midnight to now, so the figures agree with what the writing app shows for
     * "today"; a rolling 24 hours would disagree with it every afternoon.
     */
    fun load(zone: ZoneId = ZoneId.systemDefault()) {
        viewModelScope.launch {
            val current = _state.value
            _state.value = current.copy(loading = true, message = null)

            val start = LocalDate.now(zone).atStartOfDay(zone).toInstant()
            val now = Instant.now()

            try {
                val samples = when (current.metric) {
                    Metric.HEART_RATE -> source.heartRate(start, now)
                    Metric.OXYGEN -> source.oxygenSaturation(start, now)
                    Metric.HRV -> source.heartRateVariability(start, now)
                    Metric.STEPS -> source.steps(start, now)
                }
                _state.value = _state.value.copy(
                    loading = false,
                    samples = samples,
                    stats = SampleStats.of(samples),
                    cadence = Cadence.of(samples),
                    message = if (samples.isEmpty()) {
                        HealthUiState.Message(
                            strings.nothingWrittenTitle,
                            strings.nothingWrittenBody(strings.label(current.metric)),
                            HealthUiState.Remedy.RETRY,
                        )
                    } else {
                        null
                    },
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    message = HealthUiState.Message(
                        strings.couldNotReadTitle,
                        e.message ?: e::class.simpleName ?: "unknown",
                        HealthUiState.Remedy.RETRY,
                    ),
                )
            }
        }
    }

    // -- export -----------------------------------------------------------

    /** Remembers the chosen format and returns the filename to suggest. */
    fun beginExport(format: ExportFormat): String {
        pendingFormat = format
        return Exporter.filename(exportData(), format)
    }

    fun cancelExport() {
        pendingFormat = null
    }

    /**
     * Renders the pending export.
     *
     * Off the main thread: a day of per-second data is tens of thousands of rows, and a
     * PDF of it draws every one of them.
     */
    suspend fun renderPending(): Pair<ExportFormat, ByteArray>? {
        val format = pendingFormat ?: return null
        val data = exportData()
        val bytes = withContext(Dispatchers.Default) {
            when (format) {
                ExportFormat.CSV -> Exporter.toCsv(data).toByteArray()
                ExportFormat.JSON -> Exporter.toJson(data).toByteArray()
                ExportFormat.HTML -> Exporter.toHtml(data).toByteArray()
                ExportFormat.PDF -> PdfExporter.render(data)
            }
        }
        return format to bytes
    }

    fun exportSucceeded(format: ExportFormat) {
        pendingFormat = null
        _state.value = _state.value.copy(toast = strings.exportDone(format.label))
    }

    fun exportFailed(reason: String) {
        pendingFormat = null
        _state.value = _state.value.copy(toast = strings.exportFailed(reason))
    }

    private fun exportData(zone: ZoneId = ZoneId.systemDefault()): ExportData {
        val s = _state.value
        return ExportData(
            // The metric name is localized on purpose: the file is for this user, and a
            // Spanish-speaking reader opening a CSV headed "Frecuencia cardíaca" is
            // better served than one headed "HEART_RATE". The column names and every
            // number stay locale-independent.
            metric = strings.label(s.metric),
            unit = s.metric.unit,
            decimals = s.metric.decimals,
            samples = s.samples,
            stats = s.stats,
            cadence = s.cadence,
            generatedAt = Instant.now(),
            zone = zone,
        )
    }

    private fun noticeFor(availability: HealthSource.Availability): HealthUiState.Message? =
        when (availability) {
            is HealthSource.Availability.Ready -> null

            is HealthSource.Availability.Unavailable -> HealthUiState.Message(
                if (availability.canInstall) strings.missingTitle else strings.notAvailableTitle,
                availability.reason,
                if (availability.canInstall) {
                    HealthUiState.Remedy.INSTALL
                } else {
                    HealthUiState.Remedy.NONE
                },
            )

            is HealthSource.Availability.NeedsPermission -> HealthUiState.Message(
                strings.permissionNeededTitle,
                strings.permissionNeededBody(availability.missing.size),
                HealthUiState.Remedy.GRANT,
            )
        }

    private companion object {
        const val KEY_LANG = "lang"
    }
}
