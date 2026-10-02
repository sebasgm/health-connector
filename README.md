# Health Export

Android app that reads wearable data out of **Health Connect** on the device — heart
rate, blood oxygen, HRV and steps — and exports a day as CSV, JSON, HTML or PDF.

Vendor-neutral by nature: anything that writes into Health Connect is readable here. A
Fitbit Air via the Google Health app and a OnePlus/OPPO watch via OHealth both show up
the same way.

No account, no cloud project, no network permission. English and Spanish. Dark.

The app is called **Health Export**. The directory is still `fitbit-air-health/` and
the application id is still `dev.airhealth`: changing either would break an already
installed copy, and neither is visible to the user.

## How the data gets here

The chain is **device → its companion app → Health Connect → this app**, and every link
can stop it.

1. **Companion app installed** and signed in — Google Health for a Fitbit Air, OHealth
   for a OnePlus watch.
2. **Health Connect exists.** Part of the OS from Android 14. On Android 9–13 it
   installs from the Play Store, and the app offers a button when it detects that.
3. **The companion app is allowed to _write_** into Health Connect, per category.
   *This is the step people miss.* With it off, this app reads successfully and gets
   nothing — hence "Nothing written yet" with instructions rather than an error. Writes
   are generally **not retroactive**, so only data from after the grant appears.
4. **This app is allowed to _read_.** Granted in Health Connect's own screen, not a
   runtime prompt.

Verify the store independently at any time:
**Health Connect → Data and access → Heart rate → See all entries.**

## Build and install

No `java` on PATH here, so `build.sh` finds a JDK itself.

```bash
./build.sh test            # 42 tests, no device or network needed
./build.sh assembleDebug   # app/build/outputs/apk/debug/
./build.sh installDebug    # with a phone attached
```

Installing a built APK needs no Java — `adb` is a native binary:

```bash
~/Android/Sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Export

Top bar → **Export** → CSV / JSON / HTML / PDF. The system file picker chooses where it
lands, so there is no storage permission and no `FileProvider`.

| Format | For | Rows |
|---|---|---|
| CSV | spreadsheet, pandas | all, plus a `source` column |
| JSON | scripts; carries summary + cadence | all, plus `source` |
| HTML | reading in a browser; self-contained | all |
| PDF | printing or sending on | first 1,000, and the document says so |

**Every exported number is formatted against `Locale.ROOT`.** Under a Spanish locale
`"%.1f".format(72.5)` is `"72,5"` — a comma decimal inside a comma-separated file shifts
every later column, and `JSON.parse` rejects it. Six tests in `ExporterTest` run with the
default locale set to Spanish specifically to keep that from regressing. On-screen
numbers do follow the device locale; only files are pinned.

`source` (which device wrote each value) appears in CSV and JSON but not in the on-screen
list, HTML or PDF. In a file that will be analysed it is the difference between a real
trend and two devices double-counting; on screen it was noise.

## Language

English and Spanish, switched with the **EN/ES** button in the top bar; the choice
persists.

The strings live in `i18n/Strings.kt` as Kotlin data, not `res/values-es/`. Switching an
Android resource bundle at runtime needs either `LocaleManager` (API 33+, and this app
supports 29) or AppCompat pulled in purely for `setApplicationLocales`. A composition
local switches instantly, costs no dependency, works on every supported API level, and —
being plain data — can be unit-tested for completeness, which a resource bundle cannot:
`StringsTest` fails on a blank or untranslated entry, where a missing `values-es` string
would silently fall back to English. The cost is hand-rolled plurals.

## Traps the code exists to handle

**A record is a container.** One `HeartRateRecord` holds a *list* of timestamped
samples, so a day can arrive as a handful of records holding thousands of readings.
Mapping a record to one reading would discard almost all of it and make the cadence look
60× coarser. `HeartRateRecord.toSamples()` flattens it; three tests pin it down.

**SpO2 is a percentage here** (0..100), where the Google Health cloud API returns the
same metric as a 0..1 fraction. Converted at the boundary so the model holds one
convention.

**Health Connect is a shared store.** A phone pedometer, a watch and a tracker all write
into it, and there is no server-side "wearables only" filter to ask for.

## The resolution question

Granularity is whatever the companion app chose to write, not what the store can hold. So
the app measures: `Cadence` derives the mode and median gap from the samples that
actually arrive. A 1 s mode means per-second data really is being written; 60 s means it
is not, and no amount of app work changes that.

## Layout

```
app/src/main/kotlin/dev/airhealth/
  hc/
    HealthConnectSource.kt  availability, permissions, paged reads
    Mapping.kt              records -> Sample; pure, so it is unit-tested
  model/
    HealthSource.kt         the read contract and the Availability verdicts
    Metric.kt               what is language-neutral about a metric
    Samples.kt              Sample, SleepSession
    Cadence.kt              measured sampling interval (no prose)
  i18n/Strings.kt           both language bundles + LocalStrings
  export/
    ExportFormat.kt         the four formats and the PDF row cap
    Exporter.kt             CSV / JSON / HTML; pure, locale-pinned, tested
    PdfExporter.kt          android.graphics.pdf, no dependency
  ui/
    Theme.kt                dark colour scheme (light one defined, unused)
    HealthScreen.kt         one LazyColumn: Scaffold + TopAppBar + rows
    HealthViewModel.kt
  MainActivity.kt
app/src/test/kotlin/dev/airhealth/
  ExporterTest.kt               18 tests — locale pinning, escaping, caps
  HealthConnectMappingTest.kt   11 tests — record flattening, units, stages
  StringsTest.kt                 7 tests — bundle completeness, plurals
  CadenceTest.kt                 6 tests — 1s vs 5s vs idle gaps, jitter
```

## Known gaps

- **Never run on a real device from here** — no phone was ever attached. The mapping,
  exporters and strings are unit-tested; the permission handshake, the file picker and
  the PDF renderer are not. `PdfExporter` in particular has **no test at all**, because
  `android.graphics` is not available in a JVM unit test.
- **No origin filter.** With two wearables writing the same metric, the Today card sums
  both and the cadence card reads two interleaved series as one dense stream.
  `ReadRecordsRequest` takes a `dataOriginFilter` that would fix it.
- **The on-screen list is capped at 500 rows.** Exports carry everything.
- **Sleep is read but not shown.** `HealthSource.sleep` works and is tested; no screen.
- **No chart.** The [simulator mockup](https://claude.ai/artifact/VP1d9Rt4mAFD5GT5ZMM7FC)
  shows a proposed trace.
- **Dark only.** `AirHealthTheme(dark = ...)` takes a flag and a light scheme exists;
  wiring it to `isSystemInDarkTheme()` is a one-line change at the call site.
