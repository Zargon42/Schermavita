param([Parameter(ValueFromRemainingArguments=$true)][string[]]$AdbArgs)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$env:ANDROID_USER_HOME = "$root/.tools/android-user"
$env:ANDROID_SDK_HOME = "$root/.tools"
& "$root/.tools/sdk/platform-tools/adb.exe" -s emulator-5554 @AdbArgs
exit $LASTEXITCODE
