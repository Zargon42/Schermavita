"""Install the new debug APK over the existing emulator app; verify data bytes survive.

Requires an existing installation. Never targets a physical phone or clears data.
"""
from pathlib import Path
import hashlib
import os
import subprocess

root = Path(__file__).resolve().parent.parent
env = dict(os.environ, ANDROID_USER_HOME=str(root / ".tools/android-user"))
adb = [str(root / ".tools/sdk/platform-tools/adb.exe"), "-s", "emulator-5554"]
def command(*args):
    return subprocess.check_output(adb + list(args), env=env)

version_before = command("shell", "dumpsys", "package", "dev.riposte.personal").decode()
assert "versionCode=1 " in version_before, "This check expects the original v0.1.0 installation."
command("shell", "am", "force-stop", "dev.riposte.personal")
paths = command("shell", "run-as", "dev.riposte.personal", "find", "databases", "files", "-type", "f").decode().splitlines()
paths = [p for p in paths if p in {"databases/riposte.db", "databases/riposte.db-wal"} or p.startswith(("files/sources/", "files/datastore/"))]
assert any(path.endswith("riposte.db") for path in paths), "Existing database missing."
def digests():
    return {p: hashlib.sha256(command("exec-out", "run-as", "dev.riposte.personal", "cat", p)).hexdigest() for p in paths}
before = digests()
build = Path((root / ".tools/build-root.txt").read_text().strip())
print(command("install", "-r", str(build / "app/outputs/apk/debug/app-debug.apk")).decode().strip())
after = digests()
assert before == after, "Stored files changed during package replacement."
assert "versionCode=2 " in command("shell", "dumpsys", "package", "dev.riposte.personal").decode()
print(f"PASS: v0.1.0 -> v0.2.0 update preserved all {len(paths)} database/private files byte-for-byte.")
