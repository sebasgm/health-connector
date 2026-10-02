package dev.airhealth.export

/** The four shapes a day's samples can leave the app in. */
enum class ExportFormat(
    val label: String,
    val extension: String,
    /** The MIME type handed to the system file picker. */
    val mime: String,
) {
    /** For a spreadsheet or pandas. Carries every row and the source column. */
    CSV("CSV", "csv", "text/csv"),

    /** For a script. Carries the summary and the cadence alongside the rows. */
    JSON("JSON", "json", "application/json"),

    /** For reading in a browser. Self-contained, no external assets. */
    HTML("HTML", "html", "text/html"),

    /**
     * For printing or sending on.
     *
     * Row-capped, unlike the others: a day of per-second data is 86 400 rows, which is
     * roughly 1 700 pages that nobody will read. The summary is complete; the table is
     * a sample, and the document says so.
     */
    PDF("PDF", "pdf", "application/pdf");

    companion object {
        /** Rows the PDF will print before it starts saying "truncated". */
        const val PDF_ROW_CAP = 1_000
    }
}
