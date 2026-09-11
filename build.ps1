# Yerel derleme. Android Studio gerekmez.
# Kullanim:  .\build.ps1          -> debug APK
#            .\build.ps1 release  -> release APK
param([string]$Variant = "debug")

$env:JAVA_HOME = "C:\Users\Eness\.pofu-tools\jdk17"
$gradle = "C:\Users\Eness\.pofu-tools\gradle\bin\gradle.bat"

$task = if ($Variant -eq "release") { "assembleRelease" } else { "assembleDebug" }
& $gradle testDebugUnitTest $task --console=plain --no-daemon
if ($LASTEXITCODE -ne 0) { Write-Host "Derleme basarisiz." -ForegroundColor Red; exit 1 }

$apk = Get-ChildItem "app\build\outputs\apk\$Variant\*.apk" | Select-Object -First 1
Write-Host "`nHazir: $($apk.FullName)" -ForegroundColor Green
