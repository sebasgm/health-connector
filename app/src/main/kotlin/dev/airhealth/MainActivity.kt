package dev.airhealth

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.airhealth.export.ExportFormat
import dev.airhealth.i18n.LocalStrings
import dev.airhealth.i18n.Strings
import dev.airhealth.ui.AirHealthTheme
import dev.airhealth.ui.HealthScreen
import dev.airhealth.ui.HealthViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val model: HealthViewModel = viewModel()
            val state by model.state.collectAsStateWithLifecycle()
            val scope = rememberCoroutineScope()

            /*
             * Health Connect owns its permission screen, so this contract opens that
             * screen rather than the usual runtime dialog. The returned set is ignored
             * and the client is asked again, because the user can also change grants
             * outside this flow and the client is the only honest answer.
             */
            val permissionLauncher = rememberLauncherForActivityResult(
                PermissionController.createRequestPermissionResultContract()
            ) { model.refresh() }

            /*
             * One launcher per format rather than one with a wildcard MIME type: the
             * type is fixed when the contract is created, and giving the picker the real
             * one is what makes it offer the right default app and keep the extension.
             * The result callbacks are identical, so they all go through `save`.
             */
            val csv = rememberLauncherForActivityResult(CreateDocument(ExportFormat.CSV.mime)) {
                save(it, model, scope)
            }
            val json = rememberLauncherForActivityResult(CreateDocument(ExportFormat.JSON.mime)) {
                save(it, model, scope)
            }
            val html = rememberLauncherForActivityResult(CreateDocument(ExportFormat.HTML.mime)) {
                save(it, model, scope)
            }
            val pdf = rememberLauncherForActivityResult(CreateDocument(ExportFormat.PDF.mime)) {
                save(it, model, scope)
            }

            AirHealthTheme {
                CompositionLocalProvider(LocalStrings provides Strings.of(state.lang)) {
                    Surface {
                        HealthScreen(
                            appName = getString(R.string.app_name),
                            state = state,
                            onSelectMetric = model::selectMetric,
                            onSelectLang = model::setLang,
                            onExport = { format ->
                                val name = model.beginExport(format)
                                when (format) {
                                    ExportFormat.CSV -> csv.launch(name)
                                    ExportFormat.JSON -> json.launch(name)
                                    ExportFormat.HTML -> html.launch(name)
                                    ExportFormat.PDF -> pdf.launch(name)
                                }
                            },
                            onGrant = { permissionLauncher.launch(model.permissions) },
                            onInstall = ::openHealthConnectListing,
                            onRefresh = model::refresh,
                            onToastShown = model::toastShown,
                        )
                    }
                }
            }
        }
    }

    /**
     * Writes the pending export to the URI the user picked.
     *
     * A null URI means they backed out of the picker, which is not a failure and must
     * not raise an error — it only has to clear the pending format so the next export
     * does not inherit it.
     */
    private fun save(
        uri: Uri?,
        model: HealthViewModel,
        scope: kotlinx.coroutines.CoroutineScope,
    ) {
        if (uri == null) {
            model.cancelExport()
            return
        }
        scope.launch {
            try {
                val (format, bytes) = model.renderPending() ?: return@launch
                contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: throw IllegalStateException("could not open the chosen file")
                model.exportSucceeded(format)
            } catch (e: Exception) {
                model.exportFailed(e.message ?: e::class.simpleName ?: "unknown")
            }
        }
    }

    /**
     * Sends the user to Health Connect in the Play Store.
     *
     * Only reachable when `getSdkStatus` said an install or update would help, which
     * means Android 13 or below; from Android 14 it is part of the OS.
     */
    private fun openHealthConnectListing() {
        val uri = Uri.parse(
            "market://details?id=com.google.android.apps.healthdata" +
                "&url=healthconnect%3A%2F%2Fonboarding"
        )
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }.onFailure {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(
                        "https://play.google.com/store/apps/details" +
                            "?id=com.google.android.apps.healthdata"
                    ),
                )
            )
        }
    }
}
