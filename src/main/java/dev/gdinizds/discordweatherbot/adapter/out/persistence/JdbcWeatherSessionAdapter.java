package dev.gdinizds.discordweatherbot.adapter.out.persistence;

import dev.gdinizds.discordweatherbot.application.port.out.WeatherSessionPort;
import dev.gdinizds.discordweatherbot.config.Resilience;
import dev.gdinizds.discordweatherbot.domain.model.CurrentWeather;
import dev.gdinizds.discordweatherbot.domain.model.DailyForecast;
import dev.gdinizds.discordweatherbot.domain.model.Location;
import dev.gdinizds.discordweatherbot.domain.model.WeatherSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class JdbcWeatherSessionAdapter implements WeatherSessionPort {

    private static final String INSTANCE = "database";

    private static final String SELECT_BY_SESSION_ID = """
            SELECT session_id, guild_id, channel_id, user_id, origin_message_id,
                   location_name, location_state, location_country, latitude, longitude, timezone,
                   current_day_index, forecast_json, expires_at
            FROM weather_bot.weather_sessions
            WHERE session_id = ? AND expires_at > NOW()
            """;

    private static final String UPSERT_SESSION = """
            INSERT INTO weather_bot.weather_sessions
                (session_id, guild_id, channel_id, user_id, origin_message_id,
                 location_name, location_state, location_country, latitude, longitude, timezone,
                 current_day_index, forecast_json, expires_at, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            ON CONFLICT (session_id) DO UPDATE SET
                current_day_index = EXCLUDED.current_day_index,
                updated_at = NOW()
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Resilience resilience;

    public JdbcWeatherSessionAdapter(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, Resilience resilience) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.resilience = resilience;
    }

    @Override
    public Optional<WeatherSession> findById(String sessionId) {
        return resilience.call(INSTANCE, () -> {
            List<WeatherSession> results = jdbcTemplate.query(SELECT_BY_SESSION_ID, new SessionRowMapper(), sessionId);
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        });
    }

    @Override
    public void save(WeatherSession session) {
        String forecastJson = serializeForecast(session.current(), session.daily());
        resilience.run(INSTANCE, () -> jdbcTemplate.update(
                UPSERT_SESSION,
                session.sessionId(),
                session.guildId(),
                session.channelId(),
                session.userId(),
                session.originMessageId(),
                session.location().name(),
                session.location().state(),
                session.location().country(),
                session.location().latitude(),
                session.location().longitude(),
                session.location().timezone(),
                session.currentDayIndex(),
                forecastJson,
                Timestamp.from(session.expiresAt())
        ));
    }

    @Override
    public int countActive() {
        return resilience.call(INSTANCE, () -> {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM weather_bot.weather_sessions WHERE expires_at > NOW()",
                    Integer.class
            );
            return count != null ? count : 0;
        });
    }

    private record ForecastPayload(CurrentWeather current, List<DailyForecast> daily) {}

    private String serializeForecast(CurrentWeather current, List<DailyForecast> daily) {
        try {
            return objectMapper.writeValueAsString(new ForecastPayload(current, daily));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize forecast: " + e.getMessage(), e);
        }
    }

    private ForecastPayload deserializeForecast(String json) {
        try {
            if (json == null || json.isBlank()) {
                return new ForecastPayload(null, List.of());
            }
            if (json.trim().startsWith("[")) {
                List<DailyForecast> daily = objectMapper.readValue(json, new TypeReference<List<DailyForecast>>() {});
                return new ForecastPayload(null, daily);
            }
            return objectMapper.readValue(json, ForecastPayload.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize forecast: " + e.getMessage(), e);
        }
    }

    private class SessionRowMapper implements RowMapper<WeatherSession> {
        @Override
        public WeatherSession mapRow(ResultSet rs, int rowNum) throws SQLException {
            Location location = new Location(
                    rs.getString("location_name"),
                    rs.getString("location_state"),
                    rs.getString("location_country"),
                    rs.getDouble("latitude"),
                    rs.getDouble("longitude"),
                    rs.getString("timezone")
            );
            ForecastPayload payload = deserializeForecast(rs.getString("forecast_json"));
            return new WeatherSession(
                    rs.getString("session_id"),
                    rs.getString("guild_id"),
                    rs.getString("channel_id"),
                    rs.getString("user_id"),
                    rs.getString("origin_message_id"),
                    location,
                    rs.getInt("current_day_index"),
                    payload.daily(),
                    payload.current(),
                    rs.getTimestamp("expires_at").toInstant()
            );
        }
    }
}
