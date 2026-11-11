# Source export validation

## Version 0.7.2 (code 12), 10 November 2026

Source changes add navigable calendar/trailing chart windows, daily lines and weekly/monthly averages, daily-dot overrides, foreground trend overlays, profile-only rating history and collapsed goal-execution controls. The manual-score confirmation is included. Six unit tests and four device tests were added; existing chart and bout tests were adapted without removing test identities. The source contract now requires 93 tests.

All 71 unit tests and all 22 emulator tests passed, with no skips and the private-history fixture supplied. The guarded build completed debug, instrumentation and signed release APKs; lint reported no errors. The code-11 to code-12 in-place emulator upgrade preserved all 38 database/private-file hashes before first launch. Synthetic light- and dark-theme chart captures were reviewed. The final full emulator run passed after correcting a duplicate-label test selector and restarting the emulator following a transient screenshot-capture timeout.

This release also moves the 5/10/15 target buttons below Won/Lost to target and adds Jump to today to date charts. Existing application ID, signing certificate, screen-time sync, backup and import test requirements are retained.

## Version 0.7.1 (code 11), 10 November 2026

All 65 unit tests and all 18 emulator tests passed with the private-history fixture supplied. Debug, instrumentation and signed release builds passed through `tools/build.ps1`; Android lint reported no errors. The release contract requires all 83 test identities, preserving the previous requirements.

The code-10 to code-11 in-place emulator upgrade preserved all 38 database/private-file hashes before first launch. New tests verify historical Mood appears with Mood (Happiness), can be separated, retains original stored records and date bases, and does not merge unknown or incompatible scales. Chart markers now use half the previous diameter, with a thin selection outline.

## Version 0.7.0 (code 10), 10 November 2026

All 63 unit tests and all 17 emulator tests passed with the private-history fixture supplied. Debug, instrumentation and signed release builds passed through `tools/build.ps1`; Android lint reported no errors. The release contract retains the previous test identities and now requires 80 tests in total.

The final code-9 to code-10 in-place update on the disposable emulator preserved all four database/private-file hashes before first launch. New regression coverage checks compatible chart revisions, daily precedence and date bases, rolling means, equipment identity and merging, reflection/goal snapshots, partial health imports, process ratings, journal navigation, and equipment configuration changes. The combined-chart layout was captured with four synthetic observations for visual review.

Health Connect provider measurements were not validated against a physical wearable. Readiness and sleep scores remain optional manual trackers; no Google Health cloud integration is included. Existing screen-time synchronization and its permission/revocation tests remain intact.

## Earlier validation

SchermaVita 0.6.2 (code 8) was validated on 25 October 2026: 53 unit tests and all 13 emulator tests passed, debug/instrumentation/release builds succeeded, and the guarded packager checked signing, permissions, version, compiled display name and source/APK consistency. An in-place code-7 to code-8 emulator upgrade preserved existing database/private-file hashes before first launch.

All required test identities and feature wiring remain. The application ID, signing key, database and backup formats are preserved. The app label, visible name, suggested export filename prefixes and Settings version display changed.

The export contains source, three actual emulator captures and an entirely invented demo fixture. No private fixture, user database, log, APK, signing material, original source attachment or Git history is included. Two retained tests use a separately supplied private history fixture and otherwise skip; their aggregate-count assertions are retained. A public run without that fixture is not a complete maintainer release validation.

Physical Health Connect/OEM behavior, handwriting accuracy and entry speed still require hands-on checks. Publication and licensing remain separate decisions; this export has not been published.
