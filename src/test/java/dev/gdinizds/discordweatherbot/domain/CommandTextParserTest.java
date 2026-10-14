package dev.gdinizds.discordweatherbot.domain;

import dev.gdinizds.discordweatherbot.domain.service.CommandTextParser;
import dev.gdinizds.discordweatherbot.domain.service.CommandTextParser.ActionType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CommandTextParserTest {

    @Test
    void shouldParseEmptyClimaAsQueryWithoutLocation() {
        var parsed = CommandTextParser.parse("clima", "");
        assertThat(parsed.actionType()).isEqualTo(ActionType.QUERY);
        assertThat(parsed.local()).isNull();
        assertThat(parsed.estado()).isNull();
        assertThat(parsed.pais()).isNull();
    }

    @Test
    void shouldParseSingleCityQuery() {
        var parsed = CommandTextParser.parse("clima", "São Paulo");
        assertThat(parsed.actionType()).isEqualTo(ActionType.QUERY);
        assertThat(parsed.local()).isEqualTo("São Paulo");
        assertThat(parsed.estado()).isNull();
        assertThat(parsed.pais()).isNull();
    }

    @Test
    void shouldParseCommaSeparatedCityStateCountry() {
        var parsed = CommandTextParser.parse("clima", "Curitiba, PR, Brasil");
        assertThat(parsed.actionType()).isEqualTo(ActionType.QUERY);
        assertThat(parsed.local()).isEqualTo("Curitiba");
        assertThat(parsed.estado()).isEqualTo("PR");
        assertThat(parsed.pais()).isEqualTo("Brasil");
    }

    @Test
    void shouldParseConfigPrefixWithKeywords() {
        var parsed = CommandTextParser.parse("clima", "config São Paulo, SP, Brasil");
        assertThat(parsed.actionType()).isEqualTo(ActionType.CONFIGURE);
        assertThat(parsed.local()).isEqualTo("São Paulo");
        assertThat(parsed.estado()).isEqualTo("SP");
        assertThat(parsed.pais()).isEqualTo("Brasil");
    }

    @Test
    void shouldParseConfigureKeyword() {
        var parsed = CommandTextParser.parse("clima", "configure Belo Horizonte");
        assertThat(parsed.actionType()).isEqualTo(ActionType.CONFIGURE);
        assertThat(parsed.local()).isEqualTo("Belo Horizonte");
    }

    @Test
    void shouldParseClimaConfigCommandName() {
        var parsed = CommandTextParser.parse("clima-config", "Florianopolis, SC");
        assertThat(parsed.actionType()).isEqualTo(ActionType.CONFIGURE);
        assertThat(parsed.local()).isEqualTo("Florianopolis");
        assertThat(parsed.estado()).isEqualTo("SC");
    }
}
