package dev.gdinizds.discordweatherbot.config;

import dev.gdinizds.discordweatherbot.adapter.in.kafka.InboundEventConsumer;
import dev.gdinizds.discordweatherbot.adapter.in.kafka.InboundEventMapper;
import dev.gdinizds.discordweatherbot.adapter.out.kafka.GatewayResponsePublisher;
import dev.gdinizds.discordweatherbot.adapter.out.openmeteo.OpenMeteoWeatherClient;
import dev.gdinizds.discordweatherbot.adapter.out.persistence.JdbcUserWeatherConfigAdapter;
import dev.gdinizds.discordweatherbot.adapter.out.persistence.JdbcWeatherAuditLogAdapter;
import dev.gdinizds.discordweatherbot.adapter.out.persistence.JdbcWeatherSessionAdapter;
import dev.gdinizds.discordweatherbot.application.port.in.ConfigureUserWeatherUseCase;
import dev.gdinizds.discordweatherbot.application.port.in.ExecuteWeatherQueryUseCase;
import dev.gdinizds.discordweatherbot.application.port.in.NavigateWeatherUseCase;
import dev.gdinizds.discordweatherbot.application.port.out.ReplyPublisherPort;
import dev.gdinizds.discordweatherbot.application.port.out.UserWeatherConfigPort;
import dev.gdinizds.discordweatherbot.application.port.out.WeatherAuditLogPort;
import dev.gdinizds.discordweatherbot.application.port.out.WeatherClientPort;
import dev.gdinizds.discordweatherbot.application.port.out.WeatherSessionPort;
import dev.gdinizds.discordweatherbot.application.service.WeatherApplicationService;
import dev.gdinizds.discordweatherbot.domain.service.WeatherLayoutBuilder;
import io.github.resilience4j.bulkhead.Bulkhead;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.ExecutorService;

@Configuration
@EnableConfigurationProperties(WeatherBotProperties.class)
public class ApplicationConfig {

    @Bean
    public WeatherLayoutBuilder weatherLayoutBuilder() {
        return new WeatherLayoutBuilder();
    }

    @Bean
    public InboundEventMapper inboundEventMapper() {
        return new InboundEventMapper();
    }

    @Bean
    public WeatherClientPort weatherClientPort(RestClient.Builder restClientBuilder,
                                              ObjectMapper objectMapper,
                                              Resilience resilience,
                                              WeatherBotProperties properties) {
        return new OpenMeteoWeatherClient(
                restClientBuilder,
                objectMapper,
                resilience,
                properties.openmeteo().geocodingUrl(),
                properties.openmeteo().forecastUrl(),
                properties.openmeteo().timeout(),
                properties.brasilapi().cepUrl(),
                properties.brasilapi().timeout()
        );
    }

    @Bean
    public UserWeatherConfigPort userWeatherConfigPort(JdbcTemplate jdbcTemplate, Resilience resilience) {
        return new JdbcUserWeatherConfigAdapter(jdbcTemplate, resilience);
    }

    @Bean
    public WeatherSessionPort weatherSessionPort(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, Resilience resilience) {
        return new JdbcWeatherSessionAdapter(jdbcTemplate, objectMapper, resilience);
    }

    @Bean
    public WeatherAuditLogPort weatherAuditLogPort(JdbcTemplate jdbcTemplate, Resilience resilience) {
        return new JdbcWeatherAuditLogAdapter(jdbcTemplate, resilience);
    }

    @Bean
    public ReplyPublisherPort replyPublisherPort(KafkaTemplate<String, String> kafkaTemplate,
                                                 ObjectMapper objectMapper,
                                                 WeatherBotProperties properties) {
        return new GatewayResponsePublisher(kafkaTemplate, objectMapper, properties);
    }

    @Bean
    public WeatherApplicationService weatherApplicationService(WeatherClientPort weatherClient,
                                                               UserWeatherConfigPort userWeatherConfigPort,
                                                               WeatherSessionPort weatherSessionPort,
                                                               WeatherAuditLogPort weatherAuditLogPort,
                                                               ReplyPublisherPort replyPublisher,
                                                               WeatherLayoutBuilder layoutBuilder,
                                                               WeatherBotProperties properties) {
        return new WeatherApplicationService(
                weatherClient,
                userWeatherConfigPort,
                weatherSessionPort,
                weatherAuditLogPort,
                replyPublisher,
                layoutBuilder,
                properties.session().ttl()
        );
    }

    @Bean
    public InboundEventConsumer inboundEventConsumer(ObjectMapper objectMapper,
                                                     InboundEventMapper mapper,
                                                     ExecuteWeatherQueryUseCase queryUseCase,
                                                     ConfigureUserWeatherUseCase configureUseCase,
                                                     NavigateWeatherUseCase navigateUseCase,
                                                     Bulkhead weatherBulkhead,
                                                     @Qualifier("weatherExecutor") ExecutorService executor,
                                                     WeatherBotProperties properties) {
        var consumer = new InboundEventConsumer(
                objectMapper,
                mapper,
                queryUseCase,
                configureUseCase,
                navigateUseCase,
                weatherBulkhead,
                executor
        );
        consumer.setDrainTimeout(properties.executor().drainTimeout());
        return consumer;
    }
}
