package dev.gdinizds.discordweatherbot.domain;

import dev.gdinizds.discordweatherbot.domain.model.Location;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LocationTest {

    @Test
    void shouldFormatDisplayNameWithAllFields() {
        Location loc = new Location("Rio de Janeiro", "RJ", "Brasil", -22.9, -43.1, "America/Sao_Paulo");
        assertThat(loc.displayName()).isEqualTo("Rio de Janeiro, RJ - Brasil");
    }

    @Test
    void shouldFormatDisplayNameWithCityAndCountryOnly() {
        Location loc = new Location("Paris", null, "França", 48.8, 2.3, "Europe/Paris");
        assertThat(loc.displayName()).isEqualTo("Paris - França");
    }

    @Test
    void shouldFormatDisplayNameWithCityOnly() {
        Location loc = new Location("Tokyo", "", "", 35.6, 139.6, "Asia/Tokyo");
        assertThat(loc.displayName()).isEqualTo("Tokyo");
    }
}
