$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$env:JAVA_HOME = (Get-ChildItem -LiteralPath "$root/.tools/java" -Directory | Select-Object -First 1).FullName
$env:ANDROID_HOME = "$root/.tools/sdk"
$env:ANDROID_USER_HOME = "$root/.tools/android-user"
$env:PATH = "$env:JAVA_HOME/bin;" + $env:PATH
1..80 | ForEach-Object { 'y' } | & "$env:ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager.bat" --sdk_root=$env:ANDROID_HOME --licenses
& "$env:ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager.bat" --sdk_root=$env:ANDROID_HOME 'platform-tools' 'platforms;android-36' 'build-tools;36.0.0' 'emulator' 'system-images;android-36;google_apis;x86_64'
exit $LASTEXITCODE
