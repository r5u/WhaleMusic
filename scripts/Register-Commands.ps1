. "$PSScriptRoot/Common.ps1"
$paths=Read-Paths
Run-Node $paths (Join-Path $Root 'degen/discord-bot') 'deploy-commands.js'
Write-Host 'Degen commands registered. Restart MusicBot to register any new music slash commands.'
