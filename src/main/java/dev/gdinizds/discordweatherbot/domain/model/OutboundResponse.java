package dev.gdinizds.discordweatherbot.domain.model;

import java.util.List;
import java.util.Map;

public record OutboundResponse(
        String responseType,
        String interactionToken,
        String messageId,
        String channelId,
        String correlationId,
        String content,
        List<Map<String, Object>> embeds,
        Boolean finished,
        List<Map<String, Object>> components
) {
    public static OutboundResponse reply(String interactionToken,
                                         String messageId,
                                         String channelId,
                                         String correlationId,
                                         String content,
                                         List<Map<String, Object>> embeds,
                                         List<Map<String, Object>> components) {
        return new OutboundResponse("REPLY", interactionToken, messageId, channelId, correlationId, content, embeds, true, components);
    }

    public static OutboundResponse updateMessage(String interactionToken,
                                                 String messageId,
                                                 String channelId,
                                                 String correlationId,
                                                 String content,
                                                 List<Map<String, Object>> embeds,
                                                 List<Map<String, Object>> components) {
        return new OutboundResponse("UPDATE_MESSAGE", interactionToken, messageId, channelId, correlationId, content, embeds, true, components);
    }

    public static OutboundResponse ephemeralReply(String interactionToken,
                                                  String messageId,
                                                  String channelId,
                                                  String correlationId,
                                                  String content,
                                                  List<Map<String, Object>> embeds) {
        if (interactionToken != null && !interactionToken.isBlank()) {
            return new OutboundResponse("EPHEMERAL_REPLY", interactionToken, null, null, correlationId, content, embeds, true, null);
        }
        return new OutboundResponse("REPLY", null, messageId, channelId, correlationId, content, embeds, true, null);
    }
}
