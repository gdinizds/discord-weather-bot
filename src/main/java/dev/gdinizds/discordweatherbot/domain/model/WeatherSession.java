package dev.gdinizds.discordweatherbot.domain.model;

import java.time.Instant;
import java.util.List;

public record WeatherSession(
        String sessionId,
        String guildId,
        String channelId,
        String userId,
        String originMessageId,
        Location location,
        int currentDayIndex,
        List<DailyForecast> daily,
        CurrentWeather current,
        Instant expiresAt
) {
    public WeatherSession withCurrentDayIndex(int newIndex) {
        return new WeatherSession(
                sessionId,
                guildId,
                channelId,
                userId,
                originMessageId,
                location,
                newIndex,
                daily,
                current,
                expiresAt
        );
    }
}
