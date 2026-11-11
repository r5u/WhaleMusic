package com.jagrosh.jmusicbot.audio;

import com.sedmelluq.discord.lavaplayer.source.soundcloud.*;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.track.*;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import java.io.*;
import java.util.*;

/** Keeps native Opus/MP3 playback and adds SoundCloud's fragmented MP4/AAC HLS streams. */
public final class SoundCloudCompatSource extends SoundCloudAudioSourceManager {
    private static final String AAC = "F:";
    // Signed CDN URLs must not pass through SoundCloud's API client-id query filter.
    private final com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager cdnHttp =
            com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools.createDefaultThreadLocalManager();
    @Override public void configureBuilder(java.util.function.Consumer<org.apache.http.impl.client.HttpClientBuilder> configurator) {
        super.configureBuilder(configurator); cdnHttp.configureBuilder(configurator);
    }
    @Override public void shutdown() {
        super.shutdown();
        try { cdnHttp.close(); } catch (IOException ignored) { }
    }

    public static SoundCloudCompatSource create() {
        return new SoundCloudCompatSource(new Reader(),
                new DefaultSoundCloudDataLoader(), new Formats());
    }

    private SoundCloudCompatSource(SoundCloudDataReader reader, SoundCloudDataLoader loader, Formats formats) {
        super(true, reader, loader, formats, new DefaultSoundCloudPlaylistLoader(loader, reader, formats), true);
    }

    private static class Reader extends DefaultSoundCloudDataReader {
        @Override public boolean isTrackBlocked(com.sedmelluq.discord.lavaplayer.tools.JsonBrowser data) {
            if (super.isTrackBlocked(data) || "SNIP".equals(data.get("policy").safeText())
                    || "SUB_HIGH_TIER".equals(data.get("monetization_model").safeText())) return true;
            // One unavailable result must not make an entire SoundCloud search or playlist fail.
            try { new Formats().chooseBestFormat(readTrackFormats(data)); return false; }
            catch (RuntimeException unsupported) { return true; }
        }
    }

    private static class Formats extends DefaultSoundCloudFormatHandler {
        @Override public SoundCloudTrackFormat chooseBestFormat(List<SoundCloudTrackFormat> formats) {
            try { return super.chooseBestFormat(formats); }
            catch (RuntimeException unsupported) {
                return formats.stream().filter(Formats::isAac).findFirst().orElseThrow(() -> unsupported);
            }
        }
        static boolean isAac(SoundCloudTrackFormat f) {
            return "hls".equals(f.getProtocol()) && f.getMimeType().startsWith("audio/mp4");
        }
        @Override public String buildFormatIdentifier(SoundCloudTrackFormat format) {
            return isAac(format) ? AAC + format.getLookupUrl() : super.buildFormatIdentifier(format);
        }
    }

    @Override public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        if (reference.identifier != null && reference.identifier.startsWith("scsearch:")) {
            String query = reference.identifier.substring(9).strip();
            try (var http = getHttpInterface()) {
                String url = "https://api-v2.soundcloud.com/search/tracks?q="
                        + java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8) + "&limit=30";
                try (var stream = com.sedmelluq.discord.lavaplayer.tools.http.HttpStreamTools.streamContent(http,
                        new org.apache.http.client.methods.HttpGet(url))) {
                    var data = com.sedmelluq.discord.lavaplayer.tools.JsonBrowser.parse(stream);
                    List<AudioTrack> tracks = new ArrayList<>();
                    var reader = new Reader();
                    for (var entry : data.get("collection").values()) {
                        if (!reader.isTrackBlocked(entry)) tracks.add(compatible(super.loadFromTrackData(entry)));
                    }
                    return tracks.isEmpty() ? AudioReference.NO_TRACK : new BasicAudioPlaylist("SoundCloud: " + query, tracks, null, true);
                }
            } catch (IOException error) {
                throw new FriendlyException("SoundCloud search is unavailable. Try a direct track link.", FriendlyException.Severity.COMMON, error);
            }
        }
        AudioItem item = super.loadItem(manager, reference);
        if (item instanceof AudioTrack track) return compatible(track);
        if (item instanceof AudioPlaylist playlist) return new BasicAudioPlaylist(playlist.getName(),
                playlist.getTracks().stream().map(this::compatible).toList(),
                playlist.getSelectedTrack() == null ? null : compatible(playlist.getSelectedTrack()), playlist.isSearchResult());
        return item;
    }
    private AudioTrack compatible(AudioTrack track) {
        return track.getIdentifier().startsWith(AAC) ? new AacTrack(track.getInfo(), this) : track;
    }
    @Override public AudioTrack decodeTrack(AudioTrackInfo info, DataInput input) {
        return info.identifier.startsWith(AAC) ? new AacTrack(info, this) : super.decodeTrack(info, input);
    }

    private static class AacTrack extends DelegatedAudioTrack {
        private final SoundCloudCompatSource source;
        AacTrack(AudioTrackInfo info, SoundCloudCompatSource source) { super(info); this.source = source; }
        @Override public SoundCloudAudioSourceManager getSourceManager() { return source; }
        @Override protected AudioTrack makeShallowClone() { return new AacTrack(trackInfo, source); }
        @Override public boolean isSeekable() { return false; }
        @Override public void process(LocalAudioTrackExecutor executor) throws Exception {
            String playback;
            try (var api = source.getHttpInterface()) {
                playback = SoundCloudHelper.loadPlaybackUrl(api, trackInfo.identifier.substring(AAC.length()));
            }
            try (var http = source.cdnHttp.getInterface()) {
                String manifest;
                try (var stream = com.sedmelluq.discord.lavaplayer.tools.http.HttpStreamTools.streamContent(http,
                        new org.apache.http.client.methods.HttpGet(playback))) {
                    manifest = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                }
                if (manifest.contains("#EXT-X-KEY:")) throw new FriendlyException("This SoundCloud stream is encrypted and cannot be played.",
                        FriendlyException.Severity.COMMON, null);
                var urls = new java.util.ArrayList<String>();
                var init = java.util.regex.Pattern.compile("#EXT-X-MAP:URI=\"([^\"]+)\"").matcher(manifest);
                if (init.find()) urls.add(java.net.URI.create(playback).resolve(init.group(1)).toString());
                for (String line : manifest.split("\\R")) {
                    if (!line.isBlank() && !line.startsWith("#")) urls.add(java.net.URI.create(playback).resolve(line.strip()).toString());
                }
                if (urls.isEmpty()) throw new FriendlyException("SoundCloud returned an empty stream.", FriendlyException.Severity.COMMON, null);
                var iterator = urls.iterator();
                try (var chained = new com.sedmelluq.discord.lavaplayer.tools.io.NonSeekableInputStream(
                        new com.sedmelluq.discord.lavaplayer.tools.io.ChainedInputStream(() -> iterator.hasNext()
                                ? com.sedmelluq.discord.lavaplayer.tools.http.HttpStreamTools.streamContent(http,
                                        new org.apache.http.client.methods.HttpGet(iterator.next())) : null))) {
                    processDelegate(new com.sedmelluq.discord.lavaplayer.container.mpeg.MpegAudioTrack(trackInfo, chained), executor);
                }
            }
        }
    }
}
