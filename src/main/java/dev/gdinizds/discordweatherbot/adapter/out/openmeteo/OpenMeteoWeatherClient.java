package dev.gdinizds.discordweatherbot.adapter.out.openmeteo;

import dev.gdinizds.discordweatherbot.application.port.out.WeatherClientPort;
import dev.gdinizds.discordweatherbot.config.Resilience;
import dev.gdinizds.discordweatherbot.domain.model.CurrentWeather;
import dev.gdinizds.discordweatherbot.domain.model.DailyForecast;
import dev.gdinizds.discordweatherbot.domain.model.Location;
import dev.gdinizds.discordweatherbot.domain.model.WeatherForecast;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class OpenMeteoWeatherClient implements WeatherClientPort {

    private static final Logger log = LoggerFactory.getLogger(OpenMeteoWeatherClient.class);
    static final String INSTANCE_WEATHER = "weather-api";
    static final String INSTANCE_BRASILAPI = "brasilapi";

    private final RestClient geocodingClient;
    private final RestClient forecastClient;
    private final RestClient brasilApiClient;
    private final ObjectMapper objectMapper;
    private final Resilience resilience;

    public OpenMeteoWeatherClient(RestClient.Builder builder,
                                  ObjectMapper objectMapper,
                                  Resilience resilience,
                                  String geocodingUrl,
                                  String forecastUrl,
                                  Duration weatherTimeout,
                                  String brasilApiUrl,
                                  Duration brasilApiTimeout) {
        this.objectMapper = objectMapper;
        this.resilience = resilience;

        SimpleClientHttpRequestFactory weatherFactory = new SimpleClientHttpRequestFactory();
        weatherFactory.setConnectTimeout((int) Math.min(weatherTimeout.toMillis(), 5000));
        weatherFactory.setReadTimeout((int) weatherTimeout.toMillis());

        this.geocodingClient = builder.clone()
                .baseUrl(trimSlash(geocodingUrl))
                .requestFactory(weatherFactory)
                .build();

        this.forecastClient = builder.clone()
                .baseUrl(trimSlash(forecastUrl))
                .requestFactory(weatherFactory)
                .build();

        SimpleClientHttpRequestFactory brasilApiFactory = new SimpleClientHttpRequestFactory();
        brasilApiFactory.setConnectTimeout((int) Math.min(brasilApiTimeout.toMillis(), 4000));
        brasilApiFactory.setReadTimeout((int) brasilApiTimeout.toMillis());

        this.brasilApiClient = builder.clone()
                .baseUrl(trimSlash(brasilApiUrl))
                .requestFactory(brasilApiFactory)
                .build();
    }

    @Override
    public Optional<Location> resolveLocation(String query, String state, String country) {
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }

        String digitsOnly = query.replaceAll("\\D", "");
        if (digitsOnly.length() == 8) {
            Optional<Location> cepLoc = tryResolveCep(digitsOnly);
            if (cepLoc.isPresent()) {
                return cepLoc;
            }
        }

        return resolveViaOpenMeteo(query, state, country);
    }

    private Optional<Location> tryResolveCep(String cep) {
        try {
            String body = resilience.call(INSTANCE_BRASILAPI, () -> brasilApiClient.get()
                    .uri("/{cep}", cep)
                    .retrieve()
                    .body(String.class));

            JsonNode root = parseJson(body);
            String city = root.path("city").asString("").trim();
            String state = root.path("state").asString("").trim();
            JsonNode coords = root.path("location").path("coordinates");

            double lon = parseDoubleNode(coords.path("longitude"));
            double lat = parseDoubleNode(coords.path("latitude"));

            if (!Double.isNaN(lat) && !Double.isNaN(lon) && !city.isBlank()) {
                return Optional.of(new Location(city, state, "Brasil", lat, lon, "America/Sao_Paulo"));
            }
            if (!city.isBlank()) {
                return resolveViaOpenMeteo(city, state, "Brasil");
            }
        } catch (Exception e) {
            log.warn("BrasilAPI lookup failed for CEP {}: {}", cep, e.getMessage());
        }
        return Optional.empty();
    }

    private Optional<Location> resolveViaOpenMeteo(String query, String state, String country) {
        try {
            String body = resilience.call(INSTANCE_WEATHER, () -> geocodingClient.get()
                    .uri(uri -> uri
                            .queryParam("name", query)
                            .queryParam("count", 5)
                            .queryParam("language", "pt")
                            .queryParam("format", "json")
                            .build())
                    .retrieve()
                    .body(String.class));

            JsonNode root = parseJson(body);
            JsonNode results = root.path("results");
            if (!results.isArray() || results.isEmpty()) {
                return Optional.empty();
            }

            JsonNode selected = results.get(0);
            if (state != null && !state.isBlank() || country != null && !country.isBlank()) {
                for (JsonNode item : results) {
                    boolean stateMatch = state == null || state.isBlank()
                            || item.path("admin1").asString("").equalsIgnoreCase(state)
                            || item.path("admin2").asString("").equalsIgnoreCase(state);
                    boolean countryMatch = country == null || country.isBlank()
                            || item.path("country").asString("").equalsIgnoreCase(country)
                            || item.path("country_code").asString("").equalsIgnoreCase(country);

                    if (stateMatch && countryMatch) {
                        selected = item;
                        break;
                    }
                }
            }

            String name = selected.path("name").asString("");
            String admin1 = selected.path("admin1").asString(null);
            String countryName = selected.path("country").asString(null);
            double latitude = selected.path("latitude").asDouble();
            double longitude = selected.path("longitude").asDouble();
            String timezone = selected.path("timezone").asString("auto");

            return Optional.of(new Location(name, admin1, countryName, latitude, longitude, timezone));
        } catch (Exception e) {
            log.error("Failed to resolve location via Open-Meteo for query '{}': {}", query, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public WeatherForecast fetchForecast(Location location) {
        String body = resilience.call(INSTANCE_WEATHER, () -> forecastClient.get()
                .uri(uri -> uri
                        .queryParam("latitude", location.latitude())
                        .queryParam("longitude", location.longitude())
                        .queryParam("current", "temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m,is_day")
                        .queryParam("daily", "weather_code,temperature_2m_max,temperature_2m_min,apparent_temperature_max,apparent_temperature_min,uv_index_max,precipitation_sum,precipitation_probability_max,wind_speed_10m_max")
                        .queryParam("timezone", "auto")
                        .queryParam("forecast_days", 7)
                        .build())
                .retrieve()
                .body(String.class));

        JsonNode root = parseJson(body);

        CurrentWeather current = null;
        JsonNode currentNode = root.path("current");
        if (!currentNode.isMissingNode() && !currentNode.isNull()) {
            LocalDateTime time = parseDateTime(currentNode.path("time").asString());
            current = new CurrentWeather(
                    time,
                    currentNode.path("temperature_2m").asDouble(),
                    currentNode.path("apparent_temperature").asDouble(),
                    currentNode.path("relative_humidity_2m").asInt(),
                    currentNode.path("precipitation").asDouble(),
                    currentNode.path("weather_code").asInt(),
                    currentNode.path("wind_speed_10m").asDouble(),
                    currentNode.path("is_day").asInt(1)
            );
        }

        List<DailyForecast> dailyList = new ArrayList<>();
        JsonNode dailyNode = root.path("daily");
        if (!dailyNode.isMissingNode() && !dailyNode.isNull()) {
            JsonNode timeArr = dailyNode.path("time");
            JsonNode codeArr = dailyNode.path("weather_code");
            JsonNode maxArr = dailyNode.path("temperature_2m_max");
            JsonNode minArr = dailyNode.path("temperature_2m_min");
            JsonNode appMaxArr = dailyNode.path("apparent_temperature_max");
            JsonNode appMinArr = dailyNode.path("apparent_temperature_min");
            JsonNode uvArr = dailyNode.path("uv_index_max");
            JsonNode precipSumArr = dailyNode.path("precipitation_sum");
            JsonNode precipProbArr = dailyNode.path("precipitation_probability_max");
            JsonNode windArr = dailyNode.path("wind_speed_10m_max");

            for (int i = 0; i < timeArr.size(); i++) {
                LocalDate date = LocalDate.parse(timeArr.get(i).asString());
                int weatherCode = codeArr.get(i).asInt();
                double tempMax = maxArr.get(i).asDouble();
                double tempMin = minArr.get(i).asDouble();
                double apparentTempMax = appMaxArr.get(i).asDouble();
                double apparentTempMin = appMinArr.get(i).asDouble();
                Double uvIndexMax = uvArr.has(i) && !uvArr.get(i).isNull() ? uvArr.get(i).asDouble() : null;
                double precipSum = precipSumArr.has(i) ? precipSumArr.get(i).asDouble() : 0.0;
                int precipProb = precipProbArr.has(i) ? precipProbArr.get(i).asInt() : 0;
                double windSpeedMax = windArr.has(i) ? windArr.get(i).asDouble() : 0.0;

                dailyList.add(new DailyForecast(
                        date,
                        weatherCode,
                        tempMax,
                        tempMin,
                        apparentTempMax,
                        apparentTempMin,
                        uvIndexMax,
                        precipSum,
                        precipProb,
                        windSpeedMax
                ));
            }
        }

        return new WeatherForecast(location, current, dailyList);
    }

    private JsonNode parseJson(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse weather JSON response: " + e.getMessage(), e);
        }
    }

    private static double parseDoubleNode(JsonNode node) {
        if (node.isNumber()) return node.asDouble();
        if (node.isTextual()) {
            try {
                return Double.parseDouble(node.asString());
            } catch (NumberFormatException ignored) {}
        }
        return Double.NaN;
    }

    private static LocalDateTime parseDateTime(String raw) {
        try {
            return LocalDateTime.parse(raw);
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
