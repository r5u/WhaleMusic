$ErrorActionPreference = 'Stop'
$Root = Split-Path $PSScriptRoot -Parent
function Read-Settings {
    $path=Join-Path $Root 'settings.json'
    if (!(Test-Path -LiteralPath $path)) { throw 'Copy settings.json.example to settings.json, fill it in, then run setup.cmd.' }
    return Get-Content -LiteralPath $path -Raw | ConvertFrom-Json
}
function Read-Paths {
    $path=Join-Path $Root 'runtime/paths.json'
    if (!(Test-Path -LiteralPath $path)) { throw 'Run setup.cmd first.' }
    return Get-Content -LiteralPath $path -Raw | ConvertFrom-Json
}
function Test-Port($Port) {
    $client=[Net.Sockets.TcpClient]::new()
    try { $client.Connect('127.0.0.1',$Port); return $true } catch { return $false } finally { $client.Dispose() }
}
function Import-Env($Path) {
    Get-Content -LiteralPath $Path | ForEach-Object {
        if ($_ -match '^([A-Z_][A-Z0-9_]*)=(.*)$') { [Environment]::SetEnvironmentVariable($Matches[1],$Matches[2],'Process') }
    }
}
function Read-State {
    $path=Join-Path $Root 'state/processes.json'
    if (Test-Path -LiteralPath $path) { return @(Get-Content -LiteralPath $path -Raw | ConvertFrom-Json) }
    return @()
}
function Matching-Process($Record) {
    try {
        $process=Get-Process -Id $Record.Id -ErrorAction Stop
        if ($process.StartTime.ToUniversalTime().Ticks -eq [long]$Record.StartTicks -and $process.Path -eq $Record.Path) { return $process }
    } catch {}
    return $null
}
function Start-Managed($Name,$Executable,$Arguments,$Directory) {
    $records=@(Read-State)
    $old=$records | Where-Object Name -eq $Name | Select-Object -First 1
    if ($old -and (Matching-Process $old)) { Write-Host "$Name is already running."; return }
    $process=Start-Process -FilePath $Executable -ArgumentList $Arguments -WorkingDirectory $Directory -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $Root "logs/$Name.log") -RedirectStandardError (Join-Path $Root "logs/$Name-error.log")
    $record=[pscustomobject]@{Name=$Name;Id=$process.Id;StartTicks=$process.StartTime.ToUniversalTime().Ticks;Path=$Executable}
    @(@($records | Where-Object Name -ne $Name)+$record) | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $Root 'state/processes.json') -Encoding UTF8
    Start-Sleep -Milliseconds 500
    if ($process.HasExited) { throw "$Name stopped unexpectedly. Check logs/$Name-error.log." }
    Write-Host "Started $Name."
}
function Run-Node($Paths,$Directory,$Script) {
    Push-Location $Directory
    try { & $Paths.node --preserve-symlinks-main --preserve-symlinks $Script; if ($LASTEXITCODE) { throw "Failed: $Script" } }
    finally { Pop-Location }
}
