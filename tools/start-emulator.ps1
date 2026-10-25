param([switch]$Software,[switch]$Atd)
$ErrorActionPreference = 'Stop'
$env:DEBUG = ''
$root = Split-Path $PSScriptRoot -Parent
$env:JAVA_HOME = (Get-ChildItem -LiteralPath "$root/.tools/java" -Directory | Select-Object -First 1).FullName
$env:ANDROID_HOME = "$root/.tools/sdk"
$env:ANDROID_USER_HOME = "$root/.tools/android-user"
$env:ANDROID_AVD_HOME = "$root/.tools/avds"
$env:PATH = "$env:JAVA_HOME/bin;" + $env:PATH
New-Item -ItemType Directory -Force -Path $env:ANDROID_AVD_HOME | Out-Null
$avdName = if ($Atd) { 'Riposte16Atd' } else { 'Riposte16' }
$imagePackage = if ($Atd) { 'system-images;android-36;google_atd;x86_64' } else { 'system-images;android-36;google_apis;x86_64' }
if (-not (Test-Path -LiteralPath "$env:ANDROID_AVD_HOME/$avdName.ini")) {
    'no' | & "$env:ANDROID_HOME/cmdline-tools/latest/bin/avdmanager.bat" create avd --name $avdName --package $imagePackage --path "$env:ANDROID_AVD_HOME/$avdName.avd" --device pixel_2
}
if ($Atd) {
    $configPath = "$env:ANDROID_AVD_HOME/$avdName.avd/config.ini"
    $config = Get-Content -LiteralPath $configPath
    $config = $config | Where-Object { $_ -notmatch '^(hw.lcd.width|hw.lcd.height|hw.lcd.density|hw.ramSize|vm.heapSize|hw.cpu.ncore)=' }
    $config + @('hw.lcd.width=480','hw.lcd.height=800','hw.lcd.density=160','hw.ramSize=1024','vm.heapSize=256','hw.cpu.ncore=2') | Set-Content -LiteralPath $configPath -Encoding ASCII
}
$arguments = @('-avd',$avdName,'-no-window','-no-audio','-no-boot-anim','-gpu','swiftshader_indirect','-memory','1024','-no-snapshot','-no-metrics','-feature','-Vulkan')
if ($Software) { $arguments += @('-accel','off') }
$process = Start-Process -FilePath "$env:ANDROID_HOME/emulator/emulator.exe" -ArgumentList $arguments -WindowStyle Hidden -RedirectStandardOutput "$root/.tools/emulator.log" -RedirectStandardError "$root/.tools/emulator-error.log" -PassThru
Write-Output "Emulator PID: $($process.Id)"
$process.WaitForExit()
Write-Output "Emulator exited: $($process.ExitCode)"
