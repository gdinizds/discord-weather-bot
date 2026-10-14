package dev.gdinizds.discordweatherbot.adapter.in.kafka;

import dev.gdinizds.discordweatherbot.application.port.in.ConfigureUserWeatherUseCase;
import dev.gdinizds.discordweatherbot.application.port.in.ExecuteWeatherQueryUseCase;
import dev.gdinizds.discordweatherbot.application.port.in.NavigateWeatherUseCase;
import dev.gdinizds.discordweatherbot.domain.model.ConfigureWeatherCommand;
import dev.gdinizds.discordweatherbot.domain.model.WeatherNavigationCommand;
import dev.gdinizds.discordweatherbot.domain.model.WeatherQueryCommand;
import io.github.resilience4j.bulkhead.Bulkhead;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class InboundEventConsumer implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(InboundEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final InboundEventMapper mapper;
    private final ExecuteWeatherQueryUseCase queryUseCase;
    private final ConfigureUserWeatherUseCase configureUseCase;
    private final NavigateWeatherUseCase navigateUseCase;
    private final Bulkhead bulkhead;
    private final ExecutorService executor;
    private final AtomicInteger inFlight = new AtomicInteger();
    private final Object drained = new Object();
    private Duration drainTimeout = Duration.ofSeconds(30);

    public InboundEventConsumer(ObjectMapper objectMapper,
                                InboundEventMapper mapper,
                                ExecuteWeatherQueryUseCase queryUseCase,
                                ConfigureUserWeatherUseCase configureUseCase,
                                NavigateWeatherUseCase navigateUseCase,
                                Bulkhead bulkhead,
                                @Qualifier("weatherExecutor") ExecutorService executor) {
        this.objectMapper = objectMapper;
        this.mapper = mapper;
        this.queryUseCase = queryUseCase;
        this.configureUseCase = configureUseCase;
        this.navigateUseCase = navigateUseCase;
        this.bulkhead = bulkhead;
        this.executor = executor;
    }

    public void setDrainTimeout(Duration drainTimeout) {
        this.drainTimeout = drainTimeout;
    }

    @KafkaListener(topics = "${weather-bot.topics.inbound-interactions}", containerFactory = "kafkaListenerContainerFactory")
    public void onInteractionCommand(@Payload String payload,
                                     @Header(name = "command-name", required = false) String commandName) {
        if (!"clima".equals(commandName) && !"clima-config".equals(commandName)) return;

        parse(payload).ifPresent(event -> {
            if ("clima-config".equals(commandName)) {
                ConfigureWeatherCommand command = mapper.toConfigureCommand(event, payload);
                dispatch(() -> withMdc(command.correlationId(), command.guildId(), "SLASH", () -> configureUseCase.configure(command)));
            } else {
                WeatherQueryCommand command = mapper.toWeatherQueryCommand(event, payload);
                dispatch(() -> withMdc(command.correlationId(), command.guildId(), "SLASH", () -> queryUseCase.execute(command)));
            }
        });
    }

    @KafkaListener(topics = "${weather-bot.topics.inbound-commands}", containerFactory = "kafkaListenerContainerFactory")
    public void onMessageCommand(@Payload String payload,
                                 @Header(name = "command-name", required = false) String commandName) {
        if (!"clima".equals(commandName) && !"clima-config".equals(commandName)) return;

        parse(payload).ifPresent(event -> {
            Object command = mapper.fromDotCommand(event, commandName, payload);
            if (command instanceof ConfigureWeatherCommand cfg) {
                dispatch(() -> withMdc(cfg.correlationId(), cfg.guildId(), "DOT", () -> configureUseCase.configure(cfg)));
            } else if (command instanceof WeatherQueryCommand qry) {
                dispatch(() -> withMdc(qry.correlationId(), qry.guildId(), "DOT", () -> queryUseCase.execute(qry)));
            }
        });
    }

    @KafkaListener(topics = "${weather-bot.topics.inbound-buttons}", containerFactory = "kafkaListenerContainerFactory")
    public void onButtonInteraction(@Payload String payload) {
        parse(payload).ifPresent(event -> {
            if (event.rawPayload() == null) return;
            String compId = event.rawPayload().path("componentId").asString("");
            if (compId.isBlank()) compId = event.rawPayload().path("custom_id").asString("");
            if (!compId.startsWith("weather:")) return;

            WeatherNavigationCommand command = mapper.fromButton(event, payload);
            dispatch(() -> withMdc(command.correlationId(), command.guildId(), "BUTTON", () -> navigateUseCase.navigate(command)));
        });
    }

    private void dispatch(Runnable task) {
        if (!bulkhead.tryAcquirePermission()) {
            log.warn("Bulkhead rejected weather request due to high load");
            return;
        }
        submit(() -> {
            try {
                task.run();
            } finally {
                bulkhead.onComplete();
            }
        });
    }

    private void submit(Runnable task) {
        inFlight.incrementAndGet();
        try {
            executor.execute(() -> {
                try {
                    task.run();
                } finally {
                    release();
                }
            });
        } catch (RuntimeException e) {
            release();
            throw e;
        }
    }

    private void release() {
        if (inFlight.decrementAndGet() == 0) {
            synchronized (drained) {
                drained.notifyAll();
            }
        }
    }

    private Optional<InboundEvent> parse(String payload) {
        try {
            return Optional.of(objectMapper.readValue(payload, InboundEvent.class));
        } catch (Exception e) {
            log.warn("Discarding malformed inbound event: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static void withMdc(String correlationId, String guildId, String trigger, Runnable task) {
        if (correlationId != null) MDC.put("correlation_id", correlationId);
        if (guildId != null) MDC.put("guild_id", guildId);
        MDC.put("trigger", trigger);
        try {
            task.run();
        } catch (Exception e) {
            log.error("Unhandled failure during task execution", e);
        } finally {
            MDC.clear();
        }
    }

    @Override
    public void destroy() {
        long deadline = System.nanoTime() + drainTimeout.toNanos();
        synchronized (drained) {
            while (inFlight.get() > 0) {
                long remainingMs = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
                if (remainingMs <= 0) {
                    log.warn("Shutting down with {} request(s) still in flight", inFlight.get());
                    return;
                }
                log.info("Waiting for {} request(s) to finish before shutdown", inFlight.get());
                try {
                    drained.wait(remainingMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}
