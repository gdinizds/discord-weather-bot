package dev.gdinizds.discordweatherbot.application.port.in;

import dev.gdinizds.discordweatherbot.domain.model.WeatherNavigationCommand;

public interface NavigateWeatherUseCase {
    void navigate(WeatherNavigationCommand command);
}
