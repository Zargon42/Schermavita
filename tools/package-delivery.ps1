$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
& node "$root/tools/release-check.mjs" release
if ($LASTEXITCODE -ne 0) { throw 'Release validation failed; nothing packaged.' }
$buildRoot = (Get-Content -LiteralPath "$root/.tools/build-root.txt" -Raw).Trim()
$apk = "$buildRoot/app/outputs/apk/release/app-release.apk"
$metadata = Get-Content -LiteralPath "$buildRoot/app/outputs/apk/release/output-metadata.json" -Raw | ConvertFrom-Json
$contract = Get-Content -LiteralPath "$root/tools/release-contract.json" -Raw | ConvertFrom-Json
$env:JAVA_HOME = (Get-ChildItem -LiteralPath "$root/.tools/java" -Directory | Select-Object -First 1).FullName
$signature = & "$root/.tools/sdk/build-tools/35.0.0/apksigner.bat" verify --print-certs $apk
if ($LASTEXITCODE -ne 0 -or ($signature -join "`n") -notmatch [regex]::Escape($contract.certificateSha256)) { throw 'Release signature differs from the installed app.' }
& "$root/.tools/sdk/build-tools/35.0.0/zipalign.exe" -c -P 16 4 $apk
if ($LASTEXITCODE -ne 0) { throw 'APK alignment failed.' }
$badging = & "$root/.tools/sdk/build-tools/35.0.0/aapt2.exe" dump badging $apk
if ($LASTEXITCODE -ne 0 -or ($badging -join "`n") -notmatch 'android.permission.PACKAGE_USAGE_STATS') { throw 'Built APK is missing Usage Access permission.' }
if (($badging -join "`n") -notmatch "application-label:'SchermaVita'") { throw 'Built APK has the wrong display name.' }
$version = $metadata.elements[0]
if (($badging -join "`n") -notmatch "versionCode='$($version.versionCode)' versionName='$([regex]::Escape($version.versionName))'") { throw 'Built APK version differs from metadata.' }
[xml]$lint = Get-Content -LiteralPath "$buildRoot/app/reports/lint-results-debug.xml" -Raw
if (@($lint.issues.issue | Where-Object { $_.severity -in @('Error','Fatal') }).Count -gt 0) { throw 'Android lint reported errors.' }
$delivery = "$root/delivery"
New-Item -ItemType Directory -Force -Path $delivery | Out-Null
Copy-Item -LiteralPath $apk -Destination "$delivery/Riposte.apk"
Copy-Item -LiteralPath $apk -Destination "$delivery/Riposte-v$($version.versionName).apk"
Copy-Item -LiteralPath $apk -Destination "$delivery/SchermaVita.apk"
Copy-Item -LiteralPath $apk -Destination "$delivery/SchermaVita-v$($version.versionName).apk"
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zipPath = "$delivery/Riposte-source.zip"
if (Test-Path -LiteralPath $zipPath) { Remove-Item -LiteralPath $zipPath }
$zip = [IO.Compression.ZipFile]::Open($zipPath, [IO.Compression.ZipArchiveMode]::Create)
Push-Location $root
try {
    # Line-based Git output C-quotes non-ASCII names. Read raw NUL-delimited UTF-8 paths.
    $gitStart = New-Object System.Diagnostics.ProcessStartInfo
    $gitStart.FileName = 'git'
    $gitStart.WorkingDirectory = $root
    $gitStart.Arguments = 'ls-files -z --cached --others --exclude-standard'
    $gitStart.UseShellExecute = $false
    $gitStart.CreateNoWindow = $true
    $gitStart.RedirectStandardOutput = $true
    $gitStart.StandardOutputEncoding = [Text.Encoding]::UTF8
    $gitProcess = [System.Diagnostics.Process]::Start($gitStart)
    try {
        $listing = $gitProcess.StandardOutput.ReadToEnd()
        $gitProcess.WaitForExit()
        if ($gitProcess.ExitCode -ne 0) { throw 'Could not list source files with Git.' }
    } finally { $gitProcess.Dispose() }
    $files = $listing.Split([char[]]@([char]0), [StringSplitOptions]::RemoveEmptyEntries)
    foreach ($file in ($files | Sort-Object)) {
        $entry = $file.Replace('\','/')
        if ($entry -match '(^|/)(\.git|\.tools|\.gradle|\.kotlin|delivery|private-data|build)(/|$)' -or $entry -match '\.(jks|keystore|ripostehistory)$' -or $entry -match '(^|/)(signing|local)\.properties$') { continue }
        $source = Join-Path $root $file
        if (Test-Path -LiteralPath $source -PathType Leaf) {
            [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $source, $entry, [IO.Compression.CompressionLevel]::Optimal) | Out-Null
        }
    }
} finally { $zip.Dispose(); Pop-Location }
Copy-Item -LiteralPath $zipPath -Destination "$delivery/SchermaVita-source.zip"
$manifest = [ordered]@{
    versionName = $version.versionName; versionCode = $version.versionCode
    applicationId = $metadata.applicationId
    apkSha256 = (Get-FileHash -LiteralPath "$delivery/Riposte.apk" -Algorithm SHA256).Hash.ToLowerInvariant()
    sourceSha256 = (Get-FileHash -LiteralPath $zipPath -Algorithm SHA256).Hash.ToLowerInvariant()
    certificateSha256 = $contract.certificateSha256
    validatedSourceFingerprint = (Get-Content -LiteralPath "$root/.tools/build-receipt.json" -Raw | ConvertFrom-Json).fingerprint
    unitTests = @($contract.tests | Where-Object {$_.kind -eq 'test'}).Count
    deviceTests = @($contract.tests | Where-Object {$_.kind -eq 'androidTest'}).Count
}
$manifest | ConvertTo-Json | Set-Content -LiteralPath "$delivery/release-manifest.json" -Encoding UTF8
Copy-Item -LiteralPath "$root/.tools/device-tests.log" -Destination "$delivery/screen-time-restoration-tests.txt"
Copy-Item -LiteralPath "$buildRoot/app/reports/lint-results-debug.html" -Destination "$delivery/screen-time-restoration-lint.html"
$names = @('SchermaVita.apk',"SchermaVita-v$($version.versionName).apk",'SchermaVita-source.zip','Riposte.apk',"Riposte-v$($version.versionName).apk",'Riposte-source.zip','release-manifest.json','life-history.ripostehistory')
$lines = foreach ($name in $names) { if (Test-Path -LiteralPath "$delivery/$name") { "$((Get-FileHash -LiteralPath "$delivery/$name" -Algorithm SHA256).Hash.ToLowerInvariant())  $name" } }
$lines | Set-Content -LiteralPath "$delivery/SHA256SUMS.txt" -Encoding ASCII
Write-Output "Packaged validated SchermaVita $($version.versionName) (code $($version.versionCode)), source archive and checksums. Legacy Riposte filenames retained as compatibility aliases."
