$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$jdk = (Get-ChildItem -LiteralPath "$root/.tools/java" -Directory | Select-Object -First 1).FullName
$directory = "$root/.tools/signing"
New-Item -ItemType Directory -Force -Path $directory | Out-Null
if (Test-Path -LiteralPath "$directory/personal.jks") { Write-Output 'Existing personal signing key retained.'; exit 0 }
$bytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$env:RIPOSTE_STORE_PASSWORD = [Convert]::ToBase64String($bytes)
@("storePassword=$env:RIPOSTE_STORE_PASSWORD",'keyAlias=riposte') | Set-Content -LiteralPath "$directory/signing.properties" -Encoding ASCII
& "$jdk/bin/keytool.exe" -genkeypair -keystore "$directory/personal.jks" -storetype JKS -alias riposte -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Riposte Personal, OU=Personal, O=Private, C=IT' -storepass:env RIPOSTE_STORE_PASSWORD -keypass:env RIPOSTE_STORE_PASSWORD
$code = $LASTEXITCODE
Remove-Item Env:RIPOSTE_STORE_PASSWORD
exit $code
