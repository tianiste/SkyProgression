param([switch]$NoPrompt)
$ErrorActionPreference = 'Stop'
$originalKey = $env:HYPIXEL_API_KEY
$originalJava = $env:JAVA_HOME
$secureKey = $null
$exitCode = 0
try {
    Set-Location -LiteralPath $PSScriptRoot
    Write-Host 'SkyProgression companion service' -ForegroundColor Cyan
    Write-Host 'Keep this window open while using live profile lookup.'
    if (-not $env:JAVA_HOME) {
        $prismJava = Join-Path $env:APPDATA 'PrismLauncher\java\java-runtime-epsilon'
        if (Test-Path -LiteralPath (Join-Path $prismJava 'bin\java.exe')) { $env:JAVA_HOME = $prismJava }
    }
    if ($env:JAVA_HOME) {
        if (-not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
            throw 'JAVA_HOME points to a missing Java installation. Set it to your Java 25 folder.'
        }
    } elseif (-not (Get-Command java.exe -ErrorAction SilentlyContinue)) {
        throw 'Java was not found. Install Java 25 or set JAVA_HOME to your Java 25 folder.'
    }
    if (-not $env:HYPIXEL_API_KEY -and -not $NoPrompt) {
        Write-Host 'Paste your application key below (hidden input), then press Enter.'
        Write-Host 'Press Enter without a key to start with profile lookup unavailable.'
        $secureKey = Read-Host 'Hypixel application key' -AsSecureString
        $env:HYPIXEL_API_KEY = [System.Net.NetworkCredential]::new('', $secureKey).Password
    }
    if (Test-Path -LiteralPath '.\service\bin\service.bat') {
        & '.\service\bin\service.bat'
    } elseif (Test-Path -LiteralPath '.\service\build\install\service\bin\service.bat') {
        & '.\service\build\install\service\bin\service.bat'
    } elseif (Test-Path -LiteralPath '.\gradlew.bat') {
        & '.\gradlew.bat' :service:run --console=plain
    } else {
        throw 'Service files are missing. Extract the entire test-pack ZIP before starting this launcher.'
    }
    if ($LASTEXITCODE -ne 0) { throw "Service exited with code $LASTEXITCODE" }
} catch {
    $exitCode = 1
    Write-Host ''
    Write-Host ('Could not run SkyProgression: ' + $_.Exception.Message) -ForegroundColor Red
    Write-Host 'Read the messages above. If port 18475 is already in use, another copy may be running.'
} finally {
    if ($secureKey) { $secureKey.Dispose() }
    if ($originalKey) { $env:HYPIXEL_API_KEY = $originalKey }
    else { Remove-Item Env:\HYPIXEL_API_KEY -ErrorAction SilentlyContinue }
    if ($originalJava) { $env:JAVA_HOME = $originalJava }
    else { Remove-Item Env:\JAVA_HOME -ErrorAction SilentlyContinue }
    if (-not $NoPrompt) {
        Write-Host ''
        Read-Host 'Press Enter to close this launcher' | Out-Null
    }
}
exit $exitCode
