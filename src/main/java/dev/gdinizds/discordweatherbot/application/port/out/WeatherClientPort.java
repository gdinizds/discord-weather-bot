package dev.gdinizds.discordweatherbot.application.port.out;

import dev.gdinizds.discordweatherbot.domain.model.Location;
import dev.gdinizds.discordweatherbot.domain.model.WeatherForecast;

import java.util.Optional;

public interface WeatherClientPort {
    Optional<Location> resolveLocation(String query, String state, String country);
    WeatherForecast fetchForecast(Location location);
}
