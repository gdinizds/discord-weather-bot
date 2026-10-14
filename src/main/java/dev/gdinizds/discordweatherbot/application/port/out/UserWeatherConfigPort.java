package dev.gdinizds.discordweatherbot.application.port.out;

import dev.gdinizds.discordweatherbot.domain.model.UserWeatherConfig;

import java.util.Optional;

public interface UserWeatherConfigPort {
    Optional<UserWeatherConfig> findByUserId(String userId);
    UserWeatherConfig save(UserWeatherConfig config);
}
