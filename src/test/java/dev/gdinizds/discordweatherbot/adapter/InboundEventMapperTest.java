package dev.gdinizds.discordweatherbot.adapter;

import dev.gdinizds.discordweatherbot.adapter.in.kafka.InboundEvent;
import dev.gdinizds.discordweatherbot.adapter.in.kafka.InboundEvent.GuildRef;
import dev.gdinizds.discordweatherbot.adapter.in.kafka.InboundEvent.UserRef;
import dev.gdinizds.discordweatherbot.adapter.in.kafka.InboundEventMapper;
import dev.gdinizds.discordweatherbot.domain.model.ConfigureWeatherCommand;
import dev.gdinizds.discordweatherbot.domain.model.WeatherNavigationCommand;
import dev.gdinizds.discordweatherbot.domain.model.WeatherQueryCommand;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InboundEventMapperTest {

    private final InboundEventMapper mapper = new InboundEventMapper();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldMapSlashClimaCommand() throws Exception {
        String json = """
                {
                  "eventType": "INTERACTION_COMMAND",
                  "correlationId": "corr-1",
                  "guild": { "id": "g-1", "name": "Guild" },
                  "channelId": "ch-1",
                  "user": { "id": "u-1", "username": "user1" },
                  "interactionToken": "tok-1",
                  "rawPayload": {
                    "args": {
                      "local": "Belo Horizonte",
                      "estado": "MG",
                      "pais": "Brasil"
                    }
                  }
                }
                """;

        InboundEvent event = objectMapper.readValue(json, InboundEvent.class);
        WeatherQueryCommand cmd = mapper.toWeatherQueryCommand(event, json);

        assertThat(cmd.correlationId()).isEqualTo("corr-1");
        assertThat(cmd.userId()).isEqualTo("u-1");
        assertThat(cmd.local()).isEqualTo("Belo Horizonte");
        assertThat(cmd.estado()).isEqualTo("MG");
        assertThat(cmd.pais()).isEqualTo("Brasil");
        assertThat(cmd.triggerType()).isEqualTo("SLASH");
    }

    @Test
    void shouldMapSlashClimaConfigCommand() throws Exception {
        String json = """
                {
                  "eventType": "INTERACTION_COMMAND",
                  "correlationId": "corr-2",
                  "guild": { "id": "g-1", "name": "Guild" },
                  "channelId": "ch-1",
                  "user": { "id": "u-2", "username": "user2" },
                  "interactionToken": "tok-2",
                  "rawPayload": {
                    "args": {
                      "cidade": "01001-000"
                    }
                  }
                }
                """;

        InboundEvent event = objectMapper.readValue(json, InboundEvent.class);
        ConfigureWeatherCommand cmd = mapper.toConfigureCommand(event, json);

        assertThat(cmd.correlationId()).isEqualTo("corr-2");
        assertThat(cmd.userId()).isEqualTo("u-2");
        assertThat(cmd.local()).isEqualTo("01001-000");
    }

    @Test
    void shouldMapButtonInteraction() throws Exception {
        String json = """
                {
                  "eventType": "INTERACTION_BUTTON",
                  "correlationId": "corr-3",
                  "guild": { "id": "g-1", "name": "Guild" },
                  "channelId": "ch-1",
                  "user": { "id": "u-3", "username": "user3" },
                  "interactionToken": "tok-3",
                  "messageId": "msg-origin",
                  "rawPayload": {
                    "componentId": "weather:next:sess-abc"
                  }
                }
                """;

        InboundEvent event = objectMapper.readValue(json, InboundEvent.class);
        WeatherNavigationCommand cmd = mapper.fromButton(event, json);

        assertThat(cmd.sessionId()).isEqualTo("sess-abc");
        assertThat(cmd.action()).isEqualTo("next");
        assertThat(cmd.originMessageId()).isEqualTo("msg-origin");
    }
}
