package dev.airhealth.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.airhealth.export.ExportFormat
import dev.airhealth.i18n.Lang
import dev.airhealth.i18n.LocalStrings
import dev.airhealth.model.Metric
import dev.airhealth.model.Sample
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The whole screen is one [LazyColumn].
 *
 * It used to be a `Column` with a `LazyColumn` inside it, which is the classic awkward
 * Compose layout: the cards above could not scroll, the list got whatever height was
 * left over, and on a short screen the samples ended up in a cramped strip at the
 * bottom. Making the cards items in the same list means everything scrolls together and
 * the samples get the full width and height of the screen as the user moves down.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthScreen(
    appName: String,
    state: HealthUiState,
    onSelectMetric: (Metric) -> Unit,
    onSelectLang: (Lang) -> Unit,
    onExport: (ExportFormat) -> Unit,
    onGrant: () -> Unit,
    onInstall: () -> Unit,
    onRefresh: () -> Unit,
    onToastShown: () -> Unit,
) {
    val strings = LocalStrings.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.toast) {
        state.toast?.let {
            snackbar.showSnackbar(it)
            onToastShown()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        appName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    LanguageButton(state.lang, onSelectLang)
                    ExportButton(enabled = state.canExport, onExport = onExport)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("metrics") {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Metric.entries.forEach { metric ->
                        FilterChip(
                            selected = state.metric == metric,
                            onClick = { onSelectMetric(metric) },
                            label = { Text(strings.label(metric)) },
                        )
                    }
                }
            }

            item("reload") {
                Button(onClick = onRefresh, enabled = !state.loading) { Text(strings.reload) }
            }

            if (state.loading) {
                item("progress") {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            state.message?.let { message ->
                item("notice") {
                    NoticeCard(message, onGrant, onInstall, onRefresh)
                }
            }

            state.stats?.let { stats ->
                item("stats") {
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(strings.today, style = MaterialTheme.typography.titleMedium)
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Tile(strings.min, formatValue(stats.min, state.metric.decimals), state.metric.unit)
                                Tile(strings.mean, formatValue(stats.mean, 1), state.metric.unit)
                                Tile(strings.max, formatValue(stats.max, state.metric.decimals), state.metric.unit)
                                Tile(strings.sampleCount, stats.count.toString(), "")
                            }
                        }
                    }
                }
            }

            state.cadence?.let { cadence ->
                item("cadence") {
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Text(
                                strings.measuredCadence,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                strings.cadence(cadence),
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                            )
                            Text(
                                strings.cadenceFootnote,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (state.samples.isNotEmpty()) {
                item("samples-header") {
                    Column {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            Text(strings.samples, style = MaterialTheme.typography.titleMedium)
                            Text(
                                strings.newestFirst(
                                    minOf(state.samples.size, ROW_LIMIT),
                                    state.samples.size,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        HorizontalDivider(Modifier.padding(top = 8.dp))
                    }
                }

                // Newest first, and capped: a day of per-second data is tens of
                // thousands of rows, and the rows past the first few hundred are read
                // from a file rather than scrolled to. Export carries all of them.
                items(
                    items = state.samples.asReversed().take(ROW_LIMIT),
                    key = { it.time.toEpochMilli().toString() + it.value },
                ) { sample ->
                    SampleRow(sample, state.metric)
                }
            }
        }
    }
}

private const val ROW_LIMIT = 500

@Composable
private fun LanguageButton(current: Lang, onSelect: (Lang) -> Unit) {
    // A single button that names the language it switches to: with two languages a
    // menu would be two taps for the same thing.
    val next = if (current == Lang.EN) Lang.ES else Lang.EN
    TextButton(onClick = { onSelect(next) }) {
        Text(next.code.uppercase(Locale.ROOT))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportButton(enabled: Boolean, onExport: (ExportFormat) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val strings = LocalStrings.current

    TextButton(onClick = { open = true }, enabled = enabled) { Text(strings.export) }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        ExportFormat.entries.forEach { format ->
            DropdownMenuItem(
                text = { Text(format.label) },
                onClick = {
                    open = false
                    onExport(format)
                },
            )
        }
    }
}

@Composable
private fun Tile(label: String, value: String, unit: String) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.titleLarge)
            if (unit.isNotBlank()) {
                Text(
                    " $unit",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NoticeCard(
    message: HealthUiState.Message,
    onGrant: () -> Unit,
    onInstall: () -> Unit,
    onRetry: () -> Unit,
) {
    val strings = LocalStrings.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(message.title, style = MaterialTheme.typography.titleMedium)
            Text(message.body, style = MaterialTheme.typography.bodyMedium)
            when (message.remedy) {
                HealthUiState.Remedy.GRANT ->
                    Button(onClick = onGrant) { Text(strings.openHealthConnect) }
                HealthUiState.Remedy.INSTALL ->
                    Button(onClick = onInstall) { Text(strings.getHealthConnect) }
                HealthUiState.Remedy.RETRY ->
                    OutlinedButton(onClick = onRetry) { Text(strings.tryAgain) }
                HealthUiState.Remedy.NONE -> Unit
            }
        }
    }
}

private val rowTime: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT).withZone(ZoneId.systemDefault())

@Composable
private fun SampleRow(sample: Sample, metric: Metric) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            rowTime.format(sample.time),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            buildString {
                append(formatValue(sample.value, metric.decimals))
                if (metric.unit.isNotBlank()) append(" ${metric.unit}")
            },
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
        )
    }
}

