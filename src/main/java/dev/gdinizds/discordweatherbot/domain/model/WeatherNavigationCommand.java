package dev.gdinizds.discordweatherbot.domain.model;

public record WeatherNavigationCommand(
        String correlationId,
        String guildId,
        String channelId,
        String userId,
        String interactionToken,
        String originMessageId,
        String sessionId,
        String action,
        String rawPayload
) {}
