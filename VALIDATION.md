# Source export validation

SchermaVita 0.6.2 (code 8) was validated on 25 October 2026: 53 unit tests and all 13 emulator tests passed, debug/instrumentation/release builds succeeded, and the guarded packager checked signing, permissions, version, compiled display name and source/APK consistency. An in-place code-7 to code-8 emulator upgrade preserved existing database/private-file hashes before first launch.

All required test identities and feature wiring remain. The application ID, signing key, database and backup formats are preserved. The app label, visible name, suggested export filename prefixes and Settings version display changed.

The export contains source, three actual emulator captures and an entirely invented demo fixture. No private fixture, user database, log, APK, signing material, original source attachment or Git history is included. Two retained tests use a separately supplied private history fixture and otherwise skip; their aggregate-count assertions are retained. A public run without that fixture is not a complete maintainer release validation.

Physical Health Connect/OEM behavior, handwriting accuracy and entry speed still require hands-on checks. Publication and licensing remain separate decisions; this export has not been published.
