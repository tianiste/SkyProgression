param([switch]$NoPrompt)
$ErrorActionPreference = 'Stop'
$originalKey = $env:HYPIXEL_API_KEY
Set-Location -LiteralPath $PSScriptRoot
if (-not $env:JAVA_HOME) {
    $prismJava = Join-Path $env:APPDATA 'PrismLauncher\java\java-runtime-epsilon'
    if (Test-Path -LiteralPath (Join-Path $prismJava 'bin\java.exe')) { $env:JAVA_HOME = $prismJava }
}
if (-not $env:HYPIXEL_API_KEY -and -not $NoPrompt) {
    Write-Host 'Enter your SkyProgression application key locally. It is not saved to disk.'
    $secureKey = Read-Host 'Hypixel application key (input hidden)' -AsSecureString
    $env:HYPIXEL_API_KEY = [System.Net.NetworkCredential]::new('', $secureKey).Password
}
try {
    if (Test-Path -LiteralPath '.\service\bin\service.bat') {
        & '.\service\bin\service.bat'
    } elseif (Test-Path -LiteralPath '.\service\build\install\service\bin\service.bat') {
        & '.\service\build\install\service\bin\service.bat'
    } else {
        & '.\gradlew.bat' :service:run --console=plain
    }
    if ($LASTEXITCODE -ne 0) { throw "Service exited with code $LASTEXITCODE" }
} finally {
    if ($secureKey) { $secureKey.Dispose() }
    if ($originalKey) { $env:HYPIXEL_API_KEY = $originalKey }
    else { Remove-Item Env:\HYPIXEL_API_KEY -ErrorAction SilentlyContinue }
}
