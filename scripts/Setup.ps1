. "$PSScriptRoot/Common.ps1"
$settings=Read-Settings
if ($settings.botToken -eq 'PASTE_DISCORD_BOT_TOKEN' -or [string]::IsNullOrWhiteSpace($settings.botToken)) { throw 'Fill in botToken in settings.json.' }
foreach($key in @('applicationId','ownerId','guildId')) { if ($settings.$key -notmatch '^\d{17,20}$') { throw "Fill in $key in settings.json with a Discord ID." } }
foreach($key in @('coOwnerId','musicOnlyChannelId','redirectChannelId')) { if ($settings.$key -and $settings.$key -notmatch '^\d{17,20}$') { throw "$key must be blank or a Discord ID." } }
foreach($name in @('runtime','data','logs','state')) { New-Item -ItemType Directory -Path (Join-Path $Root $name) -Force | Out-Null }
[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12
function Download-Zip($Url,$Name,$ExpectedHash='') {
    $destination=Join-Path $Root "runtime/$Name"
    if (!(Test-Path -LiteralPath $destination)) {
        Write-Host "Downloading $Name from the official vendor. This may take a few minutes."
        $archive=Join-Path $Root "runtime/$Name.zip"
        Invoke-WebRequest -Uri $Url -OutFile $archive -UseBasicParsing -Headers @{'User-Agent'='WhaleMusic-Setup'}
        if ($ExpectedHash -and (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ne $ExpectedHash) { throw "Checksum verification failed for $Name." }
        Expand-Archive -LiteralPath $archive -DestinationPath $destination -Force
    }
    return $destination
}
$javaCommand=Get-Command java.exe -ErrorAction SilentlyContinue
$java=$null
if ($javaCommand) { $version=(& $javaCommand.Source -version 2>&1 | Out-String); if ($version -match 'version "(2[5-9]|[3-9]\d)') { $java=$javaCommand.Source } }
if (!$java) {
    $assets=Invoke-RestMethod -Uri 'https://api.adoptium.net/v3/assets/latest/25/hotspot?architecture=x64&image_type=jdk&os=windows' -Headers @{'User-Agent'='WhaleMusic-Setup'}
    $asset=@($assets)[0]
    $folder=Download-Zip $asset.binary.package.link 'java' $asset.binary.package.checksum
    $java=(Get-ChildItem -LiteralPath $folder -Recurse -Filter java.exe | Where-Object {$_.Directory.Name -eq 'bin'} | Select-Object -First 1).FullName
}
$nodeFolder=Download-Zip 'https://nodejs.org/dist/v22.20.0/node-v22.20.0-win-x64.zip' 'node'
$node=(Get-ChildItem -LiteralPath $nodeFolder -Recurse -Filter node.exe | Select-Object -First 1).FullName
$npm=Join-Path (Split-Path $node -Parent) 'node_modules/npm/bin/npm-cli.js'
$mongoFolder=Download-Zip 'https://fastdl.mongodb.org/windows/mongodb-windows-x86_64-8.0.16.zip' 'mongo'
$mongo=(Get-ChildItem -LiteralPath $mongoFolder -Recurse -Filter mongod.exe | Select-Object -First 1).FullName
$python=Get-Command python.exe -ErrorAction SilentlyContinue
if (!$python) { throw 'Install Python 3.10 or newer from python.org with Add Python to PATH enabled, then rerun setup.cmd.' }
& $python.Source -m pip install 'Pillow==11.2.1'
if ($LASTEXITCODE) { throw 'Could not install Pillow. See docs/SETUP.md.' }
$paths=[pscustomobject]@{java=$java;node=$node;mongo=$mongo;python=$python.Source}
$paths | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $Root 'runtime/paths.json') -Encoding UTF8
foreach($part in @('backend','discord-bot')) {
    Write-Host "Installing $part dependencies..."
    Push-Location (Join-Path $Root "degen/$part")
    try { & $node $npm ci; if ($LASTEXITCODE) { throw "Dependency installation failed for $part" } } finally { Pop-Location }
}
function New-Secret { $bytes=New-Object byte[] 32; $rng=[Security.Cryptography.RandomNumberGenerator]::Create(); try {$rng.GetBytes($bytes)} finally {$rng.Dispose()}; return ([BitConverter]::ToString($bytes)).Replace('-','').ToLowerInvariant() }
$secretFile=Join-Path $Root 'state/local-secrets.json'
if (Test-Path -LiteralPath $secretFile) { $secrets=Get-Content -LiteralPath $secretFile -Raw | ConvertFrom-Json }
else { $secrets=[pscustomobject]@{bridge=(New-Secret);session=(New-Secret);jwt=(New-Secret)}; $secrets | ConvertTo-Json | Set-Content -LiteralPath $secretFile -Encoding UTF8 }
if (!$secrets.admin) { $secrets | Add-Member -NotePropertyName admin -NotePropertyValue (New-Secret) -Force; $secrets | ConvertTo-Json | Set-Content -LiteralPath $secretFile -Encoding UTF8 }
$backend=@("PORT=3078","MONGODB_URI=mongodb://127.0.0.1:27028/whalemusic?replicaSet=beer","SESSION_SECRET=$($secrets.session)","JWT_SECRET=$($secrets.jwt)","DISCORD_CLIENT_ID=$($settings.applicationId)","DISCORD_CLIENT_SECRET=$(if($settings.discordClientSecret){$settings.discordClientSecret}else{'dashboard-not-configured'})","DISCORD_CALLBACK_URL=http://localhost:3000/auth/callback","DEFAULT_GUILD_ID=$($settings.guildId)","BEER_BRIDGE_SECRET=$($secrets.bridge)","FRONTEND_URL=http://localhost:3000")
$backend | Set-Content -LiteralPath (Join-Path $Root 'degen/backend/.env') -Encoding ASCII
@("BOT_OWNER_ID=$($settings.ownerId)","BOT_CO_OWNER_ID=$($settings.coOwnerId)") | Add-Content -LiteralPath (Join-Path $Root 'degen/backend/.env') -Encoding ASCII
$backendAdmin="ADMIN_GIVE_POKEMON_SECRET=$($secrets.admin)"
$backendAdmin | Add-Content -LiteralPath (Join-Path $Root 'degen/backend/.env') -Encoding ASCII
@("DISCORD_TOKEN=$($settings.botToken)","CLIENT_ID=$($settings.applicationId)","GUILD_ID_1=$($settings.guildId)","BOT_OWNER_ID=$($settings.ownerId)","BOT_CO_OWNER_ID=$($settings.coOwnerId)","MUSIC_ONLY_CHANNEL_ID=$($settings.musicOnlyChannelId)","PYTHON_PATH=$($python.Source)","BACKEND_API_URL=http://127.0.0.1:3078/api") | Set-Content -LiteralPath (Join-Path $Root 'degen/discord-bot/.env') -Encoding ASCII
$backendAdmin | Add-Content -LiteralPath (Join-Path $Root 'degen/discord-bot/.env') -Encoding ASCII
@("BEER_BRIDGE_URL=http://127.0.0.1:3078/api/beer-wallet","BEER_BRIDGE_SECRET=$($secrets.bridge)","GUILD_ID=$($settings.guildId)") | Set-Content -LiteralPath (Join-Path $Root 'bridge.env') -Encoding ASCII
$configPath=Join-Path $Root 'config.txt'
if (!(Test-Path -LiteralPath $configPath)) {
    $config=Get-Content -LiteralPath (Join-Path $Root 'config.txt.example') -Raw
    $config=$config.Replace('BOT_TOKEN_HERE',$settings.botToken).Replace('owner = 0 # OWNER ID',"owner = $($settings.ownerId)")
    $config=$config.Replace('prefix = "@mention"',('prefix = "'+[char]96+'"'))
    [IO.File]::WriteAllText($configPath,$config,[Text.UTF8Encoding]::new($false))
}
if ($settings.weatherApiKey) { [IO.File]::WriteAllText((Join-Path $Root 'weather-key.txt'),$settings.weatherApiKey,[Text.UTF8Encoding]::new($false)) }
Write-Host 'Setup complete. Start with start.cmd. Register game commands with register-commands.cmd once.'
