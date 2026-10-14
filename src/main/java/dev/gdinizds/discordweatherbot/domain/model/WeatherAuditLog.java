package dev.gdinizds.discordweatherbot.domain.model;

import java.time.Instant;

public record WeatherAuditLog(
        String correlationId,
        String guildId,
        String channelId,
        String userId,
        String commandName,
        String triggerType,
        String queryLocation,
        String resolvedLocation,
        String status,
        String rawRequest,
        String rawResponse,
        long executionTimeMs,
        Instant createdAt
) {
    public static WeatherAuditLog create(String correlationId,
                                         String guildId,
                                         String channelId,
                                         String userId,
                                         String commandName,
                                         String triggerType,
                                         String queryLocation,
                                         String resolvedLocation,
                                         String status,
                                         String rawRequest,
                                         String rawResponse,
                                         long executionTimeMs) {
        return new WeatherAuditLog(
                correlationId,
                guildId,
                channelId,
                userId,
                commandName,
                triggerType,
                queryLocation,
                resolvedLocation,
                status,
                rawRequest,
                rawResponse,
                executionTimeMs,
                Instant.now()
        );
    }
}
