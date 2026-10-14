package dev.gdinizds.discordweatherbot.adapter.out.persistence;

import dev.gdinizds.discordweatherbot.application.port.out.UserWeatherConfigPort;
import dev.gdinizds.discordweatherbot.config.Resilience;
import dev.gdinizds.discordweatherbot.domain.model.UserWeatherConfig;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class JdbcUserWeatherConfigAdapter implements UserWeatherConfigPort {

    private static final String INSTANCE = "database";

    private static final String SELECT_BY_USER_ID = """
            SELECT user_id, city, state, country, latitude, longitude, timezone, created_at, updated_at
            FROM weather_bot.user_weather_config
            WHERE user_id = ?
            """;

    private static final String UPSERT_CONFIG = """
            INSERT INTO weather_bot.user_weather_config
                (user_id, city, state, country, latitude, longitude, timezone, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (user_id) DO UPDATE SET
                city = EXCLUDED.city,
                state = EXCLUDED.state,
                country = EXCLUDED.country,
                latitude = EXCLUDED.latitude,
                longitude = EXCLUDED.longitude,
                timezone = EXCLUDED.timezone,
                updated_at = NOW()
            """;

    private final JdbcTemplate jdbcTemplate;
    private final Resilience resilience;
    private final RowMapper<UserWeatherConfig> rowMapper = new UserConfigRowMapper();

    public JdbcUserWeatherConfigAdapter(JdbcTemplate jdbcTemplate, Resilience resilience) {
        this.jdbcTemplate = jdbcTemplate;
        this.resilience = resilience;
    }

    @Override
    public Optional<UserWeatherConfig> findByUserId(String userId) {
        return resilience.call(INSTANCE, () -> {
            List<UserWeatherConfig> results = jdbcTemplate.query(SELECT_BY_USER_ID, rowMapper, userId);
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        });
    }

    @Override
    public UserWeatherConfig save(UserWeatherConfig config) {
        resilience.run(INSTANCE, () -> jdbcTemplate.update(
                UPSERT_CONFIG,
                config.userId(),
                config.city(),
                config.state(),
                config.country(),
                config.latitude(),
                config.longitude(),
                config.timezone(),
                Timestamp.from(config.createdAt() != null ? config.createdAt() : Instant.now()),
                Timestamp.from(Instant.now())
        ));
        return config;
    }

    private static class UserConfigRowMapper implements RowMapper<UserWeatherConfig> {
        @Override
        public UserWeatherConfig mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new UserWeatherConfig(
                    rs.getString("user_id"),
                    rs.getString("city"),
                    rs.getString("state"),
                    rs.getString("country"),
                    rs.getDouble("latitude"),
                    rs.getDouble("longitude"),
                    rs.getString("timezone"),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant()
            );
        }
    }
}
