package com.jagrosh.jmusicbot.listener;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public final class PineconeCommandListener extends ListenerAdapter {
    private static final long PINECONE = com.jagrosh.jmusicbot.utils.PineconeMusicOutput.CHANNEL_ID;
    private static final long PING_CHANNEL = Long.getLong("bot.redirectChannelId", 0L);
    private static final Pattern SLASH_NAME = Pattern.compile("(?:^|\\s)/([a-z0-9-]+) - ");
    private final Set<String> gamblingCommands = loadGamblingCommands();
    private final ConcurrentHashMap<Long, Long> nextBurst = new ConcurrentHashMap<>();

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getChannel().getIdLong() != PINECONE || event.getAuthor().isBot() || event.isWebhookMessage()) {
            return;
        }
        String command = event.getMessage().getContentRaw().strip().toLowerCase(Locale.ROOT).split("\\s+", 2)[0];
        if (command.equals("`degen") || command.equals("`d") || command.equals("`gamble")) {
            redirect(event.getMember(), event.getChannel(), true);
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getChannel().getIdLong() == PINECONE && gamblingCommands.contains(event.getName())) {
            redirect(event.getMember(), event.getChannel(), true);
        }
    }

    private void redirect(Member member, MessageChannel channel, boolean ping) {
        if (member == null || member.getUser().isBot()) {
            return;
        }
        String name = net.dv8tion.jda.api.utils.MarkdownSanitizer.escape(member.getEffectiveName());
        channel.sendMessage(name + ", you loser! Use the other channels for non-music bot commands.")
                .setAllowedMentions(Set.of()).queue();
        if (!ping) {
            return;
        }
        long now = System.nanoTime();
        long next = now + TimeUnit.MINUTES.toNanos(1);
        long reserved = nextBurst.compute(member.getIdLong(), (id, previous) ->
                previous == null || now - previous >= 0 ? next : previous);
        if (reserved != next) {
            return;
        }
        var destination = member.getGuild().getTextChannelById(PING_CHANNEL);
        if (destination != null && destination.canTalk()) {
            for (int i = 0; i < 10; i++) {
                destination.sendMessage(member.getAsMention())
                        .setAllowedMentions(Set.of(Message.MentionType.USER))
                        .queueAfter(i, TimeUnit.SECONDS);
            }
        }
    }

    private static Set<String> loadGamblingCommands() {
        try (var input = PineconeCommandListener.class.getResourceAsStream("/degen-commands.txt")) {
            if (input == null) {
                return Set.of();
            }
            String catalog = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            return SLASH_NAME.matcher(catalog).results().map(match -> match.group(1)).collect(Collectors.toUnmodifiableSet());
        } catch (IOException ex) {
            throw new java.io.UncheckedIOException(ex);
        }
    }
}
