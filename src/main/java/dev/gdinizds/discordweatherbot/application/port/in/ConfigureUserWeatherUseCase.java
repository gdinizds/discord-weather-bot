package dev.gdinizds.discordweatherbot.application.port.in;

import dev.gdinizds.discordweatherbot.domain.model.ConfigureWeatherCommand;

public interface ConfigureUserWeatherUseCase {
    void configure(ConfigureWeatherCommand command);
}
