package dev.gdinizds.discordweatherbot.adapter.out.metrics;

import dev.gdinizds.discordweatherbot.application.port.out.WeatherMetricsPort;
import dev.gdinizds.discordweatherbot.application.port.out.WeatherSessionPort;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.Duration;

public class MicrometerMetrics implements WeatherMetricsPort {

    private final MeterRegistry registry;

    public MicrometerMetrics(MeterRegistry registry, WeatherSessionPort sessionPort) {
        this.registry = registry;
        Gauge.builder("discord.weatherbot.sessions.active", () -> {
            try {
                return sessionPort.countActive();
            } catch (Exception e) {
                return 0;
            }
        }).description("Active weather user navigation sessions").register(registry);
    }

    @Override
    public void recordRequest(String type, String outcome) {
        registry.counter("discord.weatherbot.requests",
                "type", type != null ? type.toLowerCase() : "unknown",
                "outcome", outcome != null ? outcome.toLowerCase() : "unknown"
        ).increment();
    }

    @Override
    public void recordLatency(String type, Duration duration) {
        if (duration != null) {
            registry.timer("discord.weatherbot.latency",
                    "type", type != null ? type.toLowerCase() : "unknown"
            ).record(duration);
        }
    }

    @Override
    public void recordGeocoding(String source, String outcome) {
        registry.counter("discord.weatherbot.geocoding",
                "source", source != null ? source.toLowerCase() : "unknown",
                "outcome", outcome != null ? outcome.toLowerCase() : "unknown"
        ).increment();
    }

    @Override
    public void recordNavigationClick(String action, String outcome) {
        registry.counter("discord.weatherbot.navigation.clicks",
                "action", action != null ? action.toLowerCase() : "unknown",
                "outcome", outcome != null ? outcome.toLowerCase() : "unknown"
        ).increment();
    }
}
