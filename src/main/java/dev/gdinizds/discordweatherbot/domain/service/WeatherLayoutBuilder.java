package dev.gdinizds.discordweatherbot.domain.service;

import dev.gdinizds.discordweatherbot.domain.model.CurrentWeather;
import dev.gdinizds.discordweatherbot.domain.model.DailyForecast;
import dev.gdinizds.discordweatherbot.domain.model.Location;
import dev.gdinizds.discordweatherbot.domain.model.WeatherSession;
import dev.gdinizds.discordweatherbot.domain.service.WeatherCodeTranslator.WeatherCondition;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class WeatherLayoutBuilder {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy", Locale.of("pt", "BR"));
    private static final DateTimeFormatter SHORT_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM", Locale.of("pt", "BR"));

    public record RenderedWeather(
            String content,
            List<Map<String, Object>> embeds,
            List<Map<String, Object>> components
    ) {}

    public RenderedWeather render(WeatherSession session, CurrentWeather current) {
        int index = session.currentDayIndex();
        List<DailyForecast> daily = session.daily();
        DailyForecast targetDay = daily.get(Math.min(index, daily.size() - 1));
        Location location = session.location();

        WeatherCondition condition = WeatherCodeTranslator.translate(targetDay.weatherCode());

        Map<String, Object> embed = new HashMap<>();
        embed.put("color", condition.colorHex());

        List<Map<String, Object>> fields = new ArrayList<>();

        if (index == 0) {
            embed.put("title", condition.emoji() + " Clima em " + location.displayName());
            StringBuilder desc = new StringBuilder();
            if (current != null) {
                desc.append("### ").append(formatTemp(current.temperature()))
                        .append(" — ").append(condition.emoji()).append(" ").append(condition.description()).append("\n");
                desc.append("Sensação Térmica: **").append(formatTemp(current.apparentTemperature())).append("**\n");
            } else {
                desc.append("### ").append(formatTemp(targetDay.tempMax())).append(" / ").append(formatTemp(targetDay.tempMin()))
                        .append(" — ").append(condition.emoji()).append(" ").append(condition.description()).append("\n");
            }
            embed.put("description", desc.toString());

            fields.add(Map.of("name", "🌡️ Máx / Mín", "value", "🔺 " + formatTemp(targetDay.tempMax()) + " / 🔻 " + formatTemp(targetDay.tempMin()), "inline", true));
            if (current != null) {
                fields.add(Map.of("name", "💧 Umidade", "value", current.relativeHumidity() + "%", "inline", true));
                fields.add(Map.of("name", "💨 Vento", "value", current.windSpeed() + " km/h", "inline", true));
            } else {
                fields.add(Map.of("name", "💨 Vento Máx", "value", targetDay.windSpeedMax() + " km/h", "inline", true));
            }
            fields.add(Map.of("name", "🌧️ Chuva", "value", targetDay.precipitationProbabilityMax() + "% (" + targetDay.precipitationSum() + " mm)", "inline", true));
            if (targetDay.uvIndexMax() != null) {
                fields.add(Map.of("name", "☀️ Índice UV", "value", String.format(Locale.US, "%.1f", targetDay.uvIndexMax()), "inline", true));
            }
            fields.add(Map.of("name", "📅 Data", "value", capitalize(targetDay.date().format(DATE_FORMATTER)), "inline", true));

            if (daily.size() > 1) {
                StringBuilder forecastSummary = new StringBuilder();
                for (int i = 1; i < Math.min(daily.size(), 7); i++) {
                    DailyForecast d = daily.get(i);
                    WeatherCondition c = WeatherCodeTranslator.translate(d.weatherCode());
                    forecastSummary.append("`").append(d.date().format(SHORT_DATE_FORMATTER)).append("` ")
                            .append(c.emoji()).append(" ")
                            .append(formatTemp(d.tempMin())).append(" - ").append(formatTemp(d.tempMax()))
                            .append(" (").append(d.precipitationProbabilityMax()).append("% 🌧️)\n");
                }
                fields.add(Map.of("name", "🔮 Próximos Dias", "value", forecastSummary.toString(), "inline", false));
            }
        } else {
            embed.put("title", "📅 Previsão para " + capitalize(targetDay.date().format(DATE_FORMATTER)) + " — " + location.displayName());
            String desc = "### " + formatTemp(targetDay.tempMax()) + " / " + formatTemp(targetDay.tempMin()) +
                    " — " + condition.emoji() + " " + condition.description() + "\n" +
                    "Sensação Térmica: 🔺 **" + formatTemp(targetDay.apparentTempMax()) + "** / 🔻 **" + formatTemp(targetDay.apparentTempMin()) + "**";
            embed.put("description", desc);

            fields.add(Map.of("name", "🌡️ Máx / Mín", "value", "🔺 " + formatTemp(targetDay.tempMax()) + " / 🔻 " + formatTemp(targetDay.tempMin()), "inline", true));
            fields.add(Map.of("name", "🌧️ Prob. de Chuva", "value", targetDay.precipitationProbabilityMax() + "%", "inline", true));
            fields.add(Map.of("name", "💧 Volume de Chuva", "value", targetDay.precipitationSum() + " mm", "inline", true));
            fields.add(Map.of("name", "💨 Vento Máximo", "value", targetDay.windSpeedMax() + " km/h", "inline", true));
            if (targetDay.uvIndexMax() != null) {
                fields.add(Map.of("name", "☀️ Índice UV Máx", "value", String.format(Locale.US, "%.1f", targetDay.uvIndexMax()), "inline", true));
            }
        }

        embed.put("fields", fields);
        embed.put("footer", Map.of("text", "Dia " + (index + 1) + " de " + daily.size() + " • Open-Meteo"));

        List<Map<String, Object>> components = buildNavigationButtons(session.sessionId(), index, daily.size());

        return new RenderedWeather(null, List.of(embed), components);
    }

    public RenderedWeather renderUnconfigured() {
        Map<String, Object> embed = new HashMap<>();
        embed.put("title", "🌍 Localização não configurada");
        embed.put("color", 0x3B82F6);
        embed.put("description", """
                Você ainda não definiu sua localização padrão!

                Para consultar o clima de forma rápida sem precisar digitar o local todas as vezes, configure agora:
                • Comando Slash: **/clima-config cidade: [Sua Cidade]**
                • Comando Prefixo: **.clima config [Sua Cidade], [Estado], [País]**

                *Exemplo:* `/clima-config cidade: São Paulo estado: SP pais: Brasil`
                """);
        return new RenderedWeather(null, List.of(embed), List.of());
    }

    public RenderedWeather renderNotFound(String query) {
        Map<String, Object> embed = new HashMap<>();
        embed.put("title", "🔍 Localização não encontrada");
        embed.put("color", 0xED4245);
        embed.put("description", "Não foi possível encontrar a localização **" + (query != null ? query : "") + "**. Verifique o nome da cidade ou CEP informado.");
        return new RenderedWeather(null, List.of(embed), List.of());
    }

    public RenderedWeather renderConfigSaved(Location location) {
        Map<String, Object> embed = new HashMap<>();
        embed.put("title", "✅ Localização configurada com sucesso!");
        embed.put("color", 0x10B981);
        embed.put("description", "Sua localização padrão foi definida para **" + location.displayName() + "**.\n\nAgora você pode usar `/clima` ou `.clima` diretamente sem parâmetros!");
        return new RenderedWeather(null, List.of(embed), List.of());
    }

    public RenderedWeather renderError(String message) {
        Map<String, Object> embed = new HashMap<>();
        embed.put("title", "⚠️ Falha ao consultar o clima");
        embed.put("color", 0xED4245);
        embed.put("description", message != null && !message.isBlank() ? message : "Ocorreu um erro inesperado ao consultar a previsão do tempo.");
        return new RenderedWeather(null, List.of(embed), List.of());
    }

    private List<Map<String, Object>> buildNavigationButtons(String sessionId, int currentIndex, int totalDays) {
        List<Map<String, Object>> buttons = new ArrayList<>();

        buttons.add(Map.of(
                "type", 2,
                "style", "SECONDARY",
                "custom_id", "weather:prev:" + sessionId,
                "label", "⬅️ Anterior",
                "disabled", currentIndex <= 0
        ));

        buttons.add(Map.of(
                "type", 2,
                "style", "PRIMARY",
                "custom_id", "weather:today:" + sessionId,
                "label", "📅 Hoje",
                "disabled", currentIndex == 0
        ));

        buttons.add(Map.of(
                "type", 2,
                "style", "SECONDARY",
                "custom_id", "weather:next:" + sessionId,
                "label", "Próximo ➡️",
                "disabled", currentIndex >= totalDays - 1
        ));

        return List.of(Map.of(
                "type", 1,
                "components", buttons
        ));
    }

    private static String formatTemp(double temp) {
        return Math.round(temp) + "°C";
    }

    private static String capitalize(String text) {
        if (text == null || text.isBlank()) return "";
        return text.substring(0, 1).toUpperCase() + text.substring(1);
    }
}
