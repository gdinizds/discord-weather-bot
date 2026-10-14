package dev.gdinizds.discordweatherbot.application.port.out;

import dev.gdinizds.discordweatherbot.domain.model.WeatherAuditLog;

public interface WeatherAuditLogPort {
    void save(WeatherAuditLog log);
}
