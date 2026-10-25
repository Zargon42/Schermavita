"""Compatibility entry point for the guarded Windows release packager."""
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parent.parent
raise SystemExit(subprocess.call([
    "powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File",
    str(root / "tools/package-delivery.ps1"),
], cwd=root))
