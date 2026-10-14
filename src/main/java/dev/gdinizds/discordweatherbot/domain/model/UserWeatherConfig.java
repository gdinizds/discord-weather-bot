package dev.gdinizds.discordweatherbot.domain.model;

import java.time.Instant;

public record UserWeatherConfig(
        String userId,
        String city,
        String state,
        String country,
        double latitude,
        double longitude,
        String timezone,
        Instant createdAt,
        Instant updatedAt
) {
    public static UserWeatherConfig create(String userId, Location location) {
        Instant now = Instant.now();
        return new UserWeatherConfig(
                userId,
                location.name(),
                location.state(),
                location.country(),
                location.latitude(),
                location.longitude(),
                location.timezone(),
                now,
                now
        );
    }
}
