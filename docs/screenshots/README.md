# Screenshot provenance

Captured on 11 November 2026 from the validated SchermaVita **0.7.2 (code 12)** debug APK on the disposable Android 16 emulator (`emulator-5554`, `Riposte16Atd`) at **480 × 1000 pixels**. These are direct PNG screenshots of the actual app, not mockups or generated UI. No image edits, compositing or overlays were applied.

The app was cleared before seeding. No private history package or personal database was loaded. All displayed people, clubs, equipment, lessons, bout scores, step/sleep totals and ratings were invented for this demonstration. Integrations were not granted live access. The on-screen permission status is retained honestly.

- `today.png`: 11 November selected explicitly, with invented daily totals and quick-entry trackers. A tracking day can begin on the previous calendar date before the configured 04:00 boundary.
- `fencing.png`: a fictional active practice session, lesson, goals, process-execution summary and recorded results.
- `insights.png`: the Mood (Happiness) Month timeline, calculated by the app, with calendar/trailing range controls, Jump to today and optional rolling trends.
- `bout.png`: unsaved bout entry, with visible session goals and target-score buttons outside the collapsed rating/tactics section.
- `equipment.png`: an individual demo foil with blade/handle/set-angle properties and its recorded performance.
- `journal.png`: searchable session cards showing goals and an invented completed reflection.

The exact invented payloads, including the default tracker definitions used for this capture, are in [synthetic-records.json](../demo/synthetic-records.json). This is a documented set of SQLite record payload examples for developer inspection, not an end-user import package. `Demo`, `Example` and `Synthetic demo` labels make their origin explicit. The fixture contains no source attachments, private records or credentials.

For reproduction, use only a disposable emulator: clear the app, launch the 0.7.2 debug build to initialize Room, then stop it and populate its `records` table from the fixture. Set the display to 480 × 1000 and enable `debug.hwui.drawing_enabled` on ATD images. Launch the app and capture PNGs with `adb exec-out screencap -p`. The app may create current tracking-day records and refresh permission-status text on launch. Do not use this database-seeding workflow on a personal device.

The screenshot session changes emulator data only; application code and the required regression-test inventory are unchanged.
