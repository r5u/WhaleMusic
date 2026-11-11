package com.jagrosh.jmusicbot.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jagrosh.jmusicbot.config.io.ConfigIO;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public final class WeatherListener extends ListenerAdapter {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json = new ObjectMapper();

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || event.isWebhookMessage()) {
            return;
        }
        String[] parts = event.getMessage().getContentRaw().strip().split("\\s+", 2);
        if (!parts[0].equalsIgnoreCase("`weather")) {
            return;
        }
        if (parts.length != 2 || parts[1].length() > 120) {
            reply(event, "Use `weather <city>, for example `weather Boston,US.");
            return;
        }
        String key = System.getenv("OPENWEATHER_API_KEY");
        if (key == null || key.isBlank()) {
            try {
                key = Files.readString(ConfigIO.getConfigPath().toAbsolutePath().resolveSibling("weather-key.txt")).strip();
            } catch (IOException ignored) {
                reply(event, "The weather API key is missing or unreadable.");
                return;
            }
        }
        if (key.isBlank()) {
            reply(event, "The weather API key is empty.");
            return;
        }
        var request = HttpRequest.newBuilder(URI.create("https://api.openweathermap.org/data/2.5/weather?q="
                + encode(parts[1]) + "&units=metric&appid=" + encode(key.strip())))
                .timeout(Duration.ofSeconds(10)).build();
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(this::format)
                .exceptionally(error -> "Weather lookup failed. Try again later.")
                .thenAccept(message -> reply(event, message));
    }

    private String format(HttpResponse<String> response) {
        return switch (response.statusCode()) {
            case 200 -> conditions(response.body());
            case 401 -> "OpenWeather rejected the API key.";
            case 404 -> "City not found. Try adding a country code.";
            case 429 -> "Weather lookups are temporarily rate-limited.";
            default -> "Weather is temporarily unavailable.";
        };
    }

    private String conditions(String body) {
        try {
            var data = json.readTree(body);
            double celsius = data.path("main").path("temp").asDouble(Double.NaN);
            if (!Double.isFinite(celsius)) {
                return "Weather data is temporarily unavailable.";
            }
            var humidity = data.path("main").path("humidity");
            double windMetersPerSecond = data.path("wind").path("speed").asDouble(Double.NaN);
            String humidityText = humidity.isNumber() ? humidity.asInt() + "%" : "unavailable";
            String windText = Double.isFinite(windMetersPerSecond)
                    ? String.format(Locale.ROOT, "%.0f mph / %.0f km/h",
                            windMetersPerSecond * 2.236936, windMetersPerSecond * 3.6)
                    : "unavailable";
            return String.format(Locale.ROOT, "%s, %s | %.0f°F / %.0f°C | %s | Humidity: %s | Wind: %s",
                    data.path("name").asText(), data.path("sys").path("country").asText(),
                    celsius * 9 / 5 + 32, celsius, data.path("weather").path(0).path("description").asText(),
                    humidityText, windText);
        } catch (IOException ignored) {
            return "Weather data is temporarily unavailable.";
        }
    }

    private static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
    }

    private static void reply(MessageReceivedEvent event, String message) {
        event.getChannel().sendMessage("🌤️ " + message).setAllowedMentions(Set.of()).queue();
    }
}
