. "$PSScriptRoot/Common.ps1"
$paths=Read-Paths
$settings=Read-Settings
if (!(Test-Path -LiteralPath (Join-Path $Root 'WhaleMusic.jar'))) { throw 'Download the Windows release ZIP, or build WhaleMusic.jar. See docs/BUILD.md.' }
foreach($name in @('data','logs','state')) { New-Item -ItemType Directory -Force (Join-Path $Root $name) | Out-Null }
$records=@(Read-State)
foreach($service in @(@{Name='mongo';Port=27028},@{Name='backend';Port=3078})) {
    $record=$records | Where-Object Name -eq $service.Name | Select-Object -First 1
    if ((Test-Port $service.Port) -and !($record -and (Matching-Process $record))) { throw "Port $($service.Port) is already used by another installation. Stop that installation first." }
}
Start-Managed 'mongo' $paths.mongo "--replSet beer --bind_ip 127.0.0.1 --port 27028 --dbpath `"$Root/data`" --logpath `"$Root/logs/mongo-server.log`" --logappend" $Root
for($i=0;$i -lt 30 -and !(Test-Port 27028);$i++){Start-Sleep -Seconds 1}
Run-Node $paths (Join-Path $Root 'degen/backend') 'init-beer-db.js'
Write-Host 'Preparing game catalogs and owner access...'
try { Run-Node $paths (Join-Path $Root 'degen/backend') 'whalemusic-bootstrap.js' *> (Join-Path $Root 'logs/bootstrap.log') }
catch { throw 'Game initialization failed. Read logs/bootstrap.log.' }
Start-Managed 'backend' $paths.node '--preserve-symlinks-main --preserve-symlinks index.js' (Join-Path $Root 'degen/backend')
for($i=0;$i -lt 30 -and !(Test-Port 3078);$i++){Start-Sleep -Seconds 1}
if (!(Test-Port 3078)){throw 'Backend did not start. Read logs/backend-error.log.'}
Start-Managed 'companion' $paths.node '--preserve-symlinks-main --preserve-symlinks index.js' (Join-Path $Root 'degen/discord-bot')
Import-Env (Join-Path $Root 'bridge.env')
$javaOptions='-Dnogui=true --enable-native-access=ALL-UNNAMED'
foreach($entry in @(@{Key='coOwnerId';Property='bot.coOwnerId'},@{Key='musicOnlyChannelId';Property='bot.musicOnlyChannelId'},@{Key='redirectChannelId';Property='bot.redirectChannelId'})) {
    $value=$settings.($entry.Key)
    if ($value) {
        if ($value -notmatch '^\d{17,20}$') { throw "Invalid optional ID: $($entry.Key)" }
        $javaOptions+=" -D$($entry.Property)=$value"
    }
}
Start-Managed 'musicbot' $paths.java "$javaOptions -jar `"$Root/WhaleMusic.jar`"" $Root
Write-Host 'WhaleMusic is starting. Pokemon data can take a few minutes to warm up. Logs are in the logs folder.'
Write-Host 'Use stop.cmd to stop this installation. You do not need to restart Windows.'
