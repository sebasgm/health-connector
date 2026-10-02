package dev.airhealth.model

/**
 * A metric the app can read.
 *
 * Carries only what is language-neutral. The display name lives in
 * [dev.airhealth.i18n.Strings] — a unit like "bpm" is the same in every language, a
 * label is not.
 */
enum class Metric(val unit: String, val decimals: Int = 0) {
    HEART_RATE("bpm"),
    OXYGEN("%", decimals = 1),
    HRV("ms", decimals = 1),
    STEPS(""),
}
