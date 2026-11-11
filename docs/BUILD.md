# Build from source

The release ZIP already includes `WhaleMusic.jar`. You do not need Maven just to run it.

## Requirements

- JDK 25
- Maven 3.9 or newer
- Internet access for Maven dependencies

From the repository root:

```powershell
mvn -f musicbot/pom.xml clean package -DskipTests
Copy-Item musicbot/target/JMusicBot-0.7.1-All.jar WhaleMusic.jar
```

For the full upstream Java test suite, omit `-DskipTests`. Tests that depend on live services require suitable access. The released binary was verified with focused playback, help, wallet and DJ control checks; the complete upstream suite was not run for this release.

The Node backend and companion are JavaScript; no Java compilation is involved for those folders. Run `setup.cmd` to install their locked dependencies.

## Layout

| Path | Contents |
| --- | --- |
| `musicbot/` | Java MusicBot source, custom beer/weather games and streaming resolvers |
| `degen/backend/` | Local wallet/game API and MongoDB models |
| `degen/discord-bot/` | Prefix/slash games, reactions and Pokémon |
| `degen/frontend/` | Optional inherited web dashboard |
| `scripts/` | Portable Windows setup/start/stop/account connection tools |
| `docs/` | Setup, commands and maintenance guides |
| `runtime/`, `data/`, `logs/`, `state/` | Generated locally, ignored by Git |

## Release contents

`WhaleMusic-v1.0.0-Windows.zip` contains the clean project plus the built JAR. Java, Node.js, MongoDB, Python packages and `node_modules` are installed during setup rather than bundled. A SHA-256 checksum file accompanies the release.

No live tokens, API keys, databases, balances, inventories, or FM bot files belong in a source commit or release archive.
