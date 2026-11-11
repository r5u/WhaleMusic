# Apple Music and Spotify

Apple Music songs and albums work with the normal play command. For example:

```
`p https://music.apple.com/us/album/never-gonna-give-you-up/1559885420?i=1559885421
```

Song links with `?i=` load that one song. Album links without `?i=` load the album. Public playlist links load the songs visible in Apple's public page, and the queue labels the listing size. Public page listings can be incomplete. Audio is matched by title, artist and duration on YouTube, then SoundCloud. No Apple account token is needed.

## Spotify app setup

1. Sign in at https://developer.spotify.com/dashboard with your Spotify account. New development apps require the owner to have Spotify Premium.
2. Create an app named loserMusic. Choose Web API. Use a description such as "Personal Discord music bot that reads playlist metadata and finds matching tracks on supported sources."
3. Add this exact Redirect URI in app settings: `http://127.0.0.1:8765/callback`.
4. Run Connect-Spotify.cmd from the bot folder after setup.cmd. It prints a local setup link. Open `http://127.0.0.1:8765/` and enter the app's Client ID. A Client Secret is not required.
5. Authorize read-only access to private and collaborative playlist metadata on Spotify's own permission screen. No playlist editing or playback-control permissions are requested.
6. The connector saves spotify.properties beside config.txt. Keep that file private. Restart MusicBot to load it. The bot renews its access token automatically.

Spotify's development-mode API returns full playlist contents only for the authorized user's own playlists. New apps do not unlock every Spotify or third-party playlist. Other public playlists fall back to their public embed preview. The bot imports at most 500 API-accessible tracks per playlist.

Do not paste passwords, verification codes, access tokens or refresh tokens into Discord. No password is required by the connector.

References: [Spotify development-mode rules](https://developer.spotify.com/documentation/web-api/concepts/quota-modes), [Spotify 2026 playlist changes](https://developer.spotify.com/documentation/web-api/references/changes/february-2026), [Apple metadata lookup](https://developer.apple.com/library/archive/documentation/AudioVideo/Conceptual/iTuneSearchAPI/LookupExamples.html).
