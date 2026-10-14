package dev.gdinizds.discordweatherbot.domain.service;

public final class CommandTextParser {

    public enum ActionType {
        QUERY,
        CONFIGURE
    }

    public record ParsedCommand(
            ActionType actionType,
            String local,
            String estado,
            String pais
    ) {}

    private CommandTextParser() {}

    public static ParsedCommand parse(String commandName, String content) {
        String trimmedContent = content != null ? content.strip() : "";

        if ("clima-config".equalsIgnoreCase(commandName)) {
            return parseLocationParts(ActionType.CONFIGURE, trimmedContent);
        }

        if (trimmedContent.toLowerCase().startsWith("config ") || trimmedContent.toLowerCase().startsWith("configure ")) {
            String remainder = trimmedContent.replaceFirst("(?i)^(config|configure)\\s+", "").strip();
            return parseLocationParts(ActionType.CONFIGURE, remainder);
        }

        if (trimmedContent.equalsIgnoreCase("config") || trimmedContent.equalsIgnoreCase("configure")) {
            return new ParsedCommand(ActionType.CONFIGURE, null, null, null);
        }

        if (trimmedContent.isBlank()) {
            return new ParsedCommand(ActionType.QUERY, null, null, null);
        }

        return parseLocationParts(ActionType.QUERY, trimmedContent);
    }

    private static ParsedCommand parseLocationParts(ActionType actionType, String raw) {
        if (raw == null || raw.isBlank()) {
            return new ParsedCommand(actionType, null, null, null);
        }

        if (raw.contains(",")) {
            String[] segments = raw.split(",");
            String local = segments[0].strip();
            String estado = segments.length > 1 && !segments[1].isBlank() ? segments[1].strip() : null;
            String pais = segments.length > 2 && !segments[2].isBlank() ? segments[2].strip() : null;
            return new ParsedCommand(actionType, local, estado, pais);
        }

        return new ParsedCommand(actionType, raw.strip(), null, null);
    }
}
