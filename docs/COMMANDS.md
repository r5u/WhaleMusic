# Commands

The normal prefix is a backtick, written `` ` ``. Commands below include it so you can copy an example. Replace sample mentions and IDs with real ones. Owner-only help entries are hidden when ordinary users request help.

## Music

| Command | What it does |
| --- | --- |
| `` `p artist - song `` | Play a search result |
| `` `p <link> `` | Play YouTube, SoundCloud, Spotify or Apple Music links |
| `` `queue `` | Show the queue and a DJ-only ❌ reaction |
| `` `skip `` | Skip or vote to skip under MusicBot's normal rules |
| `` `skipall `` | DJ only: skip the current song and every queued song |
| `` `clearqueue `` | Alias for skipall |
| `` `pause `` | DJ playback control |
| `` `stop `` | Stop playback, clear the queue and leave voice |
| `` `help `` | Music and game help appropriate to your permissions |

`/skipall` is also available. The ❌ queue reaction does the same thing as skipall, without leaving voice. Only the configured DJ role, bot owners, or users with Manage Server can use it. It is active for one minute on both text and slash queue messages, including compact #pinecone output. Any DJ can use it, not only the person who opened the queue. Other users' reactions do nothing.

## Beer

- 🍺 `` `beer `` - One random pour per hour.
- 💰 `` `balance `` or `` `bal `` - Beer balance, rank and capacity.
- 🌈 `` `beerity `` - Beer brands, rarities and mL ranges.
- 🛒 `` `shop `` - Glass upgrades and prices.
- 🍶 `` `buyglass `` - Buy the next glass size.
- 🍻 `` `drink `` - Purchase the next rank.
- ⏳ `` `cdr `` - Spend 10mL to reset the pour timer, once an hour. Owners have unlimited free resets.
- 🫗 `` `pour @player 50 `` - Give another player 50mL, subject to their capacity.
- 🏆 `` `leaderboard `` - Beer leaderboard with ranks.
- 🔄 `` `convertdegen 10000 `` - Spend 10,000 Degen points for 100mL.
- 🪙 `` `convertml 100 `` - Spend 100mL for 1,000 Degen points.
- 🎰 `` `gamble all `` - 50/50 chance to double or lose the beer balance.
- 📖 `` `beerhelp `` - Grouped beer guide.

Beer and Degen points are separate balances. Beer capacity applies to pours, transfers and incoming conversions. A rejected conversion spends no points. The error message tells you how much fits and how to increase capacity.

Common pours include Hurricane High Gravity, Coors Light, Voodoo Ranger Juice Force IPA, Two Hearted IPA, Bud Light Chelada, Budweiser Chelada and Modelo Chelada Especial. Rarer brands give larger fixed mL pours. Use beerity for the full list.

## Owner tools

These are permission protected and hidden from ordinary users' help:

- `` `setml @player 100 `` - Set a beer balance within capacity.
- `` `removeml @player 50 `` - Remove beer; omit the mention to remove your own.
- `` `leaderboardreset confirm `` - Reset server beer balances, ranks and glasses. It does not reset Degen points.

## Pokémon

- 🌿 `` `bush `` - Search for a wild Pokémon, once an hour per user per server.
- 🔴 `` `pokecatch `` - Catch the active Pokémon, or react 🔴 on the spawn.
- `` `degen pokespawn `` - Admin manual spawn without a cooldown.

Automatic hourly spawns are disabled. Failed bush searches do not consume the cooldown. Existing encounters are not replaced by another bush search. Artwork is displayed as small thumbnails.

## Weather

`` `weather Detroit,US `` shows temperature, conditions, humidity and wind speed. Add an OpenWeather key during setup.

## Degen directory

Use `` `degen `` or `` `d `` for the six-page guide. Use `` `d bal `` for the Degen balance. The examples below use the longer form for clarity. Many commands need an amount, mention, choice or subcommand; the bot suggests concrete examples when input is missing or mistyped.

### Wallet & Daily Play

Build your points, check your progress, and find something to do while games cool down.


**💰 Your Wallet**

- 💰 `` `degen balance `` - Checks your current point balance
- 📈 `` `degen profile `` - View your detailed profile, including balance, betting, and gambling stats
- 💰 `` `degen transactions `` - View your transaction history
- 📈 `` `degen leaderboard `` - Shows the top users by balance

**🧰 Earn Points**

- 💰 `` `degen daily `` - Claim your daily point bonus
- 💰 `` `degen work `` - Work a job for a chance to earn points and rare bonuses
- 💰 `` `degen beg `` - Beg for coins and see what happens
- 🐾 `` `degen meowbark `` - Perform a meow or bark to earn points
- ❓ `` `degen question `` - Answer a question about a cat for a chance to win or lose points

**✨ Progress & Perks**

- ✨ `` `degen buffs `` - View your active buffs and their remaining time
- 📅 `` `degen cooldowns `` - View all your current cooldowns
- 📈 `` `degen auracheck `` - Check aura for a user
- 📈 `` `degen auraleaderboard `` - View the aura leaderboard
- 🎟️ `` `degen golden-tickets `` - Check how many golden tickets you have
- 🎟️ `` `degen redeem-golden-ticket `` - Redeem a golden ticket for 10% of the jackpot pool

### Casino & Player Bets

Pick a quick game or join a player-created event. Wagers use Degen points.


**🎰 Casino Floor**

- 🎰 `` `degen slots `` - Play the slot machine
- 🃏 `` `degen blackjack `` - Play blackjack
- 🎲 `` `degen coinflip `` - Flip a coin and bet on the outcome
- 🎲 `` `degen dice `` - Roll dice and bet on the outcome
- 🎲 `` `degen roulette `` - Play roulette
- 🎰 `` `degen jackpot `` - View or contribute to the jackpot

**🗳️ Start & Join Bets**

- 🧾 `` `degen createbet `` - Creates a new betting event
- 📋 `` `degen listbets `` - Lists all currently open betting events
- 📋 `` `degen viewbet `` - Views a concise summary of a specific betting event
- 📋 `` `degen betinfo `` - Shows detailed information and statistics about a specific bet
- 🧾 `` `degen placebet `` - Places a bet on an active event

**🧾 Manage Your Bets**

- 🧾 `` `degen editbet `` - Edits a bet's description or options before any bets are placed
- 🧾 `` `degen extendbet `` - Extends the duration of an open bet
- 🧾 `` `degen closebet `` - Closes betting for a specific event
- 🧾 `` `degen cancelbet `` - Cancels a bet before any bets are placed
- 🧾 `` `degen resolvebet `` - Resolves a betting event and distributes winnings
- 🧾 `` `degen refund `` - Refund all bets for a specific bet
- 📋 `` `degen unresolvedbets `` - Shows all bets that are unresolved
- 📈 `` `degen stats `` - Shows your full betting and gambling statistics, including win streaks, jackpots, gifts, and more

### CS2 & Collecting

Browse cases, grow a collection, and exchange items with other players.


**📦 Cases & Skins**

- 📦 `` `degen cs2cases `` - View available CS2 cases and their contents
- 📦 `` `degen cs2open `` - Open a CS2 case and get a random skin
- 🔫 `` `degen cs2inventory `` - View your CS2 skin collection and inventory
- 🔫 `` `degen cs2view `` - View detailed information about CS2 skins with search and pagination

**🔫 Skin Trading & Records**

- 🔫 `` `degen cs2sell `` - Sell a CS2 skin from your inventory
- 🔄 `` `degen cs2trade `` - Trade a CS2 skin with another user
- 🔫 `` `degen cs2stats `` - View your CS2 case opening statistics and performance
- 🔫 `` `degen cs2leaderboard `` - View the CS2 case opening leaderboard

**🎣 Gather & Collect**

- 🎣 `` `degen fish `` - Go fishing for a chance to catch something valuable
- 🏹 `` `degen hunt `` - Go hunting for a chance to catch a rare animal
- 🃏 `` `degen collection `` - View your fishing and hunting collection
- 🃏 `` `degen collection-list `` - View all possible fish and animal names in the collection
- 🃏 `` `degen collection-leaderboard `` - View the top collectors by collection value

**🎁 Share & Sell**

- 💰 `` `degen sell `` - Sell items from your collection for points
- 🔄 `` `degen trade `` - Trade items with another user
- 💰 `` `degen gift `` - Gift points to another user
- 🎰 `` `degen mysterybox `` - Open a mystery box for random rewards
- 🎁 `` `degen giveaway `` - Start a giveaway for points

### Wild Pokémon

Search a bush, react 🔴 to an encounter, then grow and show your Pokémon collection.


**🌿 Find & Catch**

- 🌿 `` `degen bush `` - Search for a wild Pokémon once per hour
- 🔴 `` `degen pokecatch `` - Try to catch the wild Pokémon in this channel
- 🔴 `` `degen pokedex `` - View your collected Pokémon
- 🔴 `` `degen pokemonshow `` - Show off one of your Pokémon publicly

**🧬 Grow Your Team**

- 🔴 `` `degen pokeevolve `` - Evolve a Pokémon using the Evolver's Ring and duplicates
- 🔴 `` `degen pokeevolveform `` - Evolve a Pokémon to any special form using Form Stones
- 🔴 `` `degen pokesellduplicates `` - Sell your duplicate Pokémon for stardust
- 🛍️ `` `degen pokeshop `` - View and buy special progression items
- 🛍️ `` `degen pokeshopdaily `` - View today's rotating Previous Generation Pokémon shop featuring Pokémon from different rarities

**🔄 Trade & Track**

- 🔄 `` `degen poketrade `` - Trade Pokémon with another user
- 🔴 `` `degen pokestats `` - View detailed EV stats for a specific Pokemon
- 🗺️ `` `degen pokequests `` - View your daily and weekly Pokémon quest progress and claim rewards
- 🛠️ `` `degen setpokedexpokemon `` - Choose which Pokémon to display in your Pokédex

### Battles, Cards & Rivals

Challenge another trainer, open card packs, or take a risk against another player.


**⚔️ Trainer Battles**

- ⚔️ `` `degen pokebattle `` - Challenge another user to a Pokémon battle
- ⚔️ `` `degen pokebattlestats `` - View Pokémon battle statistics for a user
- ⚔️ `` `degen battledexpreset `` - Set your BattleDex preset by selecting Pokémon like in pokebattle
- ⚔️ `` `degen cancelbattle `` - Cancel your active battle if it's stuck

**🃏 Pokémon Cards**

- 📦 `` `degen pokepacks `` - Browse available Pokémon TCG card packs
- 📦 `` `degen pokeopen `` - Open your purchased Pokémon TCG card packs
- 🔴 `` `degen pokecollection `` - View your Pokémon TCG card collection

**🎉 Pokémon Giveaways**

- 🎁 `` `degen giveawaypokemon `` - Start a giveaway for a Pokémon from your collection
- 🎁 `` `degen giveawaypokemonleaderboard `` - View the Pokémon giveaway leaderboard

**🚔 Risk & Rivalry**

- ⚔️ `` `degen duel `` - Challenge another user to a duel for points
- 🛡️ `` `degen crime `` - Attempt a crime for a chance to win or lose points, or get jailed
- 🛡️ `` `degen steal `` - Enhanced steal system with multiple steal types and separate cooldowns
- 🛡️ `` `degen jailed `` - View all currently jailed users in this server
- 🛡️ `` `degen bail `` - Bail a jailed user out of jail

### Server Tools & More

Restricted tools require the appropriate bot or server permissions. Open help for deeper game guides.


**🛡️ Player Controls**

- 🛡️ `` `degen timeout `` - Timeout a user for a specified duration
- 🛡️ `` `degen timeoutlist `` - List all currently timed out users in the server
- 🛡️ `` `degen untimeout `` - Reduce a bot-applied timeout for a user
- 🛠️ `` `degen changerole `` - Change a user's role
- 🛠️ `` `degen resetcooldowns `` - Reset all cooldowns

**🗓️ Server Settings**

- 🛠️ `` `degen setlogchannel `` - Set the channel where moderation logs will be sent
- 📅 `` `degen weekend `` - Manage automatic weekend events
- 📅 `` `degen doubleweekend `` - Manage double weekend events

**🧑‍🔧 Pokémon Administration**

- 🔴 `` `degen pokespawn `` - Admin: Spawn a wild current generation Pokémon in this channel
- 🛠️ `` `degen setpokechannel `` - Manage saved Pokémon channel settings; automatic spawns are disabled
- 🛠️ `` `degen spawncustompokemon `` - Give a custom Pokémon to a user
- 🔴 `` `degen pokesteal `` - Superadmin: Steal a random common Pokémon from a user

**📖 More Help**

- ❓ `` `degen help `` - Shows a help menu with all available commands

## Server-specific behavior

No personal Discord IDs are included. These features are optional in settings.json:

- `coOwnerId` adds a second trusted owner. Leave it blank for only the main owner.
- `musicOnlyChannelId` enables compact, one-line music output in that channel. Beer and weather remain allowed. Degen and gambling commands are blocked there and trigger the configured redirect response.
- `redirectChannelId` selects the destination for the existing mention-burst behavior. Leave it blank to avoid posting mentions elsewhere.
- Automatic Pokémon spawns stay disabled regardless of saved channel settings.

The configured owner is promoted to Degen superadmin during startup. No inherited account IDs are granted special access.

## Game currency

Degen points and mL are virtual game balances. Case costs are 5,000-10,000 points for normal cases, 20,000 for Weapon Cases and 15,000 for Souvenir Packages. Case names accept unique prefixes such as `` `d cs2open recoil ``. Use `` `d cs2cases `` for the four-page case list.
