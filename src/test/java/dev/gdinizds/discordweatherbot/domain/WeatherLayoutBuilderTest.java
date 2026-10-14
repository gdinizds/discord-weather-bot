package dev.gdinizds.discordweatherbot.domain;

import dev.gdinizds.discordweatherbot.domain.model.CurrentWeather;
import dev.gdinizds.discordweatherbot.domain.model.DailyForecast;
import dev.gdinizds.discordweatherbot.domain.model.Location;
import dev.gdinizds.discordweatherbot.domain.model.WeatherSession;
import dev.gdinizds.discordweatherbot.domain.service.WeatherLayoutBuilder;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WeatherLayoutBuilderTest {

    private final WeatherLayoutBuilder builder = new WeatherLayoutBuilder();

    @Test
    void shouldRenderDayZeroWithVerticalLayoutAndPortugueseDay() {
        Location location = new Location("Pau Grande", "RO", "Brasil", -10.0, -62.0, "America/Porto_Velho");
        CurrentWeather current = new CurrentWeather(LocalDateTime.now(), 30.0, 32.0, 75, 0.0, 80, 10.5, 1);
        DailyForecast day0 = new DailyForecast(LocalDate.of(2026, 10, 14), 81, 36.0, 25.0, 37.0, 25.0, 9.1, 4.0, 84, 10.5);
        DailyForecast day1 = new DailyForecast(LocalDate.of(2026, 10, 15), 81, 32.0, 25.0, 33.0, 25.0, 8.0, 3.0, 66, 9.0);

        WeatherSession session = new WeatherSession("s1", "g1", "c1", "u1", "m1", location, 0, List.of(day0, day1), Instant.now().plusSeconds(3600));

        var rendered = builder.render(session, current);

        assertThat(rendered.embeds()).hasSize(1);
        Map<String, Object> embed = rendered.embeds().get(0);
        assertThat(embed.get("title").toString()).isEqualTo("🌧️ Clima em Pau Grande, RO - Brasil");

        String description = embed.get("description").toString();
        assertThat(description).contains("36°C / 25°C — 🌧️ Pancadas de chuva");
        assertThat(description).contains("🌡️ Máx / Mín:🔺 36°C / 🔻 25°C");
        assertThat(description).contains("💨 Vento Máx: 10.5 km/h");
        assertThat(description).contains("🌧️ Chuva: 84% (4.0 mm)");
        assertThat(description).contains("☀️ Índice UV: 9.1");
        assertThat(description).contains("📅 Data\nQuarta, 14/10/2026");

        assertThat((List<?>) embed.get("fields")).isEmpty();

        assertThat(rendered.components()).hasSize(1);
        List<?> buttons = (List<?>) rendered.components().get(0).get("components");
        assertThat(buttons).hasSize(3);

        Map<?, ?> prevButton = (Map<?, ?>) buttons.get(0);
        assertThat(prevButton.get("label")).isEqualTo("◀ Anterior");
        assertThat(prevButton.get("disabled")).isEqualTo(true);

        Map<?, ?> todayButton = (Map<?, ?>) buttons.get(1);
        assertThat(todayButton.get("label")).isEqualTo("📅 Hoje");
        assertThat(todayButton.get("disabled")).isEqualTo(true);

        Map<?, ?> nextButton = (Map<?, ?>) buttons.get(2);
        assertThat(nextButton.get("label")).isEqualTo("Próximo ▶");
        assertThat(nextButton.get("disabled")).isEqualTo(false);
    }

    @Test
    void shouldRenderDayOneWithNavigationDisabledNextOnLastDay() {
        Location location = new Location("Curitiba", "PR", "Brasil", -25.42, -49.27, "America/Sao_Paulo");
        DailyForecast day0 = new DailyForecast(LocalDate.of(2026, 10, 14), 0, 20.0, 10.0, 20.0, 10.0, null, 0.0, 0, 10.0);
        DailyForecast day1 = new DailyForecast(LocalDate.of(2026, 10, 15), 3, 19.0, 11.0, 19.0, 11.0, 5.0, 1.0, 30, 8.0);

        WeatherSession session = new WeatherSession("s2", "g1", "c1", "u1", "m1", location, 1, List.of(day0, day1), Instant.now().plusSeconds(3600));

        var rendered = builder.render(session, null);

        assertThat(rendered.embeds()).hasSize(1);
        List<?> buttons = (List<?>) rendered.components().get(0).get("components");
        Map<?, ?> prevButton = (Map<?, ?>) buttons.get(0);
        assertThat(prevButton.get("disabled")).isEqualTo(false);
        assertThat(prevButton.get("label")).isEqualTo("◀ Anterior");

        Map<?, ?> todayButton = (Map<?, ?>) buttons.get(1);
        assertThat(todayButton.get("disabled")).isEqualTo(false);
        assertThat(todayButton.get("label")).isEqualTo("📅 Hoje");

        Map<?, ?> nextButton = (Map<?, ?>) buttons.get(2);
        assertThat(nextButton.get("disabled")).isEqualTo(true);
        assertThat(nextButton.get("label")).isEqualTo("Próximo ▶");
    }

    @Test
    void shouldFormatAllDaysOfWeekInPortuguese() {
        Location location = new Location("Cidade", "UF", "Brasil", 0.0, 0.0, "UTC");
        LocalDate start = LocalDate.of(2026, 10, 12);
        for (int i = 0; i < 7; i++) {
            LocalDate date = start.plusDays(i);
            DailyForecast day = new DailyForecast(date, 0, 25.0, 15.0, 25.0, 15.0, 5.0, 0.0, 0, 5.0);
            WeatherSession session = new WeatherSession("s" + i, "g", "c", "u", "m", location, 0, List.of(day), Instant.now());
            var rendered = builder.render(session, null);
            String desc = rendered.embeds().get(0).get("description").toString();
            String expectedDay = switch (date.getDayOfWeek()) {
                case MONDAY -> "Segunda";
                case TUESDAY -> "Terça";
                case WEDNESDAY -> "Quarta";
                case THURSDAY -> "Quinta";
                case FRIDAY -> "Sexta";
                case SATURDAY -> "Sábado";
                case SUNDAY -> "Domingo";
            };
            assertThat(desc).contains(expectedDay);
        }
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

    @Test
    void shouldRenderError() {
        var rendered = builder.renderError("Erro teste");
        assertThat(rendered.embeds()).hasSize(1);
        assertThat(rendered.embeds().get(0).get("description").toString()).contains("Erro teste");

        var renderedDefault = builder.renderError(null);
        assertThat(renderedDefault.embeds().get(0).get("description").toString()).contains("erro inesperado");
    }
}
