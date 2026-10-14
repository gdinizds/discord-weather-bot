package dev.gdinizds.discordweatherbot.adapter;

import dev.gdinizds.discordweatherbot.adapter.out.persistence.JdbcUserWeatherConfigAdapter;
import dev.gdinizds.discordweatherbot.config.Resilience;
import dev.gdinizds.discordweatherbot.domain.model.Location;
import dev.gdinizds.discordweatherbot.domain.model.UserWeatherConfig;
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

import java.time.Instant;
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
class JdbcUserWeatherConfigAdapterTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private JdbcUserWeatherConfigAdapter adapter;

    @BeforeEach
    void setUp() {
        var resilience = new Resilience(
                CircuitBreakerRegistry.ofDefaults(),
                RetryRegistry.ofDefaults(),
                TimeLimiterRegistry.ofDefaults(),
                BulkheadRegistry.ofDefaults(),
                Executors.newVirtualThreadPerTaskExecutor()
        );

        adapter = new JdbcUserWeatherConfigAdapter(jdbcTemplate, resilience);
    }

    @Test
    void shouldFindUserConfigWhenPresent() {
        UserWeatherConfig expected = new UserWeatherConfig(
                "user-100", "Campinas", "SP", "Brasil", -22.9, -47.06, "America/Sao_Paulo",
                Instant.now(), Instant.now()
        );

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("user-100")))
                .thenReturn(List.of(expected));

        Optional<UserWeatherConfig> found = adapter.findByUserId("user-100");
        assertThat(found).isPresent();
        assertThat(found.get().userId()).isEqualTo("user-100");
        assertThat(found.get().city()).isEqualTo("Campinas");
    }

    @Test
    void shouldReturnEmptyWhenUserNotFound() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("user-not-found")))
                .thenReturn(List.of());

        Optional<UserWeatherConfig> found = adapter.findByUserId("user-not-found");
        assertThat(found).isEmpty();
    }

    @Test
    void shouldSaveUserConfig() {
        Location location = new Location("Campinas", "SP", "Brasil", -22.9, -47.06, "America/Sao_Paulo");
        UserWeatherConfig config = UserWeatherConfig.create("user-100", location);

        adapter.save(config);

        verify(jdbcTemplate).update(
                anyString(),
                eq("user-100"),
                eq("Campinas"),
                eq("SP"),
                eq("Brasil"),
                eq(-22.9),
                eq(-47.06),
                eq("America/Sao_Paulo"),
                any(),
                any()
        );
    }
}
