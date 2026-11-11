package com.jagrosh.jmusicbot.audio;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.*;
import com.sedmelluq.discord.lavaplayer.track.*;
import org.jsoup.Jsoup;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Reads public Apple Music metadata without an Apple account or preview-audio playback. */
public final class AppleMusicSource implements AudioSourceManager {
    private volatile AudioPlayerManager manager;
    public AppleMusicSource(AudioPlayerManager manager){this.manager=manager;}
    @Override public String getSourceName(){return "applemusic";}
    @Override public AudioItem loadItem(AudioPlayerManager manager,AudioReference reference) {
        String input=reference.identifier;
        if(input==null)return null;
        if(input.startsWith("<")&&input.endsWith(">"))input=input.substring(1,input.length()-1);
        URI uri;
        try {uri=URI.create(input);}catch(IllegalArgumentException ignored){return null;}
        if(!Set.of("music.apple.com","itunes.apple.com").contains(String.valueOf(uri.getHost())))return null;
        this.manager=manager;
        try {
            if(!"https".equalsIgnoreCase(uri.getScheme()))throw new IOException("Use an HTTPS Apple Music link.");
            String[] parts=uri.getPath().split("/");
            String country=parts.length>1&&parts[1].matches("[A-Za-z]{2}")?parts[1].toLowerCase(Locale.ROOT):"us";
            String type=Arrays.stream(parts).filter(p->Set.of("album","song","playlist").contains(p)).findFirst().orElse("");
            if(type.isEmpty())throw new IOException("Use a song, album, or public playlist link.");
            String songId=null;
            if(uri.getRawQuery()!=null)for(String param:uri.getRawQuery().split("&")) {
                String[] pair=param.split("=",2);
                if(pair[0].equals("i"))songId=pair.length==2?URLDecoder.decode(pair[1],StandardCharsets.UTF_8):"";
            }
            if(songId!=null&&!songId.matches("[0-9]+"))throw new IOException("Invalid Apple Music song ID.");
            String id=parts[parts.length-1].replaceFirst("^id","");
            if(type.equals("playlist"))return playlist(uri.toString());
            if(!id.matches("[0-9]+"))throw new IOException("Invalid Apple Music ID.");
            boolean single=songId!=null||type.equals("song");
            String lookupId=songId!=null?songId:id;
            String url="https://itunes.apple.com/lookup?id="+lookupId+"&country="+country+"&entity=song&limit=200";
            var json=JsonBrowser.parse(Jsoup.connect(url).ignoreContentType(true).timeout(15000).maxBodySize(4*1024*1024).execute().body());
            List<AudioTrack> tracks=new ArrayList<>();String name="Apple Music album";
            for(var item:json.get("results").values()) {
                if(!item.get("collectionName").safeText().isBlank())name=item.get("collectionName").safeText();
                if("song".equals(item.get("kind").safeText()) && (!single||lookupId.equals(item.get("trackId").safeText())))
                    tracks.add(track(item.get("trackId").safeText(),item.get("trackName").safeText(),item.get("artistName").safeText(),
                            item.get("trackTimeMillis").as(Long.class),item.get("trackViewUrl").safeText(),item.get("artworkUrl100").text()));
            }
            if(tracks.isEmpty()) {
                // The Apple Music catalogue can contain releases not sold in the iTunes store.
                AudioPlaylist page=playlist("https://music.apple.com"+uri.getRawPath());
                if(single) {
                    final String wanted=lookupId;
                    return page.getTracks().stream().filter(t->t.getIdentifier().equals("applemusic:"+wanted)).findFirst()
                            .orElseThrow(()->new IOException("This song is unavailable in the public Apple Music page."));
                }
                return page;
            }
            return single?tracks.getFirst():new BasicAudioPlaylist(name,tracks,null,false);
        }catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new FriendlyException("Apple Music loading was cancelled.",FriendlyException.Severity.COMMON,interrupted);}
        catch(Exception error){throw new FriendlyException("Could not read this Apple Music link. Use a public song, album, or playlist available in its linked region.",FriendlyException.Severity.COMMON,error);}
    }
    private AudioPlaylist playlist(String url)throws Exception {
        var doc=Jsoup.connect(url).timeout(15000).maxBodySize(6*1024*1024).get();
        var script=doc.getElementById("serialized-server-data");
        if(script==null)throw new IOException("Apple Music metadata is unavailable.");
        var data=JsonBrowser.parse(script.data()).get("data").index(0).get("data");
        var tracks=new LinkedHashMap<String,AudioTrack>();
        String title=doc.select("meta[property=og:title]").attr("content").replaceFirst(" - Apple Music$","");
        for(var section:data.get("sections").values()) {
            if(!"trackLockup".equals(section.get("itemKind").safeText())&&!section.get("id").safeText().startsWith("track-list -"))continue;
            for(var item:section.get("items").values()) {
                var descriptor=item.get("contentDescriptor");
                String id=descriptor.get("identifiers").get("storeAdamID").safeText();
                if(!"song".equals(descriptor.get("kind").safeText())||!id.matches("[0-9]+"))continue;
                String artist=item.get("artistName").safeText();
                if(artist.isBlank())artist=String.join(", ",item.get("subtitleLinks").values().stream().map(a->a.get("title").safeText()).toList());
                String image=item.get("artwork").get("dictionary").get("url").text();
                if(image!=null)image=image.replace("{w}","300").replace("{h}","300").replace("{f}","jpg");
                long duration=item.get("duration").as(Long.class);
                if(duration>0&&!artist.isBlank())tracks.putIfAbsent(id,track(id,item.get("title").safeText(),artist,duration,descriptor.get("url").safeText(),image));
            }
        }
        if(tracks.isEmpty())throw new IOException("This Apple Music page has no public songs.");
        return new BasicAudioPlaylist((title.isBlank()?"Apple Music":title)+" (public listing - "+tracks.size()+" tracks)",new ArrayList<>(tracks.values()),null,false);
    }
    private AudioTrack track(String id,String title,String artist,long duration,String url,String artwork) {
        if(title.isBlank()||artist.isBlank()||duration<=0)throw new IllegalArgumentException("Incomplete Apple Music metadata.");
        return new MirroredMusicTrack(new AudioTrackInfo(title,artist,duration,"applemusic:"+id,false,url,artwork,null),this,manager,"Apple Music");
    }
    @Override public boolean isTrackEncodable(AudioTrack track){return true;}
    @Override public void encodeTrack(AudioTrack track,DataOutput output){}
    @Override public AudioTrack decodeTrack(AudioTrackInfo info,DataInput input){return new MirroredMusicTrack(info,this,manager,"Apple Music");}
    @Override public void shutdown(){}
}
