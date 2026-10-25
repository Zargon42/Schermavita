# Run BEFORE the destructive instrumentation suite. Never retarget to a phone.
param([int]$ExpectedPreviousCode = 4, [int]$ExpectedNewCode = 5)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$env:ANDROID_USER_HOME = "$root/.tools/android-user"
$env:ANDROID_SDK_HOME = "$root/.tools"
$adb = "$root/.tools/sdk/platform-tools/adb.exe"
$device = 'emulator-5554'
$package = 'dev.riposte.personal'
function Invoke-Adb([string[]]$Arguments) {
    $output = & $adb -s $device @Arguments
    if ($LASTEXITCODE -ne 0) { throw "ADB failed: $($Arguments -join ' ')" }
    return $output
}
function Data-Hashes {
    $files = Invoke-Adb @('shell','run-as',$package,'find','databases','files','-type','f')
    $result = foreach ($file in ($files | Sort-Object)) {
        if ($file -match '^(databases|files)/[A-Za-z0-9_./-]+$') {
            Invoke-Adb @('shell','run-as',$package,'sha256sum',$file)
        } else { throw "Unexpected private file path: $file" }
    }
    return ($result -join "`n")
}
$beforePackage = Invoke-Adb @('shell','dumpsys','package',$package)
if (($beforePackage -join "`n") -notmatch "versionCode=$ExpectedPreviousCode\b") { throw "Expected the previous code-$ExpectedPreviousCode release on the disposable emulator." }
Invoke-Adb @('shell','am','force-stop',$package) | Out-Null
$before = Data-Hashes
if (-not $before.Contains('riposte.db')) { throw 'No existing app database to test.' }
$buildRoot = (Get-Content -LiteralPath "$root/.tools/build-root.txt" -Raw).Trim()
Invoke-Adb @('install','-r',"$buildRoot/app/outputs/apk/debug/app-debug.apk") | Out-Null
$afterPackage = Invoke-Adb @('shell','dumpsys','package',$package)
if (($afterPackage -join "`n") -notmatch "versionCode=$ExpectedNewCode\b") { throw "Expected code-$ExpectedNewCode release after update." }
$after = Data-Hashes
if ($before -cne $after) { throw 'Private app files changed during installation.' }
$report = "PASS: in-place emulator update code $ExpectedPreviousCode -> $ExpectedNewCode preserved all $(@($before -split "`n").Count) database/private-file hashes before first launch. Backup decoding is separately covered by unit tests."
$report | Set-Content -LiteralPath "$root/.tools/update-validation.txt" -Encoding UTF8
Write-Output $report
