# Running and maintaining WhaleMusic

## Start, stop and restart

- **Start:** `start.cmd`.
- **Stop:** `stop.cmd`.
- **Restart:** run `stop.cmd`, wait for it to finish, then run `start.cmd`.
- **Register game commands:** `register-commands.cmd`.

The launcher records each process ID, executable path and start time. Stop only targets matching processes from this installation. It does not kill every Java, Node.js or MongoDB process on the PC. If Windows denies stopping a process, run the launcher under the same Windows account that started it. No PC restart is required.

## Logs

Open the `logs/` folder. Check `musicbot.log`, `musicbot-error.log`, `backend-error.log`, and `companion-error.log`. Logs and credentials are private local files and should not be committed.

## Back up

1. Run `stop.cmd` and confirm the processes have stopped.
2. Copy the complete installation folder to a safe location, including `data/`, `state/`, `settings.json`, `config.txt`, `serversettings.json`, game progress `.properties` files, and the companion's JSON state files.
3. Keep the backup private because it contains bot and account credentials.

For an active server, use MongoDB's `mongodump` tools instead of copying live database files. Database tools are a separate optional download. Restore into the same MongoDB major version first.

To migrate the older Desktop installation, stop its Java bot, companion, backend and MongoDB first. Its MongoDB files are under the old workspace `work/beer-db`; its MongoDB database name may differ from the new `whalemusic` default. Use a logical dump/restore or keep the old database name consistently in the backend `.env`. Also copy MusicBot config/progress and the companion's bet/reaction/cooldown JSON state. The release intentionally does not include those live files.

## Update

Stop the bot and make a backup first. Replace `WhaleMusic.jar`, source folders, scripts and documentation from the new release. Keep your private settings and database. Rerun `setup.cmd` if dependencies changed, then `register-commands.cmd` if game command definitions changed. Start again.

Do not reset the leaderboard or reinitialize the database to apply a normal update.

## Common fixes

| Problem | What to do |
| --- | --- |
| Python opens the Microsoft Store | Install Python from python.org and enable Add Python to PATH |
| Java class-version error | Run setup again so Java 25 is used |
| Port 27028 or 3078 already used | Stop the older installation; do not run two copies |
| Bot ignores prefix commands | Check the Message Content Intent, channel permissions and `config.txt` prefix |
| Degen command not registered | Run `register-commands.cmd`; check the application ID and server ID |
| Beer says wallet service is needed | Start through `start.cmd` so the bridge environment and backend are loaded |
| Glass has no room for conversion | Follow the suggested smaller conversion or increase capacity with `drink`/`buyglass`; rejected conversions spend no points |
| Pokémon data is loading | Wait for the startup cache to finish; check companion logs if it never completes |
| CS2 thumbnails show originals | Check Python/Pillow availability; image processing falls back to the source image |
| A music link is unavailable | It may be restricted or absent from both matching sources; try a direct accessible upload |
| Spotify only adds part of a playlist | Public previews are limited; authorize your eligible account for supported full playlists |
| PowerShell download fails | Check Internet access and extract the ZIP to a normal local folder; rerun setup after fixing the cause |

The local backend is designed for localhost. The optional dashboard is not a ready-made public hosting deployment.
