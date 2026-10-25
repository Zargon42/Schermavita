# Preserve shipped features

- Before reverting a commit, inspect its entire diff and identify unrelated features bundled into it. Restore or remove only the intended changes.
- A passing suite is insufficient if tests have disappeared. `tools/release-contract.json` lists required feature wiring and exact test identities. Do not reduce it to accommodate a regression. Add entries for new shipped features/tests.
- Preserve screen-time sync (permission, Settings, Today, foreground refresh, worker, backup settings) alongside session preparation, equipment and tracker revisions.
- Increment versionCode and versionName for changed release APKs; preserve the application ID and signing key.
- Build using `tools/build.ps1`, run the full emulator suite using `tools/run-device-tests.ps1 -WithPrivateHistory`, then package with `tools/package-delivery.ps1`. This verifies recorded test identities, source/APK consistency, signing, permissions, lint and version. Do not bypass it by directly copying an APK to delivery.
- Regenerate source archive and checksums with each release. Never include signing keys or private history in the source archive. Test upgrades on the disposable emulator, never by resetting the user's phone.

See `SCREEN_TIME_RESTORATION.md` for the regression and recovery.
