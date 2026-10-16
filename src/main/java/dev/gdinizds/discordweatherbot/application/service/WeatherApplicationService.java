package dev.gdinizds.discordweatherbot.application.service;

import dev.gdinizds.discordweatherbot.application.port.in.ConfigureUserWeatherUseCase;
import dev.gdinizds.discordweatherbot.application.port.in.ExecuteWeatherQueryUseCase;
import dev.gdinizds.discordweatherbot.application.port.in.NavigateWeatherUseCase;
import dev.gdinizds.discordweatherbot.application.port.out.ReplyPublisherPort;
import dev.gdinizds.discordweatherbot.application.port.out.UserWeatherConfigPort;
import dev.gdinizds.discordweatherbot.application.port.out.WeatherAuditLogPort;
import dev.gdinizds.discordweatherbot.application.port.out.WeatherClientPort;
import dev.gdinizds.discordweatherbot.application.port.out.WeatherMetricsPort;
import dev.gdinizds.discordweatherbot.application.port.out.WeatherSessionPort;
import dev.gdinizds.discordweatherbot.domain.model.ConfigureWeatherCommand;
import dev.gdinizds.discordweatherbot.domain.model.Location;
import dev.gdinizds.discordweatherbot.domain.model.OutboundResponse;
import dev.gdinizds.discordweatherbot.domain.model.UserWeatherConfig;
import dev.gdinizds.discordweatherbot.domain.model.WeatherAuditLog;
import dev.gdinizds.discordweatherbot.domain.model.WeatherForecast;
import dev.gdinizds.discordweatherbot.domain.model.WeatherNavigationCommand;
import dev.gdinizds.discordweatherbot.domain.model.WeatherQueryCommand;
import dev.gdinizds.discordweatherbot.domain.model.WeatherSession;
import dev.gdinizds.discordweatherbot.domain.service.WeatherLayoutBuilder;
import dev.gdinizds.discordweatherbot.domain.service.WeatherLayoutBuilder.RenderedWeather;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public class WeatherApplicationService implements ExecuteWeatherQueryUseCase, ConfigureUserWeatherUseCase, NavigateWeatherUseCase {

    private static final Logger log = LoggerFactory.getLogger(WeatherApplicationService.class);

    private final WeatherClientPort weatherClient;
    private final UserWeatherConfigPort userWeatherConfigPort;
    private final WeatherSessionPort weatherSessionPort;
    private final WeatherAuditLogPort weatherAuditLogPort;
    private final ReplyPublisherPort replyPublisher;
    private final WeatherLayoutBuilder layoutBuilder;
    private final WeatherMetricsPort metricsPort;
    private final Duration sessionTtl;

    public WeatherApplicationService(WeatherClientPort weatherClient,
                                     UserWeatherConfigPort userWeatherConfigPort,
                                     WeatherSessionPort weatherSessionPort,
                                     WeatherAuditLogPort weatherAuditLogPort,
                                     ReplyPublisherPort replyPublisher,
                                     WeatherLayoutBuilder layoutBuilder,
                                     Duration sessionTtl) {
        this(weatherClient, userWeatherConfigPort, weatherSessionPort, weatherAuditLogPort,
                replyPublisher, layoutBuilder, WeatherMetricsPort.NOOP, sessionTtl);
    }

    public WeatherApplicationService(WeatherClientPort weatherClient,
                                     UserWeatherConfigPort userWeatherConfigPort,
                                     WeatherSessionPort weatherSessionPort,
                                     WeatherAuditLogPort weatherAuditLogPort,
                                     ReplyPublisherPort replyPublisher,
                                     WeatherLayoutBuilder layoutBuilder,
                                     WeatherMetricsPort metricsPort,
                                     Duration sessionTtl) {
        this.weatherClient = weatherClient;
        this.userWeatherConfigPort = userWeatherConfigPort;
        this.weatherSessionPort = weatherSessionPort;
        this.weatherAuditLogPort = weatherAuditLogPort;
        this.replyPublisher = replyPublisher;
        this.layoutBuilder = layoutBuilder;
        this.metricsPort = metricsPort != null ? metricsPort : WeatherMetricsPort.NOOP;
        this.sessionTtl = sessionTtl;
    }

    @Override
    public void execute(WeatherQueryCommand command) {
        long start = System.currentTimeMillis();
        String queryLocation = command.local();
        String queryState = command.estado();
        String queryCountry = command.pais();

        try {
            if (queryLocation == null || queryLocation.isBlank()) {
                Optional<UserWeatherConfig> userConfig = userWeatherConfigPort.findByUserId(command.userId());
                if (userConfig.isEmpty()) {
                    RenderedWeather rendered = layoutBuilder.renderUnconfigured();
                    replyPublisher.publish(OutboundResponse.reply(
                            command.interactionToken(),
                            command.originMessageId(),
                            command.channelId(),
                            command.correlationId(),
                            rendered.content(),
                            rendered.embeds(),
                            rendered.components()
                    ));
                    recordAudit(command.correlationId(), command.guildId(), command.channelId(), command.userId(),
                            "clima", command.triggerType(), "DEFAULT", null, "UNCONFIGURED",
                            command.rawPayload(), null, System.currentTimeMillis() - start);
                    metricsPort.recordRequest("clima", "unconfigured");
                    metricsPort.recordLatency("clima", Duration.ofMillis(System.currentTimeMillis() - start));
                    return;
                }

                UserWeatherConfig cfg = userConfig.get();
                queryLocation = cfg.city();
                queryState = cfg.state();
                queryCountry = cfg.country();
            }

            Optional<Location> locationOpt = weatherClient.resolveLocation(queryLocation, queryState, queryCountry);
            if (locationOpt.isEmpty()) {
                RenderedWeather rendered = layoutBuilder.renderNotFound(queryLocation);
                replyPublisher.publish(OutboundResponse.reply(
                        command.interactionToken(),
                        command.originMessageId(),
                        command.channelId(),
                        command.correlationId(),
                        rendered.content(),
                        rendered.embeds(),
                        rendered.components()
                ));
                recordAudit(command.correlationId(), command.guildId(), command.channelId(), command.userId(),
                        "clima", command.triggerType(), queryLocation, null, "NOT_FOUND",
                        command.rawPayload(), null, System.currentTimeMillis() - start);
                metricsPort.recordGeocoding("location", "not_found");
                metricsPort.recordRequest("clima", "not_found");
                metricsPort.recordLatency("clima", Duration.ofMillis(System.currentTimeMillis() - start));
                return;
            }

            Location location = locationOpt.get();
            boolean isCep = queryLocation != null && queryLocation.trim().matches("\\d{5}-?\\d{3}");
            metricsPort.recordGeocoding(isCep ? "brasilapi" : "openmeteo", "success");

            WeatherForecast forecast = weatherClient.fetchForecast(location);

            String sessionId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            WeatherSession session = new WeatherSession(
                    sessionId,
                    command.guildId(),
                    command.channelId(),
                    command.userId(),
                    command.originMessageId(),
                    location,
                    0,
                    forecast.daily(),
                    forecast.current(),
                    Instant.now().plus(sessionTtl)
            );
            weatherSessionPort.save(session);

            RenderedWeather rendered = layoutBuilder.render(session, forecast.current());
            replyPublisher.publish(OutboundResponse.reply(
                    command.interactionToken(),
                    command.originMessageId(),
                    command.channelId(),
                    command.correlationId(),
                    rendered.content(),
                    rendered.embeds(),
                    rendered.components()
            ));

            recordAudit(command.correlationId(), command.guildId(), command.channelId(), command.userId(),
                    "clima", command.triggerType(), queryLocation, location.displayName(), "SUCCESS",
                    command.rawPayload(), "sessionId=" + sessionId, System.currentTimeMillis() - start);
            metricsPort.recordRequest("clima", "success");
            metricsPort.recordLatency("clima", Duration.ofMillis(System.currentTimeMillis() - start));

        } catch (Exception e) {
            log.error("Failed to execute weather query [correlationId={}]: {}", command.correlationId(), e.getMessage(), e);
            RenderedWeather rendered = layoutBuilder.renderError(e.getMessage());
            replyPublisher.publish(OutboundResponse.reply(
                    command.interactionToken(),
                    command.originMessageId(),
                    command.channelId(),
                    command.correlationId(),
                    rendered.content(),
                    rendered.embeds(),
                    rendered.components()
            ));
            recordAudit(command.correlationId(), command.guildId(), command.channelId(), command.userId(),
                    "clima", command.triggerType(), queryLocation, null, "ERROR",
                    command.rawPayload(), e.getMessage(), System.currentTimeMillis() - start);
            metricsPort.recordRequest("clima", "error");
            metricsPort.recordLatency("clima", Duration.ofMillis(System.currentTimeMillis() - start));
        }
    }

    @Override
    public void configure(ConfigureWeatherCommand command) {
        long start = System.currentTimeMillis();
        String local = command.local();

        try {
            if (local == null || local.isBlank()) {
                RenderedWeather rendered = layoutBuilder.renderUnconfigured();
                replyPublisher.publish(OutboundResponse.ephemeralReply(
                        command.interactionToken(),
                        command.originMessageId(),
                        command.channelId(),
                        command.correlationId(),
                        rendered.content(),
                        rendered.embeds()
                ));
                recordAudit(command.correlationId(), command.guildId(), command.channelId(), command.userId(),
                        "clima-config", command.triggerType(), null, null, "CONFIG_EMPTY",
                        command.rawPayload(), null, System.currentTimeMillis() - start);
                metricsPort.recordRequest("config", "empty");
                metricsPort.recordLatency("config", Duration.ofMillis(System.currentTimeMillis() - start));
                return;
            }

            Optional<Location> locationOpt = weatherClient.resolveLocation(local, command.estado(), command.pais());
            if (locationOpt.isEmpty()) {
                RenderedWeather rendered = layoutBuilder.renderNotFound(local);
                replyPublisher.publish(OutboundResponse.ephemeralReply(
                        command.interactionToken(),
                        command.originMessageId(),
                        command.channelId(),
                        command.correlationId(),
                        rendered.content(),
                        rendered.embeds()
                ));
                recordAudit(command.correlationId(), command.guildId(), command.channelId(), command.userId(),
                        "clima-config", command.triggerType(), local, null, "CONFIG_NOT_FOUND",
                        command.rawPayload(), null, System.currentTimeMillis() - start);
                metricsPort.recordGeocoding("location", "not_found");
                metricsPort.recordRequest("config", "not_found");
                metricsPort.recordLatency("config", Duration.ofMillis(System.currentTimeMillis() - start));
                return;
            }

            Location location = locationOpt.get();
            boolean isCep = local.trim().matches("\\d{5}-?\\d{3}");
            metricsPort.recordGeocoding(isCep ? "brasilapi" : "openmeteo", "success");

            UserWeatherConfig config = UserWeatherConfig.create(command.userId(), location);
            userWeatherConfigPort.save(config);

            RenderedWeather rendered = layoutBuilder.renderConfigSaved(location);
            replyPublisher.publish(OutboundResponse.ephemeralReply(
                    command.interactionToken(),
                    command.originMessageId(),
                    command.channelId(),
                    command.correlationId(),
                    rendered.content(),
                    rendered.embeds()
            ));

            recordAudit(command.correlationId(), command.guildId(), command.channelId(), command.userId(),
                    "clima-config", command.triggerType(), local, location.displayName(), "CONFIG_SAVED",
                    command.rawPayload(), null, System.currentTimeMillis() - start);
            metricsPort.recordRequest("config", "success");
            metricsPort.recordLatency("config", Duration.ofMillis(System.currentTimeMillis() - start));

        } catch (Exception e) {
            log.error("Failed to configure user weather [correlationId={}]: {}", command.correlationId(), e.getMessage(), e);
            RenderedWeather rendered = layoutBuilder.renderError(e.getMessage());
            replyPublisher.publish(OutboundResponse.ephemeralReply(
                    command.interactionToken(),
                    command.originMessageId(),
                    command.channelId(),
                    command.correlationId(),
                    rendered.content(),
                    rendered.embeds()
            ));
            recordAudit(command.correlationId(), command.guildId(), command.channelId(), command.userId(),
                    "clima-config", command.triggerType(), local, null, "ERROR",
                    command.rawPayload(), e.getMessage(), System.currentTimeMillis() - start);
            metricsPort.recordRequest("config", "error");
            metricsPort.recordLatency("config", Duration.ofMillis(System.currentTimeMillis() - start));
        }
    }

    @Override
    public void navigate(WeatherNavigationCommand command) {
        long start = System.currentTimeMillis();

        try {
            Optional<WeatherSession> sessionOpt = weatherSessionPort.findById(command.sessionId());
            if (sessionOpt.isEmpty()) {
                RenderedWeather rendered = layoutBuilder.renderError("⚠️ Esta previsão expirou ou não foi encontrada. Execute `/clima` novamente.");
                replyPublisher.publish(OutboundResponse.ephemeralReply(
                        command.interactionToken(),
                        command.originMessageId(),
                        command.channelId(),
                        command.correlationId(),
                        rendered.content(),
                        rendered.embeds()
                ));
                recordAudit(command.correlationId(), command.guildId(), command.channelId(), command.userId(),
                        "weather_button", "BUTTON", command.sessionId(), null, "EXPIRED",
                        command.rawPayload(), null, System.currentTimeMillis() - start);
                metricsPort.recordNavigationClick(command.action(), "expired");
                return;
            }

            WeatherSession session = sessionOpt.get();
            if (session.userId() != null && !session.userId().equals(command.userId())) {
                replyPublisher.publish(OutboundResponse.ephemeralReply(
                        command.interactionToken(),
                        command.originMessageId(),
                        command.channelId(),
                        command.correlationId(),
                        "⚠️ Apenas quem executou o comando pode interagir com estes botões.",
                        null
                ));
                recordAudit(command.correlationId(), command.guildId(), command.channelId(), command.userId(),
                        "weather_button", "BUTTON", command.sessionId(), null, "FORBIDDEN",
                        command.rawPayload(), "User " + command.userId() + " not authorized for session of user " + session.userId(), System.currentTimeMillis() - start);
                metricsPort.recordNavigationClick(command.action(), "forbidden");
                return;
            }

            int current = session.currentDayIndex();
            int total = session.daily().size();

            int targetIndex = switch (command.action().toUpperCase()) {
                case "NEXT" -> Math.min(total - 1, current + 1);
                case "PREV" -> Math.max(0, current - 1);
                case "TODAY" -> 0;
                default -> current;
            };

            WeatherSession updatedSession = session.withCurrentDayIndex(targetIndex);
            weatherSessionPort.save(updatedSession);

            RenderedWeather rendered = layoutBuilder.render(updatedSession, updatedSession.current());
            replyPublisher.publish(OutboundResponse.updateMessage(
                    command.interactionToken(),
                    command.originMessageId(),
                    command.channelId(),
                    command.correlationId(),
                    rendered.content(),
                    rendered.embeds(),
                    rendered.components()
            ));

            recordAudit(command.correlationId(), command.guildId(), command.channelId(), command.userId(),
                    "weather_button", "BUTTON", command.sessionId(), updatedSession.location().displayName(), "NAVIGATED",
                    command.rawPayload(), "day=" + targetIndex, System.currentTimeMillis() - start);
            metricsPort.recordNavigationClick(command.action(), "success");

        } catch (Exception e) {
            log.error("Failed to navigate weather [sessionId={}]: {}", command.sessionId(), e.getMessage(), e);
            RenderedWeather rendered = layoutBuilder.renderError(e.getMessage());
            replyPublisher.publish(OutboundResponse.updateMessage(
                    command.interactionToken(),
                    command.originMessageId(),
                    command.channelId(),
                    command.correlationId(),
                    rendered.content(),
                    rendered.embeds(),
                    rendered.components()
            ));
            recordAudit(command.correlationId(), command.guildId(), command.channelId(), command.userId(),
                    "weather_button", "BUTTON", command.sessionId(), null, "ERROR",
                    command.rawPayload(), e.getMessage(), System.currentTimeMillis() - start);
            metricsPort.recordNavigationClick(command.action(), "error");
        }
    }

    private void recordAudit(String correlationId, String guildId, String channelId, String userId,
                             String commandName, String triggerType, String queryLocation, String resolvedLocation,
                             String status, String rawRequest, String rawResponse, long executionTimeMs) {
        try {
            weatherAuditLogPort.save(WeatherAuditLog.create(
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
                    executionTimeMs
            ));
        } catch (Exception e) {
            log.warn("Failed to record weather audit log: {}", e.getMessage());
        }
    }
}
