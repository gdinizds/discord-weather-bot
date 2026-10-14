package dev.gdinizds.discordweatherbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "weather-bot")
public record WeatherBotProperties(
        Topics topics,
        OpenMeteo openmeteo,
        BrasilApi brasilapi,
        Session session,
        Executor executor
) {
    public record Topics(
            String inboundInteractions,
            String inboundCommands,
            String inboundButtons,
            String outboundResponses,
            String gatewayCommands
    ) {}

    public record OpenMeteo(
            String geocodingUrl,
            String forecastUrl,
            Duration timeout
    ) {}

    public record BrasilApi(
            String cepUrl,
            Duration timeout
    ) {}

    public record Session(
            Duration ttl
    ) {}

    public record Executor(
            int concurrency,
            Duration drainTimeout
    ) {}
}
