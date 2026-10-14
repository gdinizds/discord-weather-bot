package dev.gdinizds.discordweatherbot.domain.model;

import java.time.LocalDateTime;

public record CurrentWeather(
        LocalDateTime time,
        double temperature,
        double apparentTemperature,
        int relativeHumidity,
        double precipitation,
        int weatherCode,
        double windSpeed,
        int isDay
) {}
