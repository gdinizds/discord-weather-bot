package dev.gdinizds.discordweatherbot.application.port.in;

import dev.gdinizds.discordweatherbot.domain.model.WeatherQueryCommand;

public interface ExecuteWeatherQueryUseCase {
    void execute(WeatherQueryCommand command);
}
