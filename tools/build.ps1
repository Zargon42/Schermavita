param([Parameter(ValueFromRemainingArguments=$true)][string[]]$Tasks = @(':app:assembleDebug'))
$ErrorActionPreference = 'Stop'
$env:DEBUG = ''
$root = Split-Path $PSScriptRoot -Parent
& node "$root/tools/release-check.mjs" begin-build
if ($LASTEXITCODE -ne 0) { throw 'Required feature inventory failed.' }
$env:JAVA_HOME = (Get-ChildItem -LiteralPath "$root/.tools/java" -Directory | Select-Object -First 1).FullName
$env:ANDROID_HOME = "$root/.tools/sdk"
$env:ANDROID_USER_HOME = "$root/.tools/android-user"
$env:GRADLE_USER_HOME = "$root/.tools/gradle-home"
$env:PATH = "$env:JAVA_HOME/bin;$env:ANDROID_HOME/platform-tools;" + $env:PATH
# Keep generated files outside OneDrive's reparse-point and attribute handling.
$hasher = [System.Security.Cryptography.SHA256]::Create()
try { $suffix = ([BitConverter]::ToString($hasher.ComputeHash([Text.Encoding]::UTF8.GetBytes($root)))).Replace('-','').Substring(0,12) } finally { $hasher.Dispose() }
$env:RIPOSTE_BUILD_ROOT = Join-Path ([IO.Path]::GetTempPath()) "riposte-build-$suffix"
New-Item -ItemType Directory -Force -Path $env:RIPOSTE_BUILD_ROOT | Out-Null
$env:RIPOSTE_BUILD_ROOT | Set-Content -LiteralPath "$root/.tools/build-root.txt" -Encoding ASCII
Push-Location $root
try {
    & "$root/.tools/gradle/gradle-8.13/bin/gradle.bat" @Tasks --project-cache-dir "$env:RIPOSTE_BUILD_ROOT/gradle-cache" --console=plain --no-daemon
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    & node "$root/tools/release-check.mjs" record-build @Tasks
    exit $LASTEXITCODE
} finally { Pop-Location }
