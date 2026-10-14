package dev.gdinizds.discordweatherbot.domain.model;

import java.util.List;

public record WeatherForecast(
        Location location,
        CurrentWeather current,
        List<DailyForecast> daily
) {}
