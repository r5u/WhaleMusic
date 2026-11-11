# Optional web dashboard

The inherited frontend source is included for completeness. It is not needed to run Discord music, beer or Degen commands, and is not started by `start.cmd`.

To try it locally after normal setup:

1. Enter the application's real Discord OAuth client secret in `settings.json` and rerun `setup.cmd`.
2. In the Discord Developer Portal, add `http://localhost:3000/auth/callback` to OAuth2 redirect URLs.
3. Create `degen/frontend/.env` with:

```dotenv
REACT_APP_API_URL=http://localhost:3078
REACT_APP_MAIN_GUILD_ID=YOUR_SERVER_ID
```

4. In `degen/frontend`, run `npm ci`, then `npm start` with Node.js 22.
5. Open `http://localhost:3000`.

The frontend has inherited dependencies and integrations and has not received an end-to-end deployment test in this release. Keep it local. Production hosting needs its own authentication, HTTPS, environment and security review. Use the Discord commands for the supported day-to-day setup.
