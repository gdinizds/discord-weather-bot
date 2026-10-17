package dev.gdinizds.discordweatherbot.adapter.out.metrics;

import dev.gdinizds.discordweatherbot.application.port.out.WeatherMetricsPort;
import dev.gdinizds.discordweatherbot.application.port.out.WeatherSessionPort;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.Duration;

public class MicrometerMetrics implements WeatherMetricsPort {

    private final MeterRegistry registry;
    private final WeatherSessionPort sessionPort;
    private volatile int cachedActiveSessions = 0;
    private volatile long lastCheckTimeMs = 0;
    private static final long CACHE_TTL_MS = 60_000;

    public MicrometerMetrics(MeterRegistry registry, WeatherSessionPort sessionPort) {
        this.registry = registry;
        this.sessionPort = sessionPort;
        Gauge.builder("discord.weatherbot.sessions.active", this, MicrometerMetrics::getActiveSessionsCached)
                .description("Active weather user navigation sessions")
                .register(registry);
    }

    private int getActiveSessionsCached() {
        long now = System.currentTimeMillis();
        if (now - lastCheckTimeMs > CACHE_TTL_MS) {
            try {
                cachedActiveSessions = sessionPort.countActive();
                lastCheckTimeMs = now;
            } catch (Exception ignored) {
            }
        }
        return cachedActiveSessions;
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
