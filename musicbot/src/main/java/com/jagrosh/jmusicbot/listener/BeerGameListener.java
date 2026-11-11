package com.jagrosh.jmusicbot.listener;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import com.jagrosh.jmusicbot.config.io.ConfigIO;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.LoggerFactory;

public final class BeerGameListener extends ListenerAdapter {
    private static final List<String> RANKS =
            List.of("Sober", "Tipsy", "Alcoholic", "Bar Regular", "Drunk Loser", "Beer Legend",
                    "Keg Captain", "Brewmaster", "Baron of Beer", "King of the Kegs", "Beer Immortal");
    private record Beer(String name, int ml) {}
    private record Rarity(String name, String emoji, int weight, List<Beer> beers) {}

    private static final List<Rarity> RARITIES = List.of(
            new Rarity("Common", "🔵 🫗", 40, List.of(new Beer("Hurricane High Gravity", 10),
                    new Beer("Coors Light", 9), new Beer("Bud Light", 8),
                    new Beer("Natural Ice", 7), new Beer("Michelob Ultra", 6),
                    new Beer("Busch Light", 7),
                    new Beer("Busch", 6),
                    new Beer("Coors Banquet", 8),
                    new Beer("Natural Light", 7),
                    new Beer("Budweiser", 10),
                    new Beer("Miller Lite", 9),
                    new Beer("Voodoo Ranger Juice Force IPA", 10),
                    new Beer("Two Hearted IPA", 10),
                    new Beer("Bud Light Chelada", 8),
                    new Beer("Budweiser Chelada", 9),
                    new Beer("Modelo Chelada Especial", 10))),
            new Rarity("Uncommon", "🔵 🍺", 30, List.of(new Beer("Pabst Blue Ribbon", 13),
                    new Beer("Miller High Life", 15), new Beer("Corona Extra", 17),
                    new Beer("Yuengling Traditional Lager", 16),
                    new Beer("Heineken", 15),
                    new Beer("Stella Artois", 14),
                    new Beer("Dos Equis Lager Especial", 17),
                    new Beer("Corona Light", 13),
                    new Beer("Modelo Especial", 16))),
            new Rarity("Rare", "🟣 🍺🍺", 20, List.of(new Beer("Guinness Draught", 22),
                    new Beer("Blue Moon Belgian White", 26), new Beer("Sierra Nevada Pale Ale", 30),
                    new Beer("Samuel Adams Boston Lager", 24),
                    new Beer("New Belgium Fat Tire", 25),
                    new Beer("Bell's Two Hearted Ale", 29),
                    new Beer("Lagunitas IPA", 23),
                    new Beer("Sapporo Premium", 27),
                    new Beer("Pacifico Clara", 28))),
            new Rarity("Very Rare", "🩷 🍺🍺🍺", 10, List.of(new Beer("Modelo Negra", 40),
                    new Beer("Duvel", 48), new Beer("Chimay Blue", 55),
                    new Beer("Hoegaarden White", 52),
                    new Beer("Leffe Blonde", 50),
                    new Beer("Guinness Extra Stout", 54),
                    new Beer("Paulaner Hefe-Weizen", 45),
                    new Beer("Samuel Adams OctoberFest", 55))),
            new Rarity("Epic", "🔴 ✨🍺", 5, List.of(new Beer("Russian River Pliny the Elder", 75),
                    new Beer("Goose Island Bourbon County Brand Stout", 90),
                    new Beer("Founders Kentucky Breakfast Stout", 82),
                    new Beer("Goose Island Bourbon County Barleywine", 88),
                    new Beer("Stella Artois Midnight Lager", 90),
                    new Beer("Heineken Silver", 78))),
            new Rarity("Legendary", "🟡 🍻", 2, List.of(new Beer("Guinness Foreign Extra Stout", 130),
                    new Beer("Samuel Adams Utopias", 160),
                    new Beer("Russian River Pliny the Younger", 145),
                    new Beer("Samuel Adams Triple Bock", 155),
                    new Beer("Sierra Nevada Bigfoot", 150))));
    private static final int TOTAL_WEIGHT = RARITIES.stream().mapToInt(Rarity::weight).sum();
    private static final List<String> GLASSES = List.of(
            "Standard Glass", "Tall Glass", "Pint Glass", "Beer Stein", "Das Boot", "Mega Boot");
    private static final int HOUR = 3600;
    private static final int RESET_COST = 10;
    private static final Set<String> COMMANDS = Set.of(
            "`beer", "`beerity", "`shop", "`buyglass", "`cooldown", "`cdr", "`drink", "`pour", "`setml", "`removeml", "`convert", "`convertdegen", "`convertml", "`gamble", "`balance", "`bal", "`leaderboard", "`leaderboardreset", "`degen", "`beerhelp");

    private final long ownerId;

    public BeerGameListener(long ownerId) {
        this.ownerId = ownerId;
    }

    private final BeerWalletBridge wallet = new BeerWalletBridge();
    private final Path saveFile = progressPath();

    private static Path progressPath() {
        String override = System.getProperty("beer.progress.file");
        if (override != null && !override.isBlank()) {
            return Path.of(override).toAbsolutePath();
        }
        Path legacy = Path.of("beer-progress.properties").toAbsolutePath();
        return Files.notExists(legacy)
                ? ConfigIO.getConfigPath().toAbsolutePath().resolveSibling(legacy.getFileName())
                : legacy;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!event.isFromGuild() || event.getAuthor().isBot() || event.isWebhookMessage()) {
            return;
        }
        String[] parts = event.getMessage().getContentRaw().strip().toLowerCase(Locale.ROOT).split("\\s+", 2);
        if (parts[0].equals("`d")) {
            parts[0] = "`degen";
        }
        if (!COMMANDS.contains(parts[0])) {
            suggestCommand(event, parts[0]);
            return;
        }
        if ((parts[0].equals("`degen") && parts.length > 1) || ((parts[0].equals("`degen") || parts[0].equals("`gamble"))
                && event.getChannel().getIdLong() == com.jagrosh.jmusicbot.utils.PineconeMusicOutput.CHANNEL_ID)) {
            return;
        }
        String argument = parts.length == 2 ? parts[1] : "";
        if (!validSyntax(parts[0], argument)) {
            event.getMessage().reply("🍺 [Beer] Try this format: " + usage(parts[0]))
                    .setAllowedMentions(Set.of()).mentionRepliedUser(false).queue();
            return;
        }
        if (parts[0].equals("`beerhelp")) {
            event.getChannel().sendMessageEmbeds(beerHelp(isHelpOwner(event.getAuthor().getIdLong())))
                    .setAllowedMentions(Set.of()).queue();
            return;
        }
        String key = event.getGuild().getId() + "." + event.getAuthor().getId() + ".";
        String name = event.getMember() == null ? event.getAuthor().getName() : event.getMember().getEffectiveName();
        String reply;
        try {
            if (parts[0].equals("`pour") || parts[0].equals("`setml")
                    || (parts[0].equals("`removeml") && !argument.matches("[0-9]+"))) {
                var users = event.getMessage().getMentions().getUsers();
                if (users.size() != 1 || users.getFirst().isBot()
                        || !argument.matches("<@!?[0-9]+>\\s+[0-9]+")) {
                    argument = "";
                }
            }
            reply = play(parts[0], argument, key, name);
        } catch (IOException | IllegalArgumentException | IndexOutOfBoundsException | SecurityException ex) {
            LoggerFactory.getLogger(BeerGameListener.class).error("Unable to access beer game progress at {}", saveFile, ex);
            reply = "Beer progress could not be loaded or saved. Try again later.";
        }
        if (parts[0].equals("`degen") && reply.startsWith("Gambling platform")) {
            List<String> pages = degenPages(reply);
            event.getChannel().sendMessageEmbeds(degenPage(pages, 0, isHelpOwner(event.getAuthor().getIdLong())))
                    .setComponents(degenButtons(event.getAuthor().getId(), 0, pages.size()))
                    .setAllowedMentions(Set.of()).queue();
        } else {
            event.getChannel().sendMessage("🍺 [Beer] " + reply).setAllowedMentions(Set.of()).queue();
        }
    }

    private static boolean validSyntax(String command, String arg) {
        return switch (command) {
            case "`pour", "`setml" -> arg.matches("<@!?[0-9]+>\\s+[0-9]+");
            case "`removeml" -> arg.matches("(?:<@!?[0-9]+>\\s+)?[0-9]+");
            case "`convert", "`convertdegen", "`convertml" -> arg.matches("[0-9]+");
            case "`gamble" -> arg.equals("all");
            case "`leaderboardreset" -> arg.isEmpty() || arg.equals("confirm");
            default -> arg.isEmpty();
        };
    }

    private static String usage(String command) {
        return switch (command) {
            case "`pour", "`setml" -> command + " @user 50";
            case "`removeml" -> command + " 50 or " + command + " @user 50";
            case "`convert", "`convertdegen" -> command + " 10000";
            case "`convertml" -> command + " 100";
            case "`gamble" -> command + " all";
            case "`leaderboardreset" -> command + " confirm";
            case "`weather" -> command + " Boston,US";
            case "`degen" -> command + " cs2cases";
            default -> command;
        };
    }

    private static void suggestCommand(MessageReceivedEvent event, String input) {
        if (!input.matches("`[a-z][a-z0-9-]{2,30}") || input.equals("`weather")) {
            return;
        }
        String closest = null;
        int best = 3;
        var candidates = new java.util.TreeSet<>(COMMANDS);
        candidates.add("`weather");
        for (String candidate : candidates) {
            int distance = editDistance(input, candidate);
            if (distance < best) {
                closest = candidate;
                best = distance;
            }
        }
        if (closest != null && best <= (input.length() <= 5 ? 1 : 2)) {
            event.getMessage().reply("🍺 [Beer] Did you mean " + usage(closest) + "?")
                    .setAllowedMentions(Set.of()).mentionRepliedUser(false).queue();
        }
    }

    private static int editDistance(String left, String right) {
        int[] row = java.util.stream.IntStream.rangeClosed(0, right.length()).toArray();
        for (int i = 0; i < left.length(); i++) {
            int[] next = new int[right.length() + 1];
            next[0] = i + 1;
            for (int j = 0; j < right.length(); j++) {
                next[j + 1] = Math.min(Math.min(row[j + 1] + 1, next[j] + 1),
                        row[j] + (left.charAt(i) == right.charAt(j) ? 0 : 1));
            }
            row = next;
        }
        return row[right.length()];
    }

    private synchronized String play(String command, String arg, String key, String name) throws IOException {
        Properties data = new Properties();
        try (var input = Files.newInputStream(saveFile)) {
            data.load(input);
        } catch (NoSuchFileException ignored) {
            // First play starts with an empty save.
        }
        if (command.equals("`degen")) {
            return degenHelp();
        }
        if (wallet.enabled()) {
            data = wallet.load(key.substring(0, key.indexOf('.')), data);
        }
        long ml = value(data, key, "ml");
        String[] ids = key.split("\\.");
        boolean owner = ids[1].equals(Long.toUnsignedString(ownerId))
                || ids[1].equals(Long.toUnsignedString(com.jagrosh.jmusicbot.BotConfig.CO_OWNER_ID));
        int rank = level(data, key, "rank");
        int glass = level(data, key, "glass");
        long capacity = capacity(rank, glass);
        long now = System.currentTimeMillis() / 1000;
        long last = value(data, key, "last");
        long wait = last + HOUR - now;
        switch (command) {
            case "`beerity":
                return rarityGuide();
            case "`beer":
                if (ml >= capacity) {
                    return "Your " + GLASSES.get(glass) + " is at its " + capacity + "mL limit. " + upgradeHint(rank, glass);
                }
                if (wait > 0) {
                    return "Next pour in " + remaining(wait) + ".";
                }
                var random = ThreadLocalRandom.current();
                Rarity rarity = rollRarity(random.nextInt(TOTAL_WEIGHT));
                Beer beer = rarity.beers().get(random.nextInt(rarity.beers().size()));
                long collected = Math.min(beer.ml(), capacity - ml);
                long total = ml + collected;
                data.setProperty(key + "last", Long.toString(now));
                save(data, key, total);
                return "[%s] %s -> %s! (+%dmL) %s %s | %d total! | 1 hour cooldown..."
                        .formatted(RANKS.get(rank), name, beer.name(), beer.ml(),
                                rarity.emoji(), rarity.name(), total)
                        + (total == capacity ? " Glass full! Collected " + collected + "mL of that pour. "
                                + upgradeHint(rank, glass) : "");
            case "`shop":
                StringBuilder shop = new StringBuilder("Glass shop | ")
                        .append(GLASSES.get(glass)).append(": ").append(ml).append("/").append(capacity).append("mL")
                        .append("\nEach upgrade costs ").append(rankCost(rank) / 4).append("mL.");
                for (int tier = 1; tier < GLASSES.size(); tier++) {
                    shop.append("\n").append(tier).append(". ").append(GLASSES.get(tier))
                            .append(" - ").append(capacity(rank, tier)).append("mL")
                            .append(tier <= glass ? " (owned)" : tier == glass + 1 ? " | `buyglass" : "");
                }
                return shop.append("\nCooldown reset: 10mL | `cdr | Once per hour.").toString();
            case "`buyglass":
                if (glass == GLASSES.size() - 1) {
                    return "You already have the biggest glass for this rank.";
                }
                long price = rankCost(rank) / 4;
                if (ml < price) {
                    return "You need " + price + "mL for the " + GLASSES.get(glass + 1) + ".";
                }
                data.setProperty(key + "glass", Integer.toString(glass + 1));
                save(data, key, ml - price);
                return "Bought " + GLASSES.get(glass + 1) + "! | Capacity: " + capacity(rank, glass + 1)
                        + "mL | " + (ml - price) + "mL left.";
            case "`cdr", "`cooldown":
                if (owner) {
                    data.remove(key + "last");
                    save(data, key, ml);
                    return "Owner cooldown reset. Use `beer to pour again.";
                }
                long resetWait = value(data, key, "lastReset") + HOUR - now;
                if (resetWait > 0) {
                    return "Next cooldown reset in " + remaining(resetWait) + ".";
                }
                if (wait <= 0) {
                    return "Your glass is ready. Use `beer to pour.";
                }
                if (ml < RESET_COST) {
                    return "A cooldown reset costs 10mL. You have " + ml + "mL.";
                }
                data.remove(key + "last");
                data.setProperty(key + "lastReset", Long.toString(now));
                save(data, key, ml - RESET_COST);
                return "Cooldown reset for 10mL! Use `beer to pour again.";
            case "`leaderboard":
                return leaderboard(data, key.substring(0, key.indexOf('.') + 1));
            case "`leaderboardreset":
                if (!owner) {
                    return "Only bot owners can reset the beer leaderboard.";
                }
                if (!arg.equals("confirm")) {
                    return "This clears all beer mL, ranks, and glass upgrades in this server. "
                            + "Type `leaderboardreset confirm to proceed.";
                }
                return resetLeaderboard(data, ids[0] + ".");
            case "`pour":
                return pour(data, key, ml, arg);
            case "`convert", "`convertdegen", "`convertml":
                boolean toBeer = !command.equals("`convertml");
                long conversionAmount = positiveAmount(arg);
                if (conversionAmount <= 0 || conversionAmount > 9_007_199_254_740_991L
                        || (toBeer && conversionAmount % 100 != 0)
                        || (!toBeer && conversionAmount > 9_007_199_254_740_991L / 10)) {
                    return toBeer ? "Use `convertdegen <points> in multiples of 100. Every 100 points yields 1mL."
                            : "Use `convertml <mL> with a positive whole amount. Every 1mL yields 10 points.";
                }
                long conversionRoom = Math.max(0, capacity - ml);
                if (toBeer && conversionAmount / 100 > conversionRoom) {
                    return "That would add " + (conversionAmount / 100) + "mL. Your glass has " + ml + "/" + capacity
                            + "mL, with " + conversionRoom + "mL free. "
                            + (conversionRoom > 0 ? "Try `convertdegen " + (conversionRoom * 100) + " to fill the available space. " : "Your glass is full. ")
                            + upgradeHint(rank, glass) + " No Degen points were spent.";
                }
                if (!(data instanceof BeerWalletBridge.Snapshot snapshot)) {
                    return "Degen conversion needs the wallet service.";
                }
                return wallet.convert(snapshot, ids[1], conversionAmount, capacity, toBeer);
            case "`gamble":
                if (!arg.equals("all")) {
                    return "Use `gamble all to bet your entire balance.";
                }
                if (ml <= 0) {
                    return "You need some beer to gamble.";
                }
                if (ml > capacity / 2) {
                    return "Your glass cannot hold a doubled balance. " + upgradeHint(rank, glass);
                }
                boolean won = ThreadLocalRandom.current().nextBoolean();
                long winnings = won ? ml * 2 : 0;
                save(data, key, winnings);
                return (won ? "🎉 You doubled your beer!" : "💥 You lost your bet!")
                        + " | " + winnings + "mL left.";
            case "`setml":
                if (!owner) {
                    return "Only bot owners can set balances.";
                }
                if (arg.isEmpty()) {
                    return "Use `setml @user <mL>.";
                }
                String[] target = arg.split("\\s+", 2);
                long newBalance;
                try {
                    newBalance = Long.parseLong(target[1]);
                } catch (NumberFormatException ex) {
                    return "Enter a whole amount between 0 and 9007199254740991mL.";
                }
                if (newBalance < 0 || newBalance > 9_007_199_254_740_991L) {
                    return "Enter a whole amount between 0 and 9007199254740991mL.";
                }
                String targetId = target[0].replaceAll("[^0-9]", "");
                String targetKey = ids[0] + "." + targetId + ".";
                long targetCapacity = capacity(level(data, targetKey, "rank"), level(data, targetKey, "glass"));
                if (newBalance > targetCapacity) {
                    return "That player's glass holds up to " + targetCapacity + "mL.";
                }
                save(data, targetKey, newBalance);
                return "Set <@" + targetId + ">'s balance to " + newBalance + "mL.";
            case "`removeml":
                if (!owner) {
                    return "Only bot owners can remove mL.";
                }
                if (arg.isEmpty()) {
                    return "Use `removeml <mL> for yourself or `removeml @user <mL>.";
                }
                String[] removal = arg.split("\\s+", 2);
                String recipient = removal.length == 1 ? ids[1] : removal[0].replaceAll("[^0-9]", "");
                String recipientKey = ids[0] + "." + recipient + ".";
                long amount = positiveAmount(removal[removal.length - 1]);
                long available = value(data, recipientKey, "ml");
                if (amount <= 0 || amount > available) {
                    return "Enter a positive whole amount up to the player's balance of " + available + "mL.";
                }
                long remaining = available - amount;
                save(data, recipientKey, remaining);
                return "Removed " + amount + "mL from <@" + recipient + ">. | " + remaining + "mL left.";
            case "`drink":
                if (rank == RANKS.size() - 1) {
                    return RANKS.getLast() + " is the highest rank.";
                }
                String next = RANKS.get(rank + 1);
                long cost = rankCost(rank);
                if (!arg.isEmpty()) {
                    return "Use `drink to spend " + cost + "mL and become " + next + ".";
                }
                if (ml < cost) {
                    return "You need " + cost + "ML to become " + next + ". You have " + ml + "ML.";
                }
                data.setProperty(key + "rank", Integer.toString(rank + 1));
                data.remove(key + "glass");
                save(data, key, ml - cost);
                return "[" + next + "] Rank unlocked! 🍻 | " + (ml - cost) + "mL left | New standard glass: "
                        + capacity(rank + 1, 0) + "mL.";
            case "`balance", "`bal":
                return "[" + RANKS.get(rank) + "] " + ml + "/" + capacity + "mL | " + GLASSES.get(glass)
                        + (ml >= capacity ? " is full. " + upgradeHint(rank, glass) : ".");
            case "`degen":
                return degenHelp();
            default:
                return helpText(owner);
        }
    }

    private boolean isHelpOwner(long userId) {
        return userId == ownerId || userId == com.jagrosh.jmusicbot.BotConfig.CO_OWNER_ID;
    }

    public static String helpText() {
        return helpText(false);
    }

    public static String helpText(boolean owner) {
        String help = """
                **Pour & Progress**
                🍺 `` `beer `` - Get a random pour once an hour.
                💰 `` `balance `` / `` `bal `` - View mL, rank, and glass capacity.
                🌈 `` `beerity `` - Browse brands, rarities, and pour sizes.
                🏆 `` `leaderboard `` - See the top 10 players.

                **Glass & Rank Upgrades**
                🛒 `` `shop `` - View glass upgrades and prices.
                🍶 `` `buyglass `` - Buy the next glass size.
                🍻 `` `drink `` - Spend mL to unlock the next rank.
                ⏳ `` `cdr `` - Reset the pour timer for 10mL, once an hour.

                **Share & Exchange**
                🫗 `` `pour @user 50 `` - Give someone 50mL.
                🔄 `` `convertdegen 10000 `` - Spend 10,000 points for 100mL.
                🪙 `` `convertml 100 `` - Spend 100mL for 1,000 points.

                **Games & Help**
                🎰 `` `gamble all `` - 50/50 chance to double or lose your mL.
                🎲 `` `degen `` / `` `d `` - Browse Degen games.
                📖 `` `beerhelp `` - Show this guide.
                """;
        if (owner) help += """

                **Bot Owner Tools**
                🛠️ `` `setml @user 100 `` - Set a player's mL balance.
                ➖ `` `removeml @user 50 `` - Remove 50mL; omit @user for yourself.
                🧹 `` `leaderboardreset confirm `` - Reset this server's beer balances and ranks.
                """;
        return help;
    }

    private static MessageEmbed beerHelp(boolean owner) {
        EmbedBuilder embed = new EmbedBuilder().setColor(0xf1c40f).setTitle("🍺 Beer Game")
                .setDescription("Pour beer, collect rare brands, and upgrade your glass.\nYour rank and glass set your mL limit.")
                .setFooter("Amounts shown are examples. Mention a real player when sharing beer.");
        String heading = null;
        StringBuilder commands = new StringBuilder();
        for (String line : helpText(owner).split("\\R")) {
            if (line.startsWith("**") && line.endsWith("**")) {
                if (heading != null) embed.addField(heading, commands.toString().strip(), false);
                heading = line.substring(2, line.length() - 2);
                commands.setLength(0);
            } else if (!line.isBlank()) commands.append(line).append('\n');
        }
        if (heading != null) embed.addField(heading, commands.toString().strip(), false);
        return embed.build();
    }

    private static List<String> degenPages(String reply) {
        String directory = reply.substring("Gambling platform\n".length());
        List<String> pages = List.of(directory.strip().split("\\R===\\R"));
        if (pages.size() != 6) {
            throw new IllegalArgumentException("The Degen directory must contain six pages.");
        }
        return pages;
    }

    private static MessageEmbed degenPage(List<String> pages, int index, boolean owner) {
        String[] lines = pages.get(index).split("\\R");
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(0x0099ff)
                .setTitle("😄 Degen - " + lines[0])
                .setDescription(lines[1])
                .setFooter("Page " + (index + 1) + "/6 • Use `d instead of `degen for a shorter command.");
        String heading = null;
        StringBuilder commands = new StringBuilder();
        for (int i = 2; i < lines.length; i++) {
            if (lines[i].startsWith("## ")) {
                if (heading != null) embed.addField(heading, commands.toString().strip(), false);
                heading = lines[i].substring(3);
                commands.setLength(0);
            } else if (!lines[i].isBlank() && (owner || !lines[i].matches(".*`degen (changerole|spawncustompokemon|pokesteal) .*"))) {
                commands.append(lines[i]).append('\n');
            }
        }
        if (heading != null) embed.addField(heading, commands.toString().strip(), false);
        return embed.build();
    }

    private static ActionRow degenButtons(String owner, int index, int count) {
        String prefix = "beer-degen:" + owner + ":";
        return ActionRow.of(
                Button.secondary(prefix + (index - 1), Emoji.fromUnicode("⬅️")).withDisabled(index == 0),
                Button.secondary(prefix + (index + 1), Emoji.fromUnicode("➡️")).withDisabled(index == count - 1));
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String[] parts = event.getComponentId().split(":", 3);
        if (parts.length != 3 || !parts[0].equals("beer-degen")) {
            return;
        }
        if (!parts[1].equals(event.getUser().getId())) {
            event.reply("Only the person who opened this guide can turn its pages.").setEphemeral(true).queue();
            return;
        }
        try {
            List<String> pages = degenPages(degenHelp());
            int index = Integer.parseInt(parts[2]);
            if (index < 0 || index >= pages.size()) {
                event.reply("That page is unavailable.").setEphemeral(true).queue();
                return;
            }
            event.editMessageEmbeds(degenPage(pages, index, isHelpOwner(event.getUser().getIdLong())))
                    .setComponents(degenButtons(parts[1], index, pages.size())).queue();
        } catch (IOException | NumberFormatException ex) {
            event.reply("The gambling command directory is unavailable.").setEphemeral(true).queue();
        }
    }

    private static String degenHelp() throws IOException {
        try (var input = BeerGameListener.class.getResourceAsStream("/degen-directory.txt")) {
            if (input == null) throw new IOException("Degen directory resource is missing.");
            return "Gambling platform\n" + new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private String pour(Properties data, String key, long balance, String arg) throws IOException {
        if (arg.isEmpty()) {
            return "Use `pour @user <mL>.";
        }
        String[] parts = arg.split("\\s+");
        String recipient = parts[0].replaceAll("[^0-9]", "");
        String recipientKey = key.substring(0, key.indexOf('.') + 1) + recipient + ".";
        if (key.equals(recipientKey)) {
            return "Pick another player to pour for.";
        }
        long amount = positiveAmount(parts[1]);
        if (amount <= 0 || amount > balance) {
            return "Enter a positive amount up to your balance of " + balance + "mL.";
        }
        long recipientBalance = value(data, recipientKey, "ml");
        long space = capacity(level(data, recipientKey, "rank"), level(data, recipientKey, "glass"))
                - recipientBalance;
        if (amount > space) {
            return "That player's glass has room for " + Math.max(0, space) + "mL.";
        }
        data.setProperty(recipientKey + "ml", Long.toString(recipientBalance + amount));
        save(data, key, balance - amount);
        return "Poured " + amount + "mL for <@" + recipient + ">! | " + (balance - amount) + "mL left.";
    }

    private static long positiveAmount(String text) {
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static long rankCost(int rank) {
        return 1000L << rank;
    }

    private static long capacity(int rank, int glass) {
        long cap = rankCost(rank);
        for (int tier = 0; tier < glass; tier++) {
            cap = cap * 3 / 2;
        }
        return cap;
    }

    private static String upgradeHint(int rank, int glass) {
        if (glass < GLASSES.size() - 1) {
            return rank < RANKS.size() - 1 ? "Use `buyglass or `drink to make room."
                    : "Use `buyglass for a bigger glass.";
        }
        return rank < RANKS.size() - 1 ? "Use `drink for the next rank."
                : "Spend or transfer mL to make room.";
    }

    private static String rarityGuide() {
        StringBuilder guide = new StringBuilder("**Beer rarities** · Pour amounts in mL");
        for (Rarity rarity : RARITIES) {
            guide.append("\n\n").append(rarity.emoji()).append(" **").append(rarity.name()).append("**");
            for (int i = 0; i < rarity.beers().size(); i++) {
                Beer beer = rarity.beers().get(i);
                guide.append(i % 2 == 0 ? "\n" : "  |  ").append(beer.name()).append(" · ").append(beer.ml());
            }
        }
        return guide.toString();
    }

    private static Rarity rollRarity(int roll) {
        for (Rarity rarity : RARITIES) {
            roll -= rarity.weight();
            if (roll < 0) {
                return rarity;
            }
        }
        throw new IllegalArgumentException("Rarity roll out of range");
    }

    private static String remaining(long seconds) {
        return seconds / 60 + "m " + seconds % 60 + "s";
    }

    private record Score(String user, long ml, String rank) {}

    private static String leaderboard(Properties data, String guildPrefix) {
        List<Score> scores = data.stringPropertyNames().stream()
                .filter(key -> key.startsWith(guildPrefix) && key.endsWith(".ml"))
                .map(key -> new Score(key.substring(guildPrefix.length(), key.length() - 3),
                        Long.parseLong(data.getProperty(key)),
                        RANKS.get(level(data, key.substring(0, key.length() - 2), "rank"))))
                .sorted(Comparator.comparingLong(Score::ml).reversed().thenComparing(Score::user))
                .limit(10)
                .toList();
        if (scores.isEmpty()) {
            return "Nobody has poured a beer in this server yet.";
        }
        StringBuilder result = new StringBuilder("Server leaderboard by current balance");
        for (int i = 0; i < scores.size(); i++) {
            Score score = scores.get(i);
            result.append("\n").append(i + 1).append(". <@").append(score.user())
                    .append("> | ").append(score.ml()).append("mL [").append(score.rank()).append("]");
        }
        return result.toString();
    }

    private String resetLeaderboard(Properties data, String guildPrefix) throws IOException {
        var players = new java.util.HashSet<String>();
        for (String field : data.stringPropertyNames()) {
            int separator = field.lastIndexOf('.');
            if (!field.startsWith(guildPrefix) || separator < guildPrefix.length()) {
                continue;
            }
            switch (field.substring(separator + 1)) {
                case "ml", "rank", "glass" -> players.add(field.substring(0, separator + 1));
                default -> {}
            }
        }
        for (String player : players) {
            data.setProperty(player + "ml", "0");
            data.remove(player + "rank");
            data.remove(player + "glass");
        }
        if (!players.isEmpty()) {
            save(data);
        }
        return "Reset beer balances, ranks, and glasses for " + players.size() + " player(s) in this server.";
    }

    private static long value(Properties data, String key, String field) {
        return Long.parseLong(data.getProperty(key + field, "0"));
    }

    private static int level(Properties data, String key, String field) {
        return Integer.parseInt(data.getProperty(key + field, "0"));
    }

    private void save(Properties data, String key, long balance) throws IOException {
        data.setProperty(key + "ml", Long.toString(balance));
        save(data);
    }

    private void save(Properties data) throws IOException {
        if (data instanceof BeerWalletBridge.Snapshot snapshot) {
            wallet.save(snapshot);
            return;
        }
        Files.createDirectories(saveFile.getParent());
        Path temporary = Files.createTempFile(saveFile.getParent(), "beer-progress-", ".tmp");
        try {
            try (var output = Files.newOutputStream(temporary)) {
                data.store(output, "MusicBot beer game progress");
            }
            try {
                Files.move(temporary, saveFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, saveFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
