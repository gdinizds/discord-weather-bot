package dev.gdinizds.discordweatherbot.adapter.in.kafka;

import dev.gdinizds.discordweatherbot.domain.model.ConfigureWeatherCommand;
import dev.gdinizds.discordweatherbot.domain.model.WeatherNavigationCommand;
import dev.gdinizds.discordweatherbot.domain.model.WeatherQueryCommand;
import dev.gdinizds.discordweatherbot.domain.service.CommandTextParser;
import dev.gdinizds.discordweatherbot.domain.service.CommandTextParser.ActionType;
import dev.gdinizds.discordweatherbot.domain.service.CommandTextParser.ParsedCommand;
import tools.jackson.databind.JsonNode;

public class InboundEventMapper {

    public WeatherQueryCommand toWeatherQueryCommand(InboundEvent event, String rawJson) {
        JsonNode args = event.rawPayload() != null ? event.rawPayload().path("args") : null;
        String local = null;
        String estado = null;
        String pais = null;

        if (args != null && !args.isMissingNode()) {
            local = getNonBlank(args.path("local").asString(null));
            estado = getNonBlank(args.path("estado").asString(null));
            pais = getNonBlank(args.path("pais").asString(null));
        }

        return new WeatherQueryCommand(
                event.correlationId(),
                event.guildId(),
                event.channelId(),
                event.userId(),
                event.interactionToken(),
                event.messageId(),
                local,
                estado,
                pais,
                "SLASH",
                rawJson
        );
    }

    public ConfigureWeatherCommand toConfigureCommand(InboundEvent event, String rawJson) {
        JsonNode args = event.rawPayload() != null ? event.rawPayload().path("args") : null;
        String local = null;
        String estado = null;
        String pais = null;

        if (args != null && !args.isMissingNode()) {
            local = getNonBlank(args.path("cidade").asString(null));
            estado = getNonBlank(args.path("estado").asString(null));
            pais = getNonBlank(args.path("pais").asString(null));
        }

        return new ConfigureWeatherCommand(
                event.correlationId(),
                event.guildId(),
                event.channelId(),
                event.userId(),
                event.interactionToken(),
                event.messageId(),
                local,
                estado,
                pais,
                "SLASH",
                rawJson
        );
    }

    public Object fromDotCommand(InboundEvent event, String commandName, String rawJson) {
        String content = "";
        if (event.rawPayload() != null) {
            content = event.rawPayload().path("content").asString("");
        }

        ParsedCommand parsed = CommandTextParser.parse(commandName, content);

        if (parsed.actionType() == ActionType.CONFIGURE) {
            return new ConfigureWeatherCommand(
                    event.correlationId(),
                    event.guildId(),
                    event.channelId(),
                    event.userId(),
                    null,
                    event.messageId(),
                    parsed.local(),
                    parsed.estado(),
                    parsed.pais(),
                    "DOT",
                    rawJson
            );
        }

        return new WeatherQueryCommand(
                event.correlationId(),
                event.guildId(),
                event.channelId(),
                event.userId(),
                null,
                event.messageId(),
                parsed.local(),
                parsed.estado(),
                parsed.pais(),
                "DOT",
                rawJson
        );
    }

    public WeatherNavigationCommand fromButton(InboundEvent event, String rawJson) {
        String compId = "";
        String originMsgId = event.messageId();
        if (event.rawPayload() != null) {
            compId = event.rawPayload().path("componentId").asString("");
            if (compId.isBlank()) {
                compId = event.rawPayload().path("custom_id").asString("");
            }
            if (originMsgId == null || originMsgId.isBlank()) {
                originMsgId = event.rawPayload().path("messageId").asString(null);
            }
        }

        String[] parts = compId.split(":");
        String action = parts.length > 1 ? parts[1] : "TODAY";
        String sessionId = parts.length > 2 ? parts[2] : "";

        return new WeatherNavigationCommand(
                event.correlationId(),
                event.guildId(),
                event.channelId(),
                event.userId(),
                event.interactionToken(),
                originMsgId,
                sessionId,
                action,
                rawJson
        );
    }

    private static String getNonBlank(String val) {
        return val != null && !val.isBlank() ? val.strip() : null;
    }
}
