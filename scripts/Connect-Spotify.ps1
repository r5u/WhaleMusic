. "$PSScriptRoot/Common.ps1"
$paths=Read-Paths
& $paths.python (Join-Path $PSScriptRoot 'connect_spotify.py') --bot-folder $Root
