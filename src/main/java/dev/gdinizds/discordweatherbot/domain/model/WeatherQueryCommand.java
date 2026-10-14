package dev.gdinizds.discordweatherbot.domain.model;

public record WeatherQueryCommand(
        String correlationId,
        String guildId,
        String channelId,
        String userId,
        String interactionToken,
        String originMessageId,
        String local,
        String estado,
        String pais,
        String triggerType,
        String rawPayload
) {}
