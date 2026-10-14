package dev.gdinizds.discordweatherbot.adapter.out.persistence;

import dev.gdinizds.discordweatherbot.application.port.out.WeatherAuditLogPort;
import dev.gdinizds.discordweatherbot.config.Resilience;
import dev.gdinizds.discordweatherbot.domain.model.WeatherAuditLog;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;

public class JdbcWeatherAuditLogAdapter implements WeatherAuditLogPort {

    private static final String INSTANCE = "database";

    private static final String INSERT_LOG = """
            INSERT INTO weather_bot.weather_audit_log
                (correlation_id, guild_id, channel_id, user_id, command_name, trigger_type,
                 query_location, resolved_location, status, raw_request, raw_response, execution_time_ms, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final Resilience resilience;

    public JdbcWeatherAuditLogAdapter(JdbcTemplate jdbcTemplate, Resilience resilience) {
        this.jdbcTemplate = jdbcTemplate;
        this.resilience = resilience;
    }

    @Override
    public void save(WeatherAuditLog log) {
        resilience.run(INSTANCE, () -> jdbcTemplate.update(
                INSERT_LOG,
                log.correlationId(),
                log.guildId(),
                log.channelId(),
                log.userId(),
                log.commandName(),
                log.triggerType(),
                log.queryLocation(),
                log.resolvedLocation(),
                log.status(),
                log.rawRequest(),
                log.rawResponse(),
                log.executionTimeMs(),
                Timestamp.from(log.createdAt() != null ? log.createdAt() : Instant.now())
        ));
    }
}
