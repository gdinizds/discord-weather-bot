package dev.gdinizds.discordweatherbot.domain.service;

import dev.gdinizds.discordweatherbot.domain.model.CurrentWeather;
import dev.gdinizds.discordweatherbot.domain.model.DailyForecast;
import dev.gdinizds.discordweatherbot.domain.model.Location;
import dev.gdinizds.discordweatherbot.domain.model.WeatherSession;
import dev.gdinizds.discordweatherbot.domain.service.WeatherCodeTranslator.WeatherCondition;

import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class WeatherLayoutBuilder {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

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
        embed.put("title", condition.emoji() + " Clima em " + location.displayName());

        StringBuilder desc = new StringBuilder();
        desc.append(formatTemp(targetDay.tempMax())).append(" / ").append(formatTemp(targetDay.tempMin()))
                .append(" — ").append(condition.emoji()).append(" ").append(condition.description()).append("\n");
        desc.append("🌡️ Máx / Mín:🔺 ").append(formatTemp(targetDay.tempMax())).append(" / 🔻 ").append(formatTemp(targetDay.tempMin())).append("\n");
        desc.append("💨 Vento Máx: ").append(targetDay.windSpeedMax()).append(" km/h\n");
        desc.append("🌧️ Chuva: ").append(targetDay.precipitationProbabilityMax()).append("% (").append(targetDay.precipitationSum()).append(" mm)\n");
        if (targetDay.uvIndexMax() != null) {
            desc.append("☀️ Índice UV: ").append(String.format(Locale.US, "%.1f", targetDay.uvIndexMax())).append("\n");
        }
        desc.append("📅 Data\n");
        desc.append(formatDayOfWeek(targetDay.date().getDayOfWeek())).append(", ").append(targetDay.date().format(DATE_FORMATTER));

        embed.put("description", desc.toString());
        embed.put("fields", List.of());
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
                "label", "◀ Anterior",
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
                "label", "Próximo ▶",
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

    private static String formatDayOfWeek(DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case MONDAY -> "Segunda";
            case TUESDAY -> "Terça";
            case WEDNESDAY -> "Quarta";
            case THURSDAY -> "Quinta";
            case FRIDAY -> "Sexta";
            case SATURDAY -> "Sábado";
            case SUNDAY -> "Domingo";
        };
    }
}
