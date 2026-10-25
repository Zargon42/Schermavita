# SchermaVita

Local candidate for a public source release. Earlier development history is retained privately. This export contains no Git history, personal record files, or signing credentials. It has not been published.

Personal Android tracking app with fencing, life logging, flexible tracking days, local insights, Health Connect, and reviewed tournament imports. Built for Android 16; manual logging and document OCR work offline. There is no server, account, or internet permission in the app.

## Install and start

1. Build the debug app using the instructions below, then install it on an emulator or a development device. No APK is included in this source export.
2. In Settings, set **Your fencer name & aliases**. Include the name used on tournament sheets so imported bouts can match your profile.
3. Under Fencing, create a session. Set venue, target, preparation, and equipment once, then use **Add bout** after each bout. Use **Close session** when you finish; all session details and results stay saved. Tap a saved session in the Sessions list to resume it. Long-press the launcher icon to pin the Add bout shortcut.
4. Use Today for quick entries. **Review day** records daily ratings and explicit absences without erasing momentary entries.
5. The default day runs from 04:00 to the next 04:00. A Saturday 02:00 entry belongs to Friday but retains its actual timestamp. Change the boundary in Settings; the change begins after the current day. The transition day may be shorter or longer. Saved days keep their time zone when you travel.
6. Enable notifications for the daily review reminder. HyperOS battery restrictions can delay background work; allow SchermaVita background activity if reminders are delayed.

## Fencer score ratings

Fencing lists each fencer's latest score rating for the selected weapon, with eligible bout counts and provisional status until five bouts. Use **Sort fencers** for highest or lowest rating, name, or most bouts. Unrated profiles remain at the end of rating sorts. Search includes aliases, and **Show more fencers** reveals additional profiles. Open a profile for its latest Foil, Épée, and Sabre ratings. Ratings use all eligible recorded results, including other fencers' bouts, and update after record changes. Profile date and result filters do not limit these latest ratings.

## Session preparation and custom trackers

Session details now include willingness to fence, warm-up before the first bout, and arrival transport. Each is optional and can be cleared. Existing daily willingness ratings remain visible in Day notes as historical values.

Use **Lessons** in session details to add separate lesson entries linked to that session. Each entry records an optional coach, a lesson focus, and notes; entries can be edited or removed before saving the session. Lessons are included in encrypted backups and can be exported as a separate CSV.

Under session Equipment, record contact lenses (yes/no/unknown), sword, glove, and custom categories. Use **Manage equipment categories** to add, rename, reorder, or archive categories, then save the session. New personal bouts inherit this equipment; bout edits can override it, and later session changes leave saved bouts unchanged.

In **Settings → Configure trackers**, edit daily and session trackers. For ratings, enter one `value = label` per line; adding or removing lines changes the number of levels. Changes to scales, choices, and units apply to future answers. Saved answers keep their original scale, including when you edit them. Archived equipment categories remain visible wherever they have saved values.

Version-two encrypted backups include these settings and values; earlier backups can still be restored. Session CSV exports include preparation definitions and equipment, bout exports include participant equipment, and life exports include rating definitions.

## Historical imports

The app supports reviewed historical imports and retains source provenance. No personal import package is distributed with this source export. `tools/prepare_history.py` is a generic converter that reads a separately supplied data directory; its output is private and must not be committed.

## Tournament and poule sheets

- CSV/XLSX: use one bout per row (`fencer_a,fencer_b,score_a,score_b`), or a square poule matrix with a header row and names in the first column. Choose the sheet and column mapping in the importer. Remove totals/rank columns from poule matrices.
- Images/PDFs: choose a page, rotate/crop to the table, then recognise text. Specify grid dimensions. For unequal cells, enter row/column edge percentages, including 0 and 100. Correct the resulting editable table before previewing bouts.
- Every included result must be checked. Review names, both scores, outcome, weapon, event date, and round. Handwriting and complex layouts may need manual transcription.
- A mirrored poule pair creates one bout. Missing scores are not assumed to be zero. Final standings alone do not supply individual bout results.
- When a result resembles an existing bout, choose whether to link it or keep it as a rematch. Linking preserves personal equipment, ratings, and notes.
- Other-fencer results contribute to their profiles and opponent-strength estimates. Your personal bout counts and raw performance include only your own bouts. CSV export retains participant links for further analysis.

## Steps and sleep

In Google Health, enable writing steps and sleep to Health Connect. In SchermaVita Settings, connect Health Connect and permit reads. Foreground refresh reads the last eight tracking days; optional background permission allows periodic refresh. Revoked permissions stop reads but retain existing history.

Steps use Health Connect's aggregated totals within the actual tracking-day interval. Sleep is assigned by wake time. Fitbit/Google Health records are preferred when sources overlap; naps are retained. When stages are missing, the record explicitly says **session duration** rather than claiming measured asleep time. A manual daily sleep entry is available by showing Sleep duration in Configure trackers.

## Screen time

In **Settings > Screen time**, enable sync and choose **Grant Usage access**, then enable SchermaVita in Android's settings. Today shows interactive, unlocked screen time for your tracking day. It refreshes when opened and approximately hourly. Partial readings are marked and excluded from trends. Turning sync off or revoking access retains existing readings.

Version **0.3.0 (code 3)** restores this feature alongside session preparation and editable trackers. Install the signed APK over the existing installation; do not uninstall. If the affected 0.2.0 update saved your settings, you may need to enable screen-time sync again. The app cannot reconstruct older missing readings beyond Android's available events.

## Insights

Fencing Insights lets you chart touch share, points for/against, wins/losses, win rate, self-evaluation, and opponent-adjusted touch share. Filter by weapon, target, training/competition, opponent, and date. Tap a point or use the inspection buttons for dates, values, units, and sample counts. Optional regression lines describe recorded data; missing days are not filled.

Use **Compare with life** to match the selected fencing measure with a life metric on the same day or the previous calendar date. Historical rating scales and calendar/tracking-day bases stay separate. Correlation summaries require at least 20 matched days with varying values; associations do not establish causes.

**Screen time** is available in Life trends, life-to-life comparisons, and fencing comparisons even when hidden from manual entry. Values are minutes of interactive, unlocked time. Partial readings are excluded; the current tracking day is labelled **so far** with its measurement time. Saved readings remain chartable after sync is disabled. To start collecting readings, enable sync and grant Usage access in Settings > Screen time.

Opponent baselines use score margins from your recorded results network, including imported results between other fencers. Each weapon has separate estimates; target and context filters narrow their evidence. Expectations use only earlier days, with same-day results processed together. Comparisons are provisional until both participants have five earlier scored bouts. Adjusted touch share is actual minus expected share in percentage points, weighted by combined touches. Ratings are local descriptive estimates, not official rankings or win probabilities.

To import a saved poule photo, choose **Import data > Choose photo**. **Choose file** and **Photograph sheet** remain available. Stored photos use the same crop, offline OCR, correction, and reviewed-import steps.

## Backups and exports

Use **Settings → Backup, restore & export** regularly. Full backups include source attachments and use PBKDF2-HMAC-SHA256 (310,000 iterations) plus AES-256-GCM authenticated encryption. Use a strong password and keep it separately; the app cannot recover it. Restoration validates the whole archive before replacing records.

CSV exports are unencrypted. App data is excluded from Android cloud backup/device transfer; uninstalling without a manual backup can lose it. Source attachments are stored privately on the phone.

## Build

Modules: `core` (tracking days, provenance, encryption), `fencing` (independent domain/repository contracts), `life`, `insights`, `imports`, `health`, and `app` (Compose, Room, DataStore, WorkManager). The Room store uses indexed, individually versioned JSON records with transactional differential updates. Fencing has no dependency on life or Health Connect.

Toolchain: JDK 21, Gradle 8.13, Android Gradle Plugin 8.13.0, Kotlin 2.1.20, Android SDK 36. Dependencies are pinned in the Gradle files. Open the project in Android Studio or use a compatible Gradle installation with `ANDROID_HOME`/`local.properties` set.

For a standalone checkout, install JDK 21 and Android SDK 36, set `JAVA_HOME` and `ANDROID_HOME` (or configure Android Studio), then run the Gradle wrapper:

```powershell
New-Item -ItemType Directory -Force .tools | Out-Null
node tools/release-check.mjs source
.\gradlew.bat :core:test :fencing:test :life:test :insights:test :imports:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

The debug APK is generated under `app/build/outputs/apk/debug/` with a standard build. Android Studio can also open and build the project. Debug signing uses a local development key; the original release signing key is not included. Do not install a debug build over an existing signed personal installation.

The scripts under `tools/` preserve the original guarded Windows release workflow. `tools/build.ps1` expects separately provisioned tools under `.tools/java`, `.tools/sdk`, and `.tools/gradle/gradle-8.13`, and keeps generated builds in a temporary directory to avoid OneDrive locking. This export was validated using that script and externally provisioned local tools. These tools are not included in the export.

The complete private maintainer validation uses `tools/run-device-tests.ps1 -WithPrivateHistory` and `tools/package-delivery.ps1`. Two retained tests require a separately supplied private fixture and otherwise skip; the complete device contract intentionally rejects a missing private-history test. No test identity or required feature has been removed to accommodate this export. Fixture assertions contain expected aggregate sizes, not personal records. Ordinary public builds and synthetic tests do not require that fixture. See `VALIDATION.md` for the export validation scope.

Version 0.6.2 (code 8) renames the display name to SchermaVita. The original application ID, data formats and signing key are preserved. The source export omits APKs and signing material. No application license has been selected for publication; existing third-party notices are retained.

## Device checks

See `VALIDATION.md` for completed automated checks and physical-phone checks. Emulator tests cannot establish Fitbit synchronisation, Xiaomi background scheduling, real handwriting recognition, or your personal entry speed.
