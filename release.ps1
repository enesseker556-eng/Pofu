# Yeni surum yayinlar: derler, dist/ altini gunceller, commit'ler, push'lar.
# Telefondaki uygulama dist/version.json'a bakip kendini gunceller.
#
#   .\release.ps1 -Notes "ne degisti"
param(
    [Parameter(Mandatory = $true)][string]$Notes,
    [string]$VersionName
)

$ErrorActionPreference = "Stop"
$env:JAVA_HOME = "C:\Users\Eness\.pofu-tools\jdk17"
$gradle = "C:\Users\Eness\.pofu-tools\gradle\bin\gradle.bat"

$manifest = Get-Content "dist\version.json" -Raw | ConvertFrom-Json
$code = [int]$manifest.versionCode + 1
if (-not $VersionName) { $VersionName = "1.$code" }

Write-Host "Surum $VersionName (kod $code) derleniyor..." -ForegroundColor Cyan
& $gradle testDebugUnitTest assembleRelease --console=plain --no-daemon `
    "-PversionCode=$code" "-PversionName=$VersionName"
if ($LASTEXITCODE -ne 0) { throw "Derleme basarisiz" }

Copy-Item "app\build\outputs\apk\release\app-release.apk" "dist\pofu.apk" -Force

# Surum dosyasi once yazilmali ki push tek seferde tutarli gitsin.
[ordered]@{
    versionCode = $code
    versionName = $VersionName
    notes       = $Notes
    apk         = "dist/pofu.apk"
} | ConvertTo-Json | Set-Content "dist\version.json" -Encoding utf8

git add -A
git commit -m "Surum ${VersionName}: $Notes"
git push origin main

Write-Host "`nYayinlandi: $VersionName (kod $code)" -ForegroundColor Green
Write-Host "Telefonda uygulamayi kapat-ac, guncelleme kendi dusecek." -ForegroundColor Green
