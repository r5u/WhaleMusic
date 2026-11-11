package com.jagrosh.jmusicbot.audio;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.*;
import com.sedmelluq.discord.lavaplayer.track.*;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import com.jagrosh.jmusicbot.config.io.ConfigIO;
import org.jsoup.Jsoup;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

/** Spotify supplies metadata only; audio comes from matching enabled music sources. */
public final class SpotifyMirrorSource implements AudioSourceManager {
    private static final Pattern URL = Pattern.compile("https://open\\.spotify\\.com/(?:intl-[a-z-]+/)?(?:embed/)?(track|album|playlist)/([A-Za-z0-9]{22})(?:[/?#].*)?");
    private static final Pattern URI_PATTERN = Pattern.compile("spotify:(track|album|playlist):([A-Za-z0-9]{22})");
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final Properties credentials = new Properties();
    private final Path credentialsFile;
    private String token;
    private long tokenExpires;
    private static final int MAX_TRACKS = 500;

    public SpotifyMirrorSource() {
        credentialsFile = ConfigIO.getConfigPath().toAbsolutePath().resolveSibling("spotify.properties");
        try (var input = Files.newInputStream(credentialsFile)) { credentials.load(input); }
        catch (NoSuchFileException ignored) { }
        catch (IOException error) { LoggerFactory.getLogger(getClass()).warn("Could not read spotify.properties"); }
    }
    private String credential(String key, String environment) {
        String value = System.getenv(environment);
        return value == null || value.isBlank() ? credentials.getProperty(key, "").strip() : value.strip();
    }
    private synchronized String token(boolean refresh) throws Exception {
        if (!refresh && token != null && tokenExpires > System.currentTimeMillis()) return token;
        String id = credential("clientId", "SPOTIFY_CLIENT_ID"), secret = credential("clientSecret", "SPOTIFY_CLIENT_SECRET");
        String userRefresh = credential("refreshToken", "SPOTIFY_REFRESH_TOKEN");
        if (id.isEmpty() || (secret.isEmpty() && userRefresh.isEmpty())) return credential("accessToken", "SPOTIFY_ACCESS_TOKEN");
        String form = userRefresh.isEmpty() ? "grant_type=client_credentials" : "grant_type=refresh_token&refresh_token="
                + URLEncoder.encode(userRefresh, StandardCharsets.UTF_8);
        var builder = HttpRequest.newBuilder(URI.create("https://accounts.spotify.com/api/token"))
                .timeout(Duration.ofSeconds(15)).header("Content-Type", "application/x-www-form-urlencoded");
        if (!secret.isEmpty()) builder.header("Authorization", "Basic " + Base64.getEncoder()
                .encodeToString((id + ":" + secret).getBytes(StandardCharsets.UTF_8)));
        else form += "&client_id=" + URLEncoder.encode(id, StandardCharsets.UTF_8);
        var request = builder.POST(HttpRequest.BodyPublishers.ofString(form)).build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IOException("Spotify credentials were not accepted.");
        var json = JsonBrowser.parse(response.body());
        token = json.get("access_token").safeText();
        String rotated = json.get("refresh_token").safeText();
        if (!rotated.isBlank() && !rotated.equals(userRefresh)) {
            credentials.setProperty("refreshToken", rotated);
            if (System.getenv("SPOTIFY_REFRESH_TOKEN") == null && Files.isRegularFile(credentialsFile)) {
                Path temporary = credentialsFile.resolveSibling("spotify.properties.tmp");
                try {
                    try (var output = Files.newOutputStream(temporary)) { credentials.store(output, "Spotify authorization - keep private"); }
                    Files.move(temporary, credentialsFile, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException failed) { LoggerFactory.getLogger(getClass()).warn("Could not save renewed Spotify authorization."); }
            }
        }
        tokenExpires = System.currentTimeMillis() + Math.max(0, json.get("expires_in").as(Long.class) - 60) * 1000;
        return token;
    }
    private JsonBrowser api(String path) throws Exception {
        String access = token(false);
        if (access.isBlank()) throw new IOException("No Spotify API credentials configured.");
        for (int attempt = 0; attempt < 2; attempt++) {
            var request = HttpRequest.newBuilder(URI.create("https://api.spotify.com/v1/" + path)).timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + access).GET().build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401 && attempt == 0) { access = token(true); continue; }
            if (response.statusCode() != 200) throw new IOException("Spotify API returned HTTP " + response.statusCode());
            return JsonBrowser.parse(response.body());
        }
        throw new IOException("Spotify authorization expired.");
    }
    private String expand(String url) throws Exception {
        for (int i=0;i<5;i++) {
            URI uri=URI.create(url);
            if (!"https".equals(uri.getScheme()) || !Set.of("spotify.link", "open.spotify.com").contains(uri.getHost()))
                throw new IOException("Unsupported Spotify redirect.");
            if ("open.spotify.com".equals(uri.getHost())) return url;
            var response=http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.discarding());
            String location=response.headers().firstValue("location").orElseThrow(()->new IOException("Spotify short link did not redirect."));
            url=uri.resolve(location).toString();
        }
        throw new IOException("Too many Spotify redirects.");
    }
    @Override public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        this.manager = manager;
        String value=reference.identifier;
        if (value == null) return null;
        if (value.startsWith("<") && value.endsWith(">")) value=value.substring(1,value.length()-1);
        try {
            if (value.startsWith("https://spotify.link/")) value=expand(value);
            Matcher match=URL.matcher(value);
            if (!match.matches()) match=URI_PATTERN.matcher(value);
            if (!match.matches()) {
                if(value.startsWith("https://open.spotify.com/") || value.startsWith("spotify:"))
                    throw new FriendlyException("Use a Spotify track, album, or playlist link.", FriendlyException.Severity.COMMON, null);
                return null;
            }
            String type=match.group(1), id=match.group(2);
            try { return apiItem(manager,type,id); }
            catch (InterruptedException interrupted) { throw interrupted; }
            catch (Exception unavailable) { return publicItem(manager,type,id); }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new FriendlyException("Spotify loading was cancelled.",FriendlyException.Severity.COMMON,interrupted);
        } catch (FriendlyException known) { throw known; }
        catch (Exception error) {
            throw new FriendlyException("Could not read this Spotify link. Use a public music link or configure spotify.properties for API access.",
                    FriendlyException.Severity.COMMON, error);
        }
    }
    private AudioItem apiItem(AudioPlayerManager manager,String type,String id) throws Exception {
        var entity=api(type+"s/"+id);
        if(type.equals("track")) return apiTrack(manager,entity);
        List<AudioTrack> tracks=new ArrayList<>();
        String path=type.equals("album") ? "albums/"+id+"/tracks" : "playlists/"+id+"/items";
        int offset=0;
        while(tracks.size()<MAX_TRACKS) {
            JsonBrowser page;
            try { page=api(path+"?limit=50&offset="+offset); }
            catch(IOException oldApi) {
                if(!path.endsWith("/items"))throw oldApi;
                path="playlists/"+id+"/tracks";page=api(path+"?limit=50&offset="+offset);
            }
            var items=page.get("items").values();
            for(var item:items) {
                var track=type.equals("album")?item:(!item.get("item").isNull()?item.get("item"):item.get("track"));
                if(!track.isNull() && !track.get("is_local").asBoolean(false) && "track".equals(track.get("type").safeText())) {
                    AudioTrack audio=apiTrack(manager,track); if(audio!=null)tracks.add(audio);
                }
                if(tracks.size()>=MAX_TRACKS)break;
            }
            offset+=items.size();
            if(items.isEmpty()||page.get("next").isNull())break;
        }
        if(tracks.isEmpty())throw new IOException("No accessible Spotify tracks.");
        return new BasicAudioPlaylist(entity.get("name").safeText()+(tracks.size()==MAX_TRACKS?" (first 500 tracks)":""),tracks,null,false);
    }
    private AudioTrack apiTrack(AudioPlayerManager manager,JsonBrowser track) {
        String id=track.get("id").safeText();
        if(!id.matches("[A-Za-z0-9]{22}"))return null;
        String artists=String.join(", ",track.get("artists").values().stream().map(a->a.get("name").safeText()).toList());
        return mirror(manager,id,track.get("name").safeText(),artists,track.get("duration_ms").as(Long.class),
                track.get("album").get("images").index(0).get("url").text(),track.get("external_ids").get("isrc").text());
    }
    private AudioItem publicItem(AudioPlayerManager manager,String type,String id) throws Exception {
        var doc=Jsoup.connect("https://open.spotify.com/embed/"+type+"/"+id).timeout(15000).maxBodySize(4*1024*1024).get();
        var script=doc.getElementById("__NEXT_DATA__");
        if(script==null)throw new IOException("Spotify metadata was unavailable.");
        var entity=JsonBrowser.parse(script.data()).get("props").get("pageProps").get("state").get("data").get("entity");
        if(entity.isNull())throw new IOException("Spotify link is unavailable or private.");
        if(type.equals("track")) {
            String artists=String.join(", ",entity.get("artists").values().stream().map(a->a.get("name").safeText()).toList());
            return mirror(manager,id,entity.get("title").safeText(),artists,entity.get("duration").as(Long.class),null,null);
        }
        List<AudioTrack> tracks=new ArrayList<>();
        for(var track:entity.get("trackList").values()) {
            String uri=track.get("uri").safeText();
            if(uri.startsWith("spotify:track:"))tracks.add(mirror(manager,uri.substring(14),track.get("title").safeText(),
                    track.get("subtitle").safeText(),track.get("duration").as(Long.class),null,null));
            if(tracks.size()>=MAX_TRACKS)break;
        }
        if(tracks.isEmpty())throw new IOException("No public tracks in Spotify link.");
        String suffix=type.equals("playlist")?" (public preview - "+tracks.size()+" tracks)":"";
        return new BasicAudioPlaylist(entity.get("title").safeText()+suffix,tracks,null,false);
    }
    private AudioTrack mirror(AudioPlayerManager manager,String id,String title,String artist,long duration,String image,String isrc) {
        if(title.isBlank()||artist.isBlank()||duration<=0)throw new IllegalArgumentException("Spotify track metadata is incomplete.");
        return new MirroredMusicTrack(new AudioTrackInfo(title,artist,duration,"spotify:track:"+id,false,
                "https://open.spotify.com/track/"+id,image,isrc),this,manager,"Spotify");
    }
    static String normalized(String text) {
        return Normalizer.normalize(text,Normalizer.Form.NFKD).toLowerCase(Locale.ROOT).replaceAll("\\p{M}","")
                .replaceAll("[^\\p{L}\\p{N}]+"," ").strip();
    }
    static double score(AudioTrackInfo wanted,AudioTrackInfo candidate) {
        if(candidate.isStream || Math.abs(candidate.length-wanted.length)>Math.max(15000,wanted.length/10))return -1;
        String title=normalized(wanted.title), artist=normalized(wanted.author);
        String other=normalized(candidate.title), all=other+" "+normalized(candidate.author);
        for(String version:List.of("cover","remix","live","nightcore","slowed","sped","instrumental","karaoke","bootleg","rework"))
            if(!Arrays.asList(title.split(" ")).contains(version)&&Arrays.asList(other.split(" ")).contains(version))return -1;
        Set<String> words=new HashSet<>(Arrays.asList(title.split(" ")));
        long matches=words.stream().filter(w->Arrays.asList(other.split(" ")).contains(w)).count();
        double titleScore=(double)matches/words.size();
        if(titleScore<0.8)return -1;
        Set<String> artists=new HashSet<>(Arrays.asList(artist.split(" ")));
        double artistScore=(double)artists.stream().filter(w->Arrays.asList(all.split(" ")).contains(w)).count()/artists.size();
        if(artistScore<0.5)return -1;
        return titleScore*.65+artistScore*.25+.1*(1.0-Math.min(1.0,Math.abs(candidate.length-wanted.length)/15000.0));
    }
    private volatile AudioPlayerManager manager;
    public void setPlayerManager(AudioPlayerManager manager){this.manager=manager;}
    @Override public String getSourceName(){return "spotify";}
    @Override public boolean isTrackEncodable(AudioTrack track){return true;}
    @Override public void encodeTrack(AudioTrack track,DataOutput output){ }
    @Override public AudioTrack decodeTrack(AudioTrackInfo info,DataInput input){return new MirroredMusicTrack(info,this,manager,"Spotify");}
    @Override public void shutdown(){http.close();}
}
