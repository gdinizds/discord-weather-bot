package dev.gdinizds.discordweatherbot.domain.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class WeatherCodeTranslatorTest {

    @Test
    void shouldTranslateClearSky() {
        var condition = WeatherCodeTranslator.translate(0);
        assertThat(condition.emoji()).isEqualTo("☀️");
        assertThat(condition.description()).isEqualTo("Céu limpo");
        assertThat(condition.colorHex()).isEqualTo(0xF59E0B);
    }

    @Test
    void shouldTranslateThunderstorm() {
        var condition = WeatherCodeTranslator.translate(95);
        assertThat(condition.emoji()).isEqualTo("⛈️");
        assertThat(condition.description()).isEqualTo("Tempestade com trovoadas");
    }

    @ParameterizedTest
    @ValueSource(ints = {61, 63, 65, 80, 81, 82})
    void shouldTranslateRainCodes(int code) {
        var condition = WeatherCodeTranslator.translate(code);
        assertThat(condition.emoji()).matches("🌧️|🌦️|⛈️");
        assertThat(condition.description().toLowerCase()).contains("chuva");
    }

    @Test
    void shouldFallbackForUnknownCode() {
        var condition = WeatherCodeTranslator.translate(999);
        assertThat(condition.emoji()).isEqualTo("🌡️");
        assertThat(condition.description()).isEqualTo("Clima atual");
    }
}
