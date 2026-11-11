package com.jagrosh.jmusicbot.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Properties;

final class BeerWalletBridge {
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final String url = System.getenv("BEER_BRIDGE_URL");
    private final String secret = System.getenv("BEER_BRIDGE_SECRET");

    boolean enabled() {
        return url != null && !url.isBlank();
    }

    Snapshot load(String guild, Properties local) throws IOException {
        var body = json.createObjectNode();
        var legacy = body.putObject("legacy");
        String prefix = guild + ".";
        local.stringPropertyNames().stream().filter(key -> key.startsWith(prefix))
                .forEach(key -> legacy.put(key.substring(prefix.length()), local.getProperty(key)));
        var response = request(guild, body);
        var snapshot = new Snapshot(guild, response.path("revision").asLong());
        response.path("properties").properties().forEach(entry ->
                snapshot.setProperty(prefix + entry.getKey(), entry.getValue().asText()));
        snapshot.original.putAll(snapshot);
        return snapshot;
    }

    void save(Snapshot snapshot) throws IOException {
        var body = json.createObjectNode().put("revision", snapshot.revision);
        var changes = body.putObject("changes");
        var expected = body.putObject("expected");
        int prefix = snapshot.guild.length() + 1;
        for (String key : snapshot.stringPropertyNames()) {
            String current = snapshot.getProperty(key);
            String previous = snapshot.original.getProperty(key);
            if (!current.equals(previous)) {
                String field = key.substring(prefix);
                changes.put(field, current);
                if (field.endsWith(".ml")) {
                    expected.put(field, previous == null ? "0" : previous);
                }
            }
        }
        for (String key : snapshot.original.stringPropertyNames()) {
            if (!snapshot.containsKey(key)) {
                changes.putNull(key.substring(prefix));
            }
        }
        request(snapshot.guild, body);
    }

    String convert(Snapshot snapshot, String user, long amount, long capacity, boolean toBeer) throws IOException {
        var body = json.createObjectNode().put("revision", snapshot.revision);
        body.putObject("conversion").put("user", user).put("amount", amount).put("capacity", capacity)
                .put("direction", toBeer ? "toBeer" : "toDegen");
        var response = request(snapshot.guild, body);
        if (response.has("error")) {
            return response.path("error").asText();
        }
        String exchange = toBeer ? amount + " Degen points into " + response.path("ml").asLong() + "mL"
                : amount + "mL into " + (amount * 10) + " Degen points";
        return "Converted " + exchange + " | " + response.path("balance").asLong() + "mL total | "
                + response.path("points").asLong() + " Degen points left.";
    }

    private JsonNode request(String guild, JsonNode body) throws IOException {
        if (secret == null || secret.isBlank()) {
            throw new IOException("BEER_BRIDGE_SECRET is missing");
        }
        var request = HttpRequest.newBuilder(URI.create(url.replaceFirst("/+$", "") + "/" + guild))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + secret)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
        try {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("Beer wallet returned HTTP " + response.statusCode());
            }
            return json.readTree(response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Beer wallet request interrupted", ex);
        }
    }

    static final class Snapshot extends Properties {
        final String guild;
        final long revision;
        final Properties original = new Properties();

        Snapshot(String guild, long revision) {
            this.guild = guild;
            this.revision = revision;
        }
    }
}
