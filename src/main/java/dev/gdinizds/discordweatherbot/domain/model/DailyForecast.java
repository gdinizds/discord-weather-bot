package dev.gdinizds.discordweatherbot.domain.model;

import java.time.LocalDate;

public record DailyForecast(
        LocalDate date,
        int weatherCode,
        double tempMax,
        double tempMin,
        double apparentTempMax,
        double apparentTempMin,
        Double uvIndexMax,
        double precipitationSum,
        int precipitationProbabilityMax,
        double windSpeedMax
) {}
