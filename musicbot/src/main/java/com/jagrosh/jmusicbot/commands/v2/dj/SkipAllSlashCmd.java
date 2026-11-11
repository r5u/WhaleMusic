package com.jagrosh.jmusicbot.commands.v2.dj;

import com.jagrosh.jmusicbot.Bot;

public final class SkipAllSlashCmd extends StopSlashCmd {
    public SkipAllSlashCmd(Bot bot) {
        super(bot);
        this.name = "skipall";
        this.help = "skips the current song and clears every queued song (DJ only)";
        this.aliases = new String[0];
    }
    @Override public void doDJCommand(com.jagrosh.jdautilities.command.SlashCommandEvent event) {
        bot.getMusicService().skipAll(event.getGuild());
        event.reply(event.getClient().getSuccess() + " Skipped all songs and cleared the queue.").queue();
    }
}
