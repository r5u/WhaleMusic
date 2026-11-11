package com.jagrosh.jmusicbot.audio;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.*;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import org.slf4j.LoggerFactory;
import java.util.*;

/** Full-track matching shared by metadata-only music services. */
final class MirroredMusicTrack extends DelegatedAudioTrack {
    private final AudioSourceManager source;
    private final AudioPlayerManager manager;
    private final String service;
    MirroredMusicTrack(AudioTrackInfo info,AudioSourceManager source,AudioPlayerManager manager,String service) {
        super(info);this.source=source;this.manager=manager;this.service=service;
    }
    @Override public AudioSourceManager getSourceManager(){return source;}
    @Override protected AudioTrack makeShallowClone(){return new MirroredMusicTrack(trackInfo,source,manager,service);}
    @Override public void process(LocalAudioTrackExecutor executor)throws Exception {
        Exception last=null;
        String query=trackInfo.author+" - "+trackInfo.title;
        for(String provider:List.of("ytsearch:","scsearch:")) {
            if(Thread.currentThread().isInterrupted())throw new InterruptedException();
            try {
                AudioItem item=manager.loadItemSync(provider+query);
                List<AudioTrack> candidates=item instanceof AudioPlaylist p?p.getTracks():item instanceof AudioTrack t?List.of(t):List.of();
                AudioTrack best=candidates.stream().filter(t->SpotifyMirrorSource.score(trackInfo,t.getInfo())>=.7)
                        .max(Comparator.comparingDouble(t->SpotifyMirrorSource.score(trackInfo,t.getInfo()))).orElse(null);
                if(best==null)continue;
                LoggerFactory.getLogger(MirroredMusicTrack.class).info("{} match: {} - {} via {}",service,trackInfo.author,trackInfo.title,best.getSourceManager().getSourceName());
                processDelegate((InternalAudioTrack)best,executor);
                return;
            } catch(InterruptedException interrupted){throw interrupted;}
            catch(Exception failed){if(executor.getPosition()>5000)throw failed;last=failed;}
        }
        throw new FriendlyException("No playable YouTube or SoundCloud match found for this "+service+" track. Try a direct link.",FriendlyException.Severity.COMMON,last);
    }
}
