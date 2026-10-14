package dev.gdinizds.discordweatherbot.domain.model;

public record Location(
        String name,
        String state,
        String country,
        double latitude,
        double longitude,
        String timezone
) {
    public String displayName() {
        StringBuilder sb = new StringBuilder(name != null ? name : "");
        if (state != null && !state.isBlank()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(state);
        }
        if (country != null && !country.isBlank()) {
            if (!sb.isEmpty()) sb.append(" - ");
            sb.append(country);
        }
        return sb.toString();
    }
}
