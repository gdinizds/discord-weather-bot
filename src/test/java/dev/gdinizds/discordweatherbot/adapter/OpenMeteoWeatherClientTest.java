package dev.gdinizds.discordweatherbot.adapter;

import com.github.tomakehurst.wiremock.WireMockServer;
import dev.gdinizds.discordweatherbot.adapter.out.openmeteo.OpenMeteoWeatherClient;
import dev.gdinizds.discordweatherbot.config.Resilience;
import dev.gdinizds.discordweatherbot.domain.model.Location;
import dev.gdinizds.discordweatherbot.domain.model.WeatherForecast;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenMeteoWeatherClientTest {

    private final WireMockServer wireMock = new WireMockServer(options().dynamicPort());
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private OpenMeteoWeatherClient client;

    @BeforeEach
    void setUp() {
        wireMock.start();

        var circuitBreakers = CircuitBreakerRegistry.ofDefaults();
        circuitBreakers.circuitBreaker("weather-api", CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .build());
        circuitBreakers.circuitBreaker("brasilapi", CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .build());

        var timeLimiters = TimeLimiterRegistry.ofDefaults();
        timeLimiters.timeLimiter("weather-api", TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(5)).build());
        timeLimiters.timeLimiter("brasilapi", TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(4)).build());

        var resilience = new Resilience(circuitBreakers, RetryRegistry.ofDefaults(), timeLimiters,
                BulkheadRegistry.ofDefaults(), executor);

        client = new OpenMeteoWeatherClient(
                RestClient.builder(),
                JsonMapper.builder().build(),
                resilience,
                wireMock.baseUrl() + "/geocoding",
                wireMock.baseUrl() + "/forecast",
                Duration.ofSeconds(5),
                wireMock.baseUrl() + "/cep",
                Duration.ofSeconds(4)
        );
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
        executor.shutdownNow();
    }

    @Test
    void shouldResolveLocationViaGeocoding() {
        wireMock.stubFor(get(urlPathEqualTo("/geocoding")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {
                          "results": [
                            {
                              "id": 3448439,
                              "name": "São Paulo",
                              "latitude": -23.5475,
                              "longitude": -46.63611,
                              "country": "Brasil",
                              "admin1": "São Paulo",
                              "timezone": "America/Sao_Paulo"
                            }
                          ]
                        }
                        """)));

        Optional<Location> location = client.resolveLocation("São Paulo", "SP", "Brasil");

        assertThat(location).isPresent();
        assertThat(location.get().name()).isEqualTo("São Paulo");
        assertThat(location.get().latitude()).isEqualTo(-23.5475);
        assertThat(location.get().longitude()).isEqualTo(-46.63611);
    }

    @Test
    void shouldResolveLocationViaBrasilApiForCep() {
        wireMock.stubFor(get(urlPathEqualTo("/cep/01001000")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {
                          "cep": "01001000",
                          "state": "SP",
                          "city": "São Paulo",
                          "neighborhood": "Sé",
                          "street": "Praça da Sé",
                          "location": {
                            "type": "Point",
                            "coordinates": {
                              "longitude": "-46.633081",
                              "latitude": "-23.5503898"
                            }
                          }
                        }
                        """)));

        Optional<Location> location = client.resolveLocation("01001-000", null, null);

        assertThat(location).isPresent();
        assertThat(location.get().name()).isEqualTo("São Paulo");
        assertThat(location.get().state()).isEqualTo("SP");
        assertThat(location.get().latitude()).isEqualTo(-23.5503898);
        assertThat(location.get().longitude()).isEqualTo(-46.633081);
    }

    @Test
    void shouldFetchForecastSuccessfully() {
        wireMock.stubFor(get(urlPathEqualTo("/forecast")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {
                          "current": {
                            "time": "2026-10-14T15:00",
                            "temperature_2m": 25.6,
                            "relative_humidity_2m": 57,
                            "apparent_temperature": 26.6,
                            "precipitation": 0.4,
                            "weather_code": 0,
                            "wind_speed_10m": 8.7,
                            "is_day": 1
                          },
                          "daily": {
                            "time": ["2026-10-14", "2026-10-15"],
                            "weather_code": [0, 61],
                            "temperature_2m_max": [28.8, 24.5],
                            "temperature_2m_min": [18.1, 18.0],
                            "apparent_temperature_max": [31.9, 27.6],
                            "apparent_temperature_min": [18.1, 18.8],
                            "uv_index_max": [8.8, 7.2],
                            "precipitation_sum": [0.0, 15.0],
                            "precipitation_probability_max": [10, 95],
                            "wind_speed_10m_max": [14.7, 13.3]
                          }
                        }
                        """)));

        Location location = new Location("São Paulo", "SP", "Brasil", -23.54, -46.63, "America/Sao_Paulo");
        WeatherForecast forecast = client.fetchForecast(location);

        assertThat(forecast.current()).isNotNull();
        assertThat(forecast.current().temperature()).isEqualTo(25.6);
        assertThat(forecast.current().apparentTemperature()).isEqualTo(26.6);

        assertThat(forecast.daily()).hasSize(2);
        assertThat(forecast.daily().get(0).tempMax()).isEqualTo(28.8);
        assertThat(forecast.daily().get(1).tempMax()).isEqualTo(24.5);
        assertThat(forecast.daily().get(1).precipitationProbabilityMax()).isEqualTo(95);
    }
}
