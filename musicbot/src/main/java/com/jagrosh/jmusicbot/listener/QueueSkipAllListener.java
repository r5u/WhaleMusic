package com.jagrosh.jmusicbot.listener;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.commands.v1.DJCommand;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/** Only explicitly registered queue messages accept the DJ skip-all reaction. */
public final class QueueSkipAllListener extends ListenerAdapter {
    private record Control(long guildId, long expiresAt) {}
    private static final ConcurrentHashMap<Long, Control> controls = new ConcurrentHashMap<>();
    private final Bot bot;
    public QueueSkipAllListener(Bot bot) { this.bot = bot; }

    public static void attach(Message message) {
        if (!message.isFromGuild()) return;
        long now = System.nanoTime();
        controls.entrySet().removeIf(entry -> now - entry.getValue().expiresAt() >= 0);
        controls.put(message.getIdLong(), new Control(message.getGuildIdLong(), now + TimeUnit.MINUTES.toNanos(1)));
        message.addReaction(Emoji.fromUnicode("❌")).queue(null, ignored -> controls.remove(message.getIdLong()));
    }

    @Override public void onMessageReactionAdd(MessageReactionAddEvent event) {
        if (!event.isFromGuild() || !"❌".equals(event.getEmoji().getName())) return;
        Control control = controls.get(event.getMessageIdLong());
        if (control == null || control.guildId() != event.getGuild().getIdLong()) return;
        if (System.nanoTime() - control.expiresAt() >= 0) { controls.remove(event.getMessageIdLong(), control); return; }
        if (event.getUserIdLong() == event.getJDA().getSelfUser().getIdLong()) return;
        event.getGuild().retrieveMemberById(event.getUserIdLong()).queue(member -> skip(event, member, control), ignored -> {});
    }

    private void skip(MessageReactionAddEvent event, Member member, Control control) {
        if (System.nanoTime() - control.expiresAt() >= 0) { controls.remove(event.getMessageIdLong(), control); return; }
        if (member.getUser().isBot() || !DJCommand.checkDJPermission(bot, event.getGuild(), member)) return;
        if (!controls.remove(event.getMessageIdLong(), control)) return;
        bot.getMusicService().skipAll(event.getGuild());
        event.getChannel().editMessageById(event.getMessageIdLong(), "⏭️ Skipped all songs and cleared the queue.")
                .setEmbeds(List.of()).setComponents(List.of()).queue(null, ignored -> {});
        event.retrieveMessage().queue(message -> message.clearReactions().queue(null, ignored -> {}), ignored -> {});
    }
}
