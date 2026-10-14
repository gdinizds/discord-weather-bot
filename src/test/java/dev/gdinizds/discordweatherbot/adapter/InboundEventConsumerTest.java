package dev.gdinizds.discordweatherbot.adapter;

import dev.gdinizds.discordweatherbot.adapter.in.kafka.InboundEventConsumer;
import dev.gdinizds.discordweatherbot.adapter.in.kafka.InboundEventMapper;
import dev.gdinizds.discordweatherbot.application.port.in.ConfigureUserWeatherUseCase;
import dev.gdinizds.discordweatherbot.application.port.in.ExecuteWeatherQueryUseCase;
import dev.gdinizds.discordweatherbot.application.port.in.NavigateWeatherUseCase;
import dev.gdinizds.discordweatherbot.domain.model.ConfigureWeatherCommand;
import dev.gdinizds.discordweatherbot.domain.model.WeatherNavigationCommand;
import dev.gdinizds.discordweatherbot.domain.model.WeatherQueryCommand;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InboundEventConsumerTest {

    @Mock
    private ExecuteWeatherQueryUseCase queryUseCase;

    @Mock
    private ConfigureUserWeatherUseCase configureUseCase;

    @Mock
    private NavigateWeatherUseCase navigateUseCase;

    private ExecutorService executor;
    private InboundEventConsumer consumer;

    @BeforeEach
    void setUp() {
        executor = Executors.newVirtualThreadPerTaskExecutor();
        Bulkhead bulkhead = Bulkhead.of("test", BulkheadConfig.custom().maxConcurrentCalls(10).build());
        consumer = new InboundEventConsumer(
                new ObjectMapper(),
                new InboundEventMapper(),
                queryUseCase,
                configureUseCase,
                navigateUseCase,
                bulkhead,
                executor
        );
        consumer.setDrainTimeout(Duration.ofSeconds(2));
    }

    @AfterEach
    void tearDown() {
        consumer.destroy();
        executor.shutdownNow();
    }

    @Test
    void shouldDispatchSlashClima() {
        String payload = """
                {
                  "eventType": "INTERACTION_COMMAND",
                  "correlationId": "c1",
                  "guild": { "id": "g1", "name": "guild1" },
                  "channelId": "ch1",
                  "user": { "id": "u1", "username": "user1" },
                  "interactionToken": "tok1",
                  "rawPayload": { "args": { "local": "Manaus" } }
                }
                """;

        consumer.onInteractionCommand(payload, "clima");

        await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                verify(queryUseCase).execute(any(WeatherQueryCommand.class)));
    }

    @Test
    void shouldDispatchSlashClimaConfig() {
        String payload = """
                {
                  "eventType": "INTERACTION_COMMAND",
                  "correlationId": "c2",
                  "guild": { "id": "g1", "name": "guild1" },
                  "channelId": "ch1",
                  "user": { "id": "u2", "username": "user2" },
                  "interactionToken": "tok2",
                  "rawPayload": { "args": { "cidade": "Salvador" } }
                }
                """;

        consumer.onInteractionCommand(payload, "clima-config");

        await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                verify(configureUseCase).configure(any(ConfigureWeatherCommand.class)));
    }

    @Test
    void shouldDispatchDotClimaConfig() {
        String payload = """
                {
                  "eventType": "MESSAGE_COMMAND",
                  "correlationId": "c3",
                  "guild": { "id": "g1", "name": "guild1" },
                  "channelId": "ch1",
                  "user": { "id": "u3", "username": "user3" },
                  "messageId": "msg1",
                  "rawPayload": { "content": "config Porto Alegre, RS, Brasil" }
                }
                """;

        consumer.onMessageCommand(payload, "clima");

        await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                verify(configureUseCase).configure(any(ConfigureWeatherCommand.class)));
    }

    @Test
    void shouldDispatchButtonNavigation() {
        String payload = """
                {
                  "eventType": "INTERACTION_BUTTON",
                  "correlationId": "c4",
                  "guild": { "id": "g1", "name": "guild1" },
                  "channelId": "ch1",
                  "user": { "id": "u4", "username": "user4" },
                  "interactionToken": "tok4",
                  "rawPayload": { "componentId": "weather:next:sess-123" }
                }
                """;

        consumer.onButtonInteraction(payload);

        await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                verify(navigateUseCase).navigate(any(WeatherNavigationCommand.class)));
    }

    @Test
    void shouldIgnoreUnrelatedCommands() {
        String payload = """
                {
                  "eventType": "INTERACTION_COMMAND",
                  "correlationId": "c5"
                }
                """;

        consumer.onInteractionCommand(payload, "pesquisar");

        verify(queryUseCase, never()).execute(any());
        verify(configureUseCase, never()).configure(any());
    }
}
