param([switch]$WithPrivateHistory)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$env:ANDROID_USER_HOME = "$root/.tools/android-user"
$adb = "$root/.tools/sdk/platform-tools/adb.exe"
# Deliberately fixed to the test emulator: these tests reset app records.
$device = 'emulator-5554'
$buildRoot = (Get-Content -LiteralPath "$root/.tools/build-root.txt" -Raw).Trim()
& $adb -s $device install -r "$buildRoot/app/outputs/apk/debug/app-debug.apk"
if ($LASTEXITCODE -ne 0) { throw 'Debug APK installation failed.' }
& $adb -s $device install -r "$buildRoot/app/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
if ($LASTEXITCODE -ne 0) { throw 'Test APK installation failed.' }
if ($WithPrivateHistory) {
    & $adb -s $device shell run-as dev.riposte.personal mkdir -p files
    $start = New-Object System.Diagnostics.ProcessStartInfo
    $start.FileName = $adb
    $start.Arguments = "-s $device exec-in run-as dev.riposte.personal dd of=files/test-history.ripostehistory"
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardInput = $true
    $process = [System.Diagnostics.Process]::Start($start)
    $inputFile = [System.IO.File]::OpenRead("$root/delivery/life-history.ripostehistory")
    try { $inputFile.CopyTo($process.StandardInput.BaseStream) } finally { $inputFile.Dispose(); $process.StandardInput.Close() }
    $process.WaitForExit()
    if ($process.ExitCode -ne 0) { throw 'Fixture copy failed.' }
}
& $adb -s $device shell am instrument -w -r dev.riposte.personal.test/androidx.test.runner.AndroidJUnitRunner | Tee-Object -Variable testOutput
$testOutput | Set-Content -LiteralPath "$root/.tools/device-tests.log" -Encoding UTF8
if ($LASTEXITCODE -ne 0 -or ($testOutput -join "`n") -notmatch 'OK \(\d+ tests?\)') { throw 'Instrumentation did not report a passing test suite. Check its output.' }
& node "$root/tools/release-check.mjs" device
if ($LASTEXITCODE -ne 0) { throw 'Required device test inventory failed.' }
