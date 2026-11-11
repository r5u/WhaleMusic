# Release verification

The v1.0.0 release was checked with:

- Full playback of the reported SoundCloud track, plus cloning and saved-track decoding.
- Spotify metadata for a track, album and public playlist; actual YouTube audio and the SoundCloud fallback resolver.
- Apple Music song, album and public playlist metadata; actual matched audio and saved-track decoding.
- DJ-only skip-all command and queue-reaction permission/lifecycle tests with mocked Discord objects, including non-DJ denial, owner access, expiry and duplicate clicks.
- Six-page Degen directory limits, public/owner help visibility, and new common beer pour ranges.
- Both currency conversion directions and full-glass rejection in an isolated temporary MongoDB database.
- New-install catalog/owner bootstrap run twice in an isolated database, preserving an existing test balance.
- Portable PowerShell syntax and official download endpoints.
- Source and compiled-JAR checks for local credentials, personal Discord IDs and user progress files.

The complete upstream Java test suite, an end-to-end fresh Windows VM install, and live Discord reaction interactions were not run for this release. The optional inherited web dashboard is included as source and has not received an end-to-end deployment test. Spotify account API setup still requires the user's own eligible app/account authorization.
