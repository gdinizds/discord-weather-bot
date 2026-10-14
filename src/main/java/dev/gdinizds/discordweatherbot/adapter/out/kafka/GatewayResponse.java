package dev.gdinizds.discordweatherbot.adapter.out.kafka;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GatewayResponse(
        String responseType,
        String interactionToken,
        String messageId,
        String channelId,
        String correlationId,
        String content,
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        List<Map<String, Object>> embeds,
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        List<Attachment> attachments,
        Boolean finished,
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        List<Map<String, Object>> components
) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Attachment(String url, String name, String description) {}
}
