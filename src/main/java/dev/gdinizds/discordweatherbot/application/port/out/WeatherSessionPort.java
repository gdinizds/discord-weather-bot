package dev.gdinizds.discordweatherbot.application.port.out;

import dev.gdinizds.discordweatherbot.domain.model.WeatherSession;

import java.util.Optional;

public interface WeatherSessionPort {
    Optional<WeatherSession> findById(String sessionId);
    void save(WeatherSession session);
    default int countActive() {
        return 0;
    }
}
