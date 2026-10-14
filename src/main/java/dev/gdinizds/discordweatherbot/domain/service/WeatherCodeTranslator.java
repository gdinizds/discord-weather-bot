package dev.gdinizds.discordweatherbot.domain.service;

public final class WeatherCodeTranslator {

    public record WeatherCondition(String emoji, String description, int colorHex) {}

    private WeatherCodeTranslator() {}

    public static WeatherCondition translate(int code) {
        return switch (code) {
            case 0 -> new WeatherCondition("☀️", "Céu limpo", 0xF59E0B);
            case 1 -> new WeatherCondition("🌤️", "Principalmente limpo", 0xFBBF24);
            case 2 -> new WeatherCondition("⛅", "Parcialmente nublado", 0x38BDF8);
            case 3 -> new WeatherCondition("☁️", "Encoberto", 0x64748B);
            case 45, 48 -> new WeatherCondition("🌫️", "Nevoeiro", 0x94A3B8);
            case 51 -> new WeatherCondition("🌦️", "Garoa leve", 0x60A5FA);
            case 53 -> new WeatherCondition("🌦️", "Garoa moderada", 0x3B82F6);
            case 55 -> new WeatherCondition("🌧️", "Garoa intensa", 0x2563EB);
            case 56, 57 -> new WeatherCondition("🌨️", "Garoa congelante", 0x93C5FD);
            case 61 -> new WeatherCondition("🌧️", "Chuva leve", 0x3B82F6);
            case 63 -> new WeatherCondition("🌧️", "Chuva moderada", 0x2563EB);
            case 65 -> new WeatherCondition("🌧️", "Chuva forte", 0x1D4ED8);
            case 66, 67 -> new WeatherCondition("🌨️", "Chuva congelante", 0x93C5FD);
            case 71 -> new WeatherCondition("🌨️", "Neve leve", 0xE2E8F0);
            case 73 -> new WeatherCondition("🌨️", "Neve moderada", 0xCBD5E1);
            case 75 -> new WeatherCondition("🌨️", "Neve forte", 0x94A3B8);
            case 77 -> new WeatherCondition("🌨️", "Grãos de neve", 0xE2E8F0);
            case 80 -> new WeatherCondition("🌦️", "Pancadas de chuva leves", 0x60A5FA);
            case 81 -> new WeatherCondition("🌧️", "Pancadas de chuva moderadas", 0x2563EB);
            case 82 -> new WeatherCondition("⛈️", "Pancadas de chuva fortes", 0x1E40AF);
            case 85, 86 -> new WeatherCondition("🌨️", "Pancadas de neve", 0xCBD5E1);
            case 95 -> new WeatherCondition("⛈️", "Tempestade com trovoadas", 0x7C3AED);
            case 96, 99 -> new WeatherCondition("⛈️", "Tempestade com granizo", 0x5B21B6);
            default -> new WeatherCondition("🌡️", "Clima atual", 0x0284C7);
        };
    }
}
