# Setup

## 1. Create or reuse a Discord bot

1. Open the [Discord Developer Portal](https://discord.com/developers/applications).
2. Open your application, or create one named WhaleMusic.
3. Copy the **Application ID** from General Information.
4. In **Bot**, copy or reset the **bot token**. Treat it like a password.
5. Enable **Message Content Intent** and **Server Members Intent**. The games read prefix commands and look up members.
6. Use **OAuth2 → URL Generator**, select `bot` and `applications.commands`, then invite it to your server.

Give it the permissions needed for its features: View Channels, Send Messages, Read Message History, Embed Links, Attach Files, Add Reactions, Connect, Speak, and Use Voice Activity. Manage Messages lets it clean up reactions. The inherited timeout features need Moderate Members and suitable role order. Server moderation commands also require the caller's appropriate permissions. You do not need to give the bot Administrator just to play music or beer.

In Discord, enable **User Settings → Advanced → Developer Mode**. Right-click your account and server to copy their IDs.

## 2. Fill in one settings file

Copy `settings.json.example` to `settings.json` and open it in Notepad.

| Setting | What to enter |
| --- | --- |
| `botToken` | Discord bot token, not a personal account token |
| `applicationId` | Application ID from the developer portal |
| `ownerId` | Your Discord user ID |
| `guildId` | Server ID where game slash commands will be registered |
| `coOwnerId` | Optional additional trusted owner ID; blank disables it |
| `musicOnlyChannelId` | Optional channel for compact music output and gambling restrictions |
| `redirectChannelId` | Optional destination for the configured gambling-command mention burst |
| `weatherApiKey` | Optional [OpenWeather](https://openweathermap.org/api) key; blank disables weather lookups |
| `discordClientSecret` | Optional Discord OAuth client secret, used only for the web dashboard |

Leave quotes around the values. Do not add a comma after the final setting.

All Discord IDs are blank or example labels. Fill in your own. No live Discord IDs, tokens or API keys are shipped.

## 3. Run setup

On Windows 10/11 x64:

1. Install [Python 3.10+](https://www.python.org/downloads/windows/) with **Add Python to PATH**.
2. Double-click `setup.cmd`.
3. Wait for the downloads and dependency installation to finish.

Setup downloads Java 25 when needed, Node.js 22.20.0, and MongoDB 8.0.16 into `runtime/`. It installs Python Pillow for white-background CS2 thumbnails. It generates local bridge, session and JWT secrets, plus the backend/game environment files and MusicBot `config.txt`.

Setup preserves an existing `config.txt`, existing game data and generated secrets. If you edit the token or prefix after first setup, update the corresponding values in `config.txt` too. Rerun setup to update the companion's generated `.env` files.

## 4. Start

1. Run `start.cmd`.
2. Run `register-commands.cmd` once for a new server, and again after game command registration changes.
3. Join a Discord voice channel and try `` `p Rick Astley Never Gonna Give You Up ``.
4. Try `` `beer ``, `` `beerhelp ``, and `` `degen ``.

First startup initializes a local MongoDB replica set, game catalogs and owner access. Pokémon data can take several minutes to warm up. Redis is not needed. Run one installation at a time because the ports are fixed.

## DJ access

Use the MusicBot `setdj` command to select a DJ role. MusicBot's normal help shows the exact syntax for the configured prefix. Users with that role, bot owners, and users with Manage Server can use `skipall` and the queue's ❌ reaction.

## Optional accounts

- Weather needs your OpenWeather key in `settings.json`.
- YouTube works without signing in for accessible tracks; some videos may need MusicBot's existing YouTube OAuth setup. Do not share `youtubetoken.txt`.
- Spotify API access needs your own developer app and account authorization. See [Streaming](STREAMING.md).
- Apple Music public metadata needs no account token. Audio comes from matching enabled sources.
- Some Pokémon TCG requests benefit from `POKEMON_TCG_API_KEY` in the backend `.env`. It is optional and not included.

## Existing bot users

This download is a portable installation, not a backup of your live player data. Stop the old installation before starting this one. Do not copy a running MongoDB data folder. See [Operations](OPERATIONS.md) for backup and migration notes.
