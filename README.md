# WhaleMusic

A Discord music bot with a beer collecting game, Degen games, CS2 cases, Pokémon, and weather.

**Prefix:** a backtick: `` ` ``. Examples: `` `p song name ``, `` `beer ``, `` `degen ``.

## Download and run

1. Open **[Releases](https://github.com/r5u/WhaleMusic/releases)** and download **WhaleMusic-v1.0.0-Windows.zip**.
2. Extract it to a normal folder, such as `C:\Bots\WhaleMusic`.
3. Install [Python 3.10 or newer](https://www.python.org/downloads/windows/). Enable **Add Python to PATH**.
4. Copy `settings.json.example` to `settings.json`. Fill in your Discord bot token, application ID, owner ID, and server ID.
5. Double-click **setup.cmd**. It downloads Java, Node.js and MongoDB and installs the game dependencies.
6. Double-click **start.cmd**, then **register-commands.cmd** once to register the Degen commands.

**Stop everything:** double-click **stop.cmd**. No Windows restart is needed.

Need a Discord bot token or help inviting the bot? Follow **[Setup](docs/SETUP.md)** first.

## What is included

| Feature | Examples |
| --- | --- |
| Music, queues and DJ controls | `` `p song ``, `` `queue ``, `` `skipall `` |
| Spotify and Apple Music matching | `` `p <track, album, or public playlist link> `` |
| Beer, ranks and glass upgrades | `` `beer ``, `` `drink ``, `` `shop ``, `` `beerhelp `` |
| Separate game points and beer balances | `` `convertdegen 10000 ``, `` `convertml 100 `` |
| Degen games, bets and CS2 cases | `` `degen ``, `` `d bal ``, `` `d cs2open recoil `` |
| Pokémon bush encounters | `` `bush ``, `` `pokecatch `` or react 🔴 |
| Weather with humidity and wind | `` `weather Detroit,US `` |

React **❌** on a queue message to skip the current song and clear the queue. Only DJs, bot owners, and users with Manage Server can do this. Queue reactions expire after one minute.

## Guides

- **[Setup](docs/SETUP.md)** - bot account, settings, installation and first start.
- **[Commands](docs/COMMANDS.md)** - music, beer, games and permissions.
- **[Streaming links](docs/STREAMING.md)** - Spotify, Apple Music and account authorization.
- **[Operations](docs/OPERATIONS.md)** - stop, restart, update, backups and common fixes.
- **[Build from source](docs/BUILD.md)** - Java build and project layout.
- **[Optional web dashboard](docs/DASHBOARD.md)** - separate setup; not needed for Discord commands.
- **[Third-party credits](THIRD_PARTY_NOTICES.md)**.

## Important details

- This release starts with a **new, empty database**. Your running bot's player balances, inventories, settings and tokens are not uploaded.
- Keep `settings.json`, `.env`, `spotify.properties`, `weather-key.txt`, and the database private. The supplied ignore rules exclude them.
- The bot and game services run on your PC. The backend and database listen only on localhost.
- Spotify account authorization is optional and is **not preconfigured**. Public Spotify links work through metadata matching. Full playlist API access depends on Spotify's account and app restrictions.
- Server-specific channel rules and an optional co-owner are disabled until you configure them. See [Commands](docs/COMMANDS.md#server-specific-behavior) before using this in another server.
- The removed FM bot is not included.
