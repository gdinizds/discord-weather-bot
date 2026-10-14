package dev.gdinizds.discordweatherbot.domain;

import dev.gdinizds.discordweatherbot.domain.model.CurrentWeather;
import dev.gdinizds.discordweatherbot.domain.model.DailyForecast;
import dev.gdinizds.discordweatherbot.domain.model.Location;
import dev.gdinizds.discordweatherbot.domain.model.WeatherSession;
import dev.gdinizds.discordweatherbot.domain.service.WeatherLayoutBuilder;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WeatherLayoutBuilderTest {

    private final WeatherLayoutBuilder builder = new WeatherLayoutBuilder();

    @Test
    void shouldRenderDayZeroWithCurrentWeather() {
        Location location = new Location("São Paulo", "SP", "Brasil", -23.54, -46.63, "America/Sao_Paulo");
        CurrentWeather current = new CurrentWeather(LocalDateTime.now(), 25.4, 26.1, 60, 0.0, 0, 10.5, 1);
        DailyForecast day0 = new DailyForecast(LocalDate.now(), 0, 28.0, 18.0, 29.0, 18.0, 8.5, 0.0, 10, 15.0);
        DailyForecast day1 = new DailyForecast(LocalDate.now().plusDays(1), 61, 22.0, 17.0, 22.0, 17.0, 4.0, 5.0, 80, 12.0);

        WeatherSession session = new WeatherSession("s1", "g1", "c1", "u1", "m1", location, 0, List.of(day0, day1), Instant.now().plusSeconds(3600));

        var rendered = builder.render(session, current);

        assertThat(rendered.embeds()).hasSize(1);
        Map<String, Object> embed = rendered.embeds().get(0);
        assertThat(embed.get("title").toString()).contains("São Paulo");
        assertThat(embed.get("description").toString()).contains("25°C");

        assertThat(rendered.components()).hasSize(1);
        List<?> buttons = (List<?>) rendered.components().get(0).get("components");
        assertThat(buttons).hasSize(3);

        Map<?, ?> prevButton = (Map<?, ?>) buttons.get(0);
        assertThat(prevButton.get("disabled")).isEqualTo(true);

        Map<?, ?> nextButton = (Map<?, ?>) buttons.get(2);
        assertThat(nextButton.get("disabled")).isEqualTo(false);
    }

    @Test
    void shouldRenderDayOneWithNavigationDisabledNextOnLastDay() {
        Location location = new Location("Curitiba", "PR", "Brasil", -25.42, -49.27, "America/Sao_Paulo");
        DailyForecast day0 = new DailyForecast(LocalDate.now(), 0, 20.0, 10.0, 20.0, 10.0, 6.0, 0.0, 0, 10.0);
        DailyForecast day1 = new DailyForecast(LocalDate.now().plusDays(1), 3, 19.0, 11.0, 19.0, 11.0, 5.0, 1.0, 30, 8.0);

        WeatherSession session = new WeatherSession("s2", "g1", "c1", "u1", "m1", location, 1, List.of(day0, day1), Instant.now().plusSeconds(3600));

        var rendered = builder.render(session, null);

        assertThat(rendered.embeds()).hasSize(1);
        Map<?, ?> prevButton = (Map<?, ?>) ((List<?>) rendered.components().get(0).get("components")).get(0);
        assertThat(prevButton.get("disabled")).isEqualTo(false);

        Map<?, ?> nextButton = (Map<?, ?>) ((List<?>) rendered.components().get(0).get("components")).get(2);
        assertThat(nextButton.get("disabled")).isEqualTo(true);
    }

    @Test
    void shouldRenderUnconfiguredGuidance() {
        var rendered = builder.renderUnconfigured();
        assertThat(rendered.embeds()).hasSize(1);
        assertThat(rendered.embeds().get(0).get("description").toString()).contains("/clima-config");
    }

    @Test
    void shouldRenderNotFound() {
        var rendered = builder.renderNotFound("Narnia");
        assertThat(rendered.embeds()).hasSize(1);
        assertThat(rendered.embeds().get(0).get("description").toString()).contains("Narnia");
    }
}
