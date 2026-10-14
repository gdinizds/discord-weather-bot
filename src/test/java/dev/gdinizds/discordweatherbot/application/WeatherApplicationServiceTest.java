package dev.gdinizds.discordweatherbot.application;

import dev.gdinizds.discordweatherbot.application.port.out.ReplyPublisherPort;
import dev.gdinizds.discordweatherbot.application.port.out.UserWeatherConfigPort;
import dev.gdinizds.discordweatherbot.application.port.out.WeatherAuditLogPort;
import dev.gdinizds.discordweatherbot.application.port.out.WeatherClientPort;
import dev.gdinizds.discordweatherbot.application.port.out.WeatherSessionPort;
import dev.gdinizds.discordweatherbot.application.service.WeatherApplicationService;
import dev.gdinizds.discordweatherbot.domain.model.ConfigureWeatherCommand;
import dev.gdinizds.discordweatherbot.domain.model.CurrentWeather;
import dev.gdinizds.discordweatherbot.domain.model.DailyForecast;
import dev.gdinizds.discordweatherbot.domain.model.Location;
import dev.gdinizds.discordweatherbot.domain.model.OutboundResponse;
import dev.gdinizds.discordweatherbot.domain.model.UserWeatherConfig;
import dev.gdinizds.discordweatherbot.domain.model.WeatherForecast;
import dev.gdinizds.discordweatherbot.domain.model.WeatherNavigationCommand;
import dev.gdinizds.discordweatherbot.domain.model.WeatherQueryCommand;
import dev.gdinizds.discordweatherbot.domain.model.WeatherSession;
import dev.gdinizds.discordweatherbot.domain.service.WeatherLayoutBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeatherApplicationServiceTest {

    @Mock
    private WeatherClientPort weatherClient;

    @Mock
    private UserWeatherConfigPort userWeatherConfigPort;

    @Mock
    private WeatherSessionPort weatherSessionPort;

    @Mock
    private WeatherAuditLogPort weatherAuditLogPort;

    @Mock
    private ReplyPublisherPort replyPublisher;

    private final WeatherLayoutBuilder layoutBuilder = new WeatherLayoutBuilder();
    private WeatherApplicationService service;

    private final Location sampleLocation = new Location("São Paulo", "SP", "Brasil", -23.54, -46.63, "America/Sao_Paulo");
    private final CurrentWeather sampleCurrent = new CurrentWeather(LocalDateTime.now(), 25.0, 26.0, 60, 0.0, 0, 10.0, 1);
    private final List<DailyForecast> sampleDaily = List.of(
            new DailyForecast(LocalDate.now(), 0, 28.0, 18.0, 29.0, 18.0, 8.0, 0.0, 10, 15.0),
            new DailyForecast(LocalDate.now().plusDays(1), 61, 22.0, 17.0, 22.0, 17.0, 4.0, 5.0, 80, 12.0)
    );

    @BeforeEach
    void setUp() {
        service = new WeatherApplicationService(
                weatherClient,
                userWeatherConfigPort,
                weatherSessionPort,
                weatherAuditLogPort,
                replyPublisher,
                layoutBuilder,
                Duration.ofHours(1)
        );
    }

    @Test
    void shouldExecuteQueryWithExplicitLocation() {
        WeatherQueryCommand command = new WeatherQueryCommand(
                "c1", "g1", "ch1", "u1", "tok1", null, "São Paulo", "SP", "Brasil", "SLASH", "{}"
        );

        when(weatherClient.resolveLocation("São Paulo", "SP", "Brasil")).thenReturn(Optional.of(sampleLocation));
        when(weatherClient.fetchForecast(sampleLocation)).thenReturn(new WeatherForecast(sampleLocation, sampleCurrent, sampleDaily));

        service.execute(command);

        verify(weatherSessionPort).save(any(WeatherSession.class));
        ArgumentCaptor<OutboundResponse> captor = ArgumentCaptor.forClass(OutboundResponse.class);
        verify(replyPublisher).publish(captor.capture());

        OutboundResponse response = captor.getValue();
        assertThat(response.correlationId()).isEqualTo("c1");
        assertThat(response.embeds()).isNotEmpty();
        verify(weatherAuditLogPort).save(any());
    }

    @Test
    void shouldExecuteQueryUsingConfiguredLocationWhenLocalIsBlank() {
        WeatherQueryCommand command = new WeatherQueryCommand(
                "c2", "g1", "ch1", "u1", "tok2", null, null, null, null, "SLASH", "{}"
        );

        UserWeatherConfig userConfig = UserWeatherConfig.create("u1", sampleLocation);
        when(userWeatherConfigPort.findByUserId("u1")).thenReturn(Optional.of(userConfig));
        when(weatherClient.resolveLocation("São Paulo", "SP", "Brasil")).thenReturn(Optional.of(sampleLocation));
        when(weatherClient.fetchForecast(sampleLocation)).thenReturn(new WeatherForecast(sampleLocation, sampleCurrent, sampleDaily));

        service.execute(command);

        verify(replyPublisher).publish(any(OutboundResponse.class));
    }

    @Test
    void shouldRenderUnconfiguredWhenNoLocationAndNoUserConfig() {
        WeatherQueryCommand command = new WeatherQueryCommand(
                "c3", "g1", "ch1", "uNew", "tok3", null, "", null, null, "SLASH", "{}"
        );

        when(userWeatherConfigPort.findByUserId("uNew")).thenReturn(Optional.empty());

        service.execute(command);

        ArgumentCaptor<OutboundResponse> captor = ArgumentCaptor.forClass(OutboundResponse.class);
        verify(replyPublisher).publish(captor.capture());

        OutboundResponse response = captor.getValue();
        assertThat(response.embeds().get(0).get("title").toString()).contains("não configurada");
    }

    @Test
    void shouldConfigureUserLocationSuccessfully() {
        ConfigureWeatherCommand command = new ConfigureWeatherCommand(
                "c4", "g1", "ch1", "u1", "tok4", null, "Curitiba", "PR", "Brasil", "SLASH", "{}"
        );

        Location curitiba = new Location("Curitiba", "PR", "Brasil", -25.42, -49.27, "America/Sao_Paulo");
        when(weatherClient.resolveLocation("Curitiba", "PR", "Brasil")).thenReturn(Optional.of(curitiba));

        service.configure(command);

        verify(userWeatherConfigPort).save(any(UserWeatherConfig.class));
        ArgumentCaptor<OutboundResponse> captor = ArgumentCaptor.forClass(OutboundResponse.class);
        verify(replyPublisher).publish(captor.capture());

        OutboundResponse response = captor.getValue();
        assertThat(response.responseType()).isEqualTo("EPHEMERAL_REPLY");
        assertThat(response.embeds().get(0).get("title").toString()).contains("sucesso");
    }

    @Test
    void shouldNavigateNextDay() {
        WeatherSession session = new WeatherSession(
                "sess1", "g1", "ch1", "u1", "msg1", sampleLocation, 0, sampleDaily, sampleCurrent, Instant.now().plusSeconds(3600)
        );
        WeatherNavigationCommand command = new WeatherNavigationCommand(
                "c5", "g1", "ch1", "u1", "tok5", "msg1", "sess1", "next", "{}"
        );

        when(weatherSessionPort.findById("sess1")).thenReturn(Optional.of(session));

        service.navigate(command);

        ArgumentCaptor<WeatherSession> sessionCaptor = ArgumentCaptor.forClass(WeatherSession.class);
        verify(weatherSessionPort).save(sessionCaptor.capture());
        assertThat(sessionCaptor.getValue().currentDayIndex()).isEqualTo(1);

        ArgumentCaptor<OutboundResponse> responseCaptor = ArgumentCaptor.forClass(OutboundResponse.class);
        verify(replyPublisher).publish(responseCaptor.capture());
        assertThat(responseCaptor.getValue().responseType()).isEqualTo("UPDATE_MESSAGE");
    }

    @Test
    void shouldHandleExpiredSessionOnNavigation() {
        WeatherNavigationCommand command = new WeatherNavigationCommand(
                "c6", "g1", "ch1", "u1", "tok6", "msg1", "expired-sess", "next", "{}"
        );

        when(weatherSessionPort.findById("expired-sess")).thenReturn(Optional.empty());

        service.navigate(command);

        ArgumentCaptor<OutboundResponse> captor = ArgumentCaptor.forClass(OutboundResponse.class);
        verify(replyPublisher).publish(captor.capture());
        assertThat(captor.getValue().embeds().get(0).get("description").toString()).contains("expirou");
    }

    @Test
    void shouldRejectNavigationWhenUserIsNotOriginalRequester() {
        WeatherSession session = new WeatherSession(
                "sess1", "g1", "ch1", "uOriginal", "msg1", sampleLocation, 0, sampleDaily, sampleCurrent, Instant.now().plusSeconds(3600)
        );
        WeatherNavigationCommand command = new WeatherNavigationCommand(
                "c7", "g1", "ch1", "uDifferent", "tok7", "msg1", "sess1", "next", "{}"
        );

        when(weatherSessionPort.findById("sess1")).thenReturn(Optional.of(session));

        service.navigate(command);

        ArgumentCaptor<OutboundResponse> captor = ArgumentCaptor.forClass(OutboundResponse.class);
        verify(replyPublisher).publish(captor.capture());
        OutboundResponse response = captor.getValue();
        assertThat(response.responseType()).isEqualTo("EPHEMERAL_REPLY");
        assertThat(response.content()).contains("Apenas quem executou o comando pode interagir");
    }
}
