package com.jagrosh.jmusicbot.commands.v1.dj;

import com.jagrosh.jmusicbot.Bot;

/** An explicit DJ command for skipping the current song and every queued song. */
public final class SkipAllCmd extends StopCmd {
    public SkipAllCmd(Bot bot) {
        super(bot);
        this.name = "skipall";
        this.help = "skips the current song and clears every queued song (DJ only)";
        this.aliases = new String[]{"clearqueue"};
    }
    @Override public void doCommand(com.jagrosh.jdautilities.command.CommandEvent event) {
        bot.getMusicService().skipAll(event.getGuild());
        event.replySuccess("Skipped all songs and cleared the queue.");
    }
}
