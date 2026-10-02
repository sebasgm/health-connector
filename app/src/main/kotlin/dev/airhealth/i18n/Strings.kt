package dev.airhealth.i18n

import androidx.compose.runtime.staticCompositionLocalOf
import dev.airhealth.model.Cadence
import dev.airhealth.model.Metric
import java.time.Duration
import java.util.Locale

/** Languages the app ships. */
enum class Lang(val code: String, val endonym: String) {
    EN("en", "English"),
    ES("es", "Español");

    companion object {
        /** The device's language if it is one we have, else English. */
        fun fromSystem(): Lang =
            entries.firstOrNull { it.code == Locale.getDefault().language } ?: EN

        fun ofCode(code: String?): Lang = entries.firstOrNull { it.code == code } ?: fromSystem()
    }
}

/**
 * Every piece of user-facing text, in one object per language.
 *
 * Deliberately Kotlin rather than `res/values-es/strings.xml`. Switching the language
 * of an Android resource bundle at runtime means either `LocaleManager` (API 33+, and
 * this app supports 29) or pulling in AppCompat purely for
 * `setApplicationLocales`. A composition-local object switches instantly, costs no
 * dependency, works on every supported API level, and — because it is plain data — can
 * be unit-tested for completeness, which a resource bundle cannot.
 *
 * The cost is that plurals and date formatting are hand-rolled rather than
 * resource-driven. With two languages and a handful of counted nouns that is the
 * cheaper side of the trade.
 */
data class Strings(
    val lang: Lang,

    // top bar
    val reload: String,
    val export: String,
    val language: String,

    // metrics
    val heartRate: String,
    val oxygen: String,
    val hrv: String,
    val steps: String,

    // summary
    val today: String,
    val min: String,
    val mean: String,
    val max: String,
    val sampleCount: String,

    // cadence
    val measuredCadence: String,
    val cadenceFootnote: String,
    val cadenceNotEnough: (Int) -> String,
    val cadenceSummary: (count: Int, typical: String, median: String, gaps: Int) -> String,

    // samples
    val samples: String,
    val newestFirst: (shown: Int, total: Int) -> String,
    val time: String,
    val value: String,

    // notices
    val permissionNeededTitle: String,
    val permissionNeededBody: (Int) -> String,
    val openHealthConnect: String,
    val missingTitle: String,
    val getHealthConnect: String,
    val notAvailableTitle: String,
    val nothingWrittenTitle: String,
    val nothingWrittenBody: (metric: String) -> String,
    val tryAgain: String,
    val couldNotReadTitle: String,

    // export
    val exportAs: String,
    val exportDone: (format: String) -> String,
    val exportFailed: (reason: String) -> String,
    val exportEmpty: String,
) {
    /** The display name for a metric in this language. */
    fun label(metric: Metric): String = when (metric) {
        Metric.HEART_RATE -> heartRate
        Metric.OXYGEN -> oxygen
        Metric.HRV -> hrv
        Metric.STEPS -> steps
    }

    /** Human duration. Short forms are the same in both languages. */
    fun duration(d: Duration): String = when {
        d.toMinutes() >= 60 -> "${d.toHours()}h"
        d.toSeconds() >= 60 -> "${d.toMinutes()}m"
        d.toSeconds() >= 1 -> "${d.toSeconds()}s"
        else -> "${d.toMillis()}ms"
    }

    /**
     * The cadence card's sentence.
     *
     * Lives here rather than on [Cadence] so the model holds no English. The numbers
     * come from the model; only the wording is chosen here.
     */
    fun cadence(c: Cadence): String {
        if (c.sampleCount < 2 || c.mode == null || c.median == null) {
            return cadenceNotEnough(c.sampleCount)
        }
        return cadenceSummary(c.sampleCount, duration(c.mode), duration(c.median), c.gapCount)
    }

    companion object {
        val English = Strings(
            lang = Lang.EN,
            reload = "Reload",
            export = "Export",
            language = "Language",
            heartRate = "Heart rate",
            oxygen = "Blood oxygen",
            hrv = "HRV",
            steps = "Steps",
            today = "Today",
            min = "min",
            mean = "mean",
            max = "max",
            sampleCount = "samples",
            measuredCadence = "Measured cadence",
            cadenceFootnote = "What the device actually wrote, not what the store can hold.",
            cadenceNotEnough = { n -> "$n sample${if (n == 1) "" else "s"} — not enough to infer a cadence" },
            cadenceSummary = { count, typical, median, gaps ->
                buildString {
                    append("$count samples, typically every $typical (median $median)")
                    if (gaps > 0) append(", $gaps gap${if (gaps == 1) "" else "s"}")
                }
            },
            samples = "Samples",
            newestFirst = { shown, total -> "newest first · $shown of $total" },
            time = "Time",
            value = "Value",
            permissionNeededTitle = "Permission needed",
            permissionNeededBody = { n ->
                "Health Connect has not granted this app read access to $n " +
                    "categor${if (n == 1) "y" else "ies"}. The grant happens in Health " +
                    "Connect's own screen, not here."
            },
            openHealthConnect = "Open Health Connect",
            missingTitle = "Health Connect missing",
            getHealthConnect = "Get Health Connect",
            notAvailableTitle = "Not available",
            nothingWrittenTitle = "Nothing written yet",
            nothingWrittenBody = { metric ->
                "No ${metric.lowercase()} in Health Connect for today.\n\n" +
                    "Check the writing app is allowed to WRITE this category — in Health " +
                    "Connect, under App permissions — then open it near the device so it " +
                    "syncs. Writes are generally not retroactive, so only data from after " +
                    "the grant appears."
            },
            tryAgain = "Try again",
            couldNotReadTitle = "Could not read",
            exportAs = "Export as",
            exportDone = { format -> "Saved as $format" },
            exportFailed = { reason -> "Export failed: $reason" },
            exportEmpty = "Nothing to export yet",
        )

        val Spanish = Strings(
            lang = Lang.ES,
            reload = "Recargar",
            export = "Exportar",
            language = "Idioma",
            heartRate = "Frecuencia cardíaca",
            oxygen = "Oxígeno en sangre",
            hrv = "VFC",
            steps = "Pasos",
            today = "Hoy",
            min = "mín",
            mean = "media",
            max = "máx",
            sampleCount = "muestras",
            measuredCadence = "Cadencia medida",
            cadenceFootnote = "Lo que el dispositivo escribió realmente, no lo que el " +
                "almacén puede guardar.",
            cadenceNotEnough = { n ->
                "$n muestra${if (n == 1) "" else "s"} — insuficientes para deducir una cadencia"
            },
            cadenceSummary = { count, typical, median, gaps ->
                buildString {
                    append("$count muestras, normalmente cada $typical (mediana $median)")
                    if (gaps > 0) append(", $gaps hueco${if (gaps == 1) "" else "s"}")
                }
            },
            samples = "Muestras",
            newestFirst = { shown, total -> "más recientes primero · $shown de $total" },
            time = "Hora",
            value = "Valor",
            permissionNeededTitle = "Falta permiso",
            permissionNeededBody = { n ->
                "Health Connect no ha concedido a esta app acceso de lectura a $n " +
                    "categoría${if (n == 1) "" else "s"}. El permiso se concede en la " +
                    "pantalla de Health Connect, no aquí."
            },
            openHealthConnect = "Abrir Health Connect",
            missingTitle = "Falta Health Connect",
            getHealthConnect = "Instalar Health Connect",
            notAvailableTitle = "No disponible",
            nothingWrittenTitle = "Aún no hay datos escritos",
            nothingWrittenBody = { metric ->
                "No hay ${metric.lowercase()} en Health Connect para hoy.\n\n" +
                    "Comprueba que la app que registra los datos tiene permiso de " +
                    "ESCRITURA para esta categoría — en Health Connect, en Permisos de " +
                    "aplicaciones — y luego ábrela cerca del dispositivo para que " +
                    "sincronice. Las escrituras no suelen ser retroactivas, así que solo " +
                    "aparecen los datos posteriores al permiso."
            },
            tryAgain = "Reintentar",
            couldNotReadTitle = "No se pudo leer",
            exportAs = "Exportar como",
            exportDone = { format -> "Guardado como $format" },
            exportFailed = { reason -> "Error al exportar: $reason" },
            exportEmpty = "Aún no hay nada que exportar",
        )

        fun of(lang: Lang): Strings = when (lang) {
            Lang.EN -> English
            Lang.ES -> Spanish
        }
    }
}

/** The active language's text. Defaults to English until a provider sets it. */
val LocalStrings = staticCompositionLocalOf { Strings.English }
