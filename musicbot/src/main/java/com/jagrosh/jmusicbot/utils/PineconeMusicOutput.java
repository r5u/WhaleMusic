package com.jagrosh.jmusicbot.utils;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

public final class PineconeMusicOutput {
    public static final long CHANNEL_ID = Long.getLong("bot.musicOnlyChannelId", 0L);

    private PineconeMusicOutput() {}

    public static String oneLine(String text) {
        String line = text.replaceAll("\\s+", " ").strip();
        return line.length() <= 300 ? line : line.substring(0, 297) + "...";
    }

    public static MessageCreateData nowPlaying(AudioTrack track) {
        return MessageCreateData.fromContent(track == null ? "No music playing."
                : "Now playing: " + oneLine(FormatUtil.filter(FormatUtil.getTrackTitle(track))));
    }
}
