package dev.gdinizds.discordweatherbot.adapter;

import dev.gdinizds.discordweatherbot.adapter.out.persistence.JdbcWeatherSessionAdapter;
import dev.gdinizds.discordweatherbot.config.Resilience;
import dev.gdinizds.discordweatherbot.domain.model.DailyForecast;
import dev.gdinizds.discordweatherbot.domain.model.Location;
import dev.gdinizds.discordweatherbot.domain.model.WeatherSession;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JdbcWeatherSessionAdapterTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private JdbcWeatherSessionAdapter adapter;

    @BeforeEach
    void setUp() {
        var resilience = new Resilience(
                CircuitBreakerRegistry.ofDefaults(),
                RetryRegistry.ofDefaults(),
                TimeLimiterRegistry.ofDefaults(),
                BulkheadRegistry.ofDefaults(),
                Executors.newVirtualThreadPerTaskExecutor()
        );

        adapter = new JdbcWeatherSessionAdapter(jdbcTemplate, objectMapper, resilience);
    }

    @Test
    void shouldFindSessionByIdWhenPresent() {
        Location location = new Location("Recife", "PE", "Brasil", -8.05, -34.88, "America/Recife");
        DailyForecast day = new DailyForecast(LocalDate.now(), 0, 30.0, 24.0, 32.0, 25.0, 9.0, 0.0, 5, 20.0);
        WeatherSession expected = new WeatherSession(
                "sess-100", "g1", "ch1", "u1", "m1", location, 0, List.of(day), null, Instant.now().plusSeconds(3600)
        );

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("sess-100")))
                .thenReturn(List.of(expected));

        Optional<WeatherSession> found = adapter.findById("sess-100");
        assertThat(found).isPresent();
        assertThat(found.get().sessionId()).isEqualTo("sess-100");
        assertThat(found.get().location().name()).isEqualTo("Recife");
    }

    @Test
    void shouldReturnEmptyWhenSessionNotFound() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("missing")))
                .thenReturn(List.of());

        Optional<WeatherSession> found = adapter.findById("missing");
        assertThat(found).isEmpty();
    }

    @Test
    void shouldSaveSession() {
        Location location = new Location("Recife", "PE", "Brasil", -8.05, -34.88, "America/Recife");
        DailyForecast day = new DailyForecast(LocalDate.now(), 0, 30.0, 24.0, 32.0, 25.0, 9.0, 0.0, 5, 20.0);
        WeatherSession session = new WeatherSession(
                "sess-100", "g1", "ch1", "u1", "m1", location, 0, List.of(day), null, Instant.now().plusSeconds(3600)
        );

        adapter.save(session);

        verify(jdbcTemplate).update(
                anyString(),
                eq("sess-100"),
                eq("g1"),
                eq("ch1"),
                eq("u1"),
                eq("m1"),
                eq("Recife"),
                eq("PE"),
                eq("Brasil"),
                eq(-8.05),
                eq(-34.88),
                eq("America/Recife"),
                eq(0),
                anyString(),
                any()
        );
    }
}
