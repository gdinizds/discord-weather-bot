package dev.gdinizds.discordweatherbot.application.port.out;

import java.time.Duration;

public interface WeatherMetricsPort {

    void recordRequest(String type, String outcome);

    void recordLatency(String type, Duration duration);

    void recordGeocoding(String source, String outcome);

    void recordNavigationClick(String action, String outcome);

    WeatherMetricsPort NOOP = new WeatherMetricsPort() {
        @Override
        public void recordRequest(String type, String outcome) {}

        @Override
        public void recordLatency(String type, Duration duration) {}

        @Override
        public void recordGeocoding(String source, String outcome) {}

        @Override
        public void recordNavigationClick(String action, String outcome) {}
    };
}
