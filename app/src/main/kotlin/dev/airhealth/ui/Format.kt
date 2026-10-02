package dev.airhealth.ui

import java.util.Locale

/**
 * Formats a measured value for the screen.
 *
 * Pinned to [Locale.ROOT], so a decimal point stays a point in every language. The
 * device locale is deliberately *not* used: these are instrument readings, and the
 * convention a reader of this app expects for `72.5 bpm` is the same one the exported
 * CSV and JSON use. Letting the screen say `72,5` while the file says `72.5` would make
 * the two disagree for no benefit.
 *
 * Integer metrics lose the decimal entirely rather than showing `.0` — a step count of
 * `47.0` is noise.
 */
internal fun formatValue(value: Double, decimals: Int): String =
    if (decimals == 0) {
        value.toLong().toString()
    } else {
        String.format(Locale.ROOT, "%.${decimals}f", value)
    }
