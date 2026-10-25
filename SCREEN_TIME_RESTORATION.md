# Screen-time regression and restoration

## Cause

An earlier mixed-purpose commit also contained the screen-time feature. Reverting the whole commit removed screen-time calculation, Usage Access permission, foreground/hourly synchronization, Settings controls, Today display, persisted preferences and its tests.

The subsequent session/equipment/tracker update was built on that reverted source. The delivery process failed to compare shipped features against the prior release. Remaining tests passed because the screen-time tests themselves had disappeared. The update also reused version 0.2.0/code 2, already used by the original screen-time release. Its source archive and checksums were not regenerated with the changed APK. These were release verification errors, not a requested removal.

## Restoration

Recover the original implementation and tests from the earlier implementation, merge its hooks into the updated files, and preserve session preparation, equipment and editable tracker revisions. Release as 0.3.0/code 3 with the existing application ID and signing certificate.

Generic screen-time observations remain readable. The affected release could discard screen-time preferences when saving Settings, because that version no longer represented those fields. Restored code defaults to disabled when the preference is absent; the user can re-enable it under Settings > Screen time and grant Usage access. Do not silently opt them in. Recent Android usage events may permit partial recovery; this does not reconstruct missing older readings.

## Prevention

- Inspect complete diffs before reverting mixed-purpose commits; keep future feature commits focused.
- Check a separate, explicit feature and test inventory before every build. Removing both implementation and tests now fails the check.
- Require the named unit and device tests to pass, rather than accepting any passing test count. Verify the built APK retains Usage Access permission.
- Match validated source fingerprints and APK hashes before packaging; reject changed binaries that reuse a released version code.
- Verify the established signing certificate and increment versions. Package the APK, current source archive, manifest and checksums together.

The automated inventory is a focused regression safeguard, not proof that every feature works. Keep upgrade checks and physical-phone acceptance testing as well.
