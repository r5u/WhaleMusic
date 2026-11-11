. "$PSScriptRoot/Common.ps1"
$records=@(Read-State)
foreach($name in @('musicbot','companion','backend','mongo')) {
    $record=$records | Where-Object Name -eq $name | Select-Object -First 1
    if (!$record){continue}
    $process=Matching-Process $record
    if ($process) { Stop-Process -Id $process.Id -ErrorAction Stop; Write-Host "Stopped $name." }
}
Write-Host 'Stopped only the processes recorded for this WhaleMusic folder.'
