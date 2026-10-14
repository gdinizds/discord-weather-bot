package dev.gdinizds.discordweatherbot.adapter.out.kafka;

import dev.gdinizds.discordweatherbot.config.WeatherBotProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class CommandRegistrar implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CommandRegistrar.class);

    static final String SLASH_CLIMA = """
            {
              "prefix": "SLASH",
              "name": "clima",
              "description": "Consulte a previsão do tempo com suporte a navegação diária e favoritos",
              "parameters": [
                { "name": "local", "description": "Cidade ou CEP/Zipcode (opcional se você já configurou seu padrão)", "type": "STRING", "required": false },
                { "name": "estado", "description": "Estado ou UF (opcional)", "type": "STRING", "required": false },
                { "name": "pais", "description": "País (opcional)", "type": "STRING", "required": false }
              ],
              "is_deleted": false
            }
            """;

    static final String SLASH_CLIMA_CONFIG = """
            {
              "prefix": "SLASH",
              "name": "clima-config",
              "description": "Configure sua cidade e localização padrão para consultas rápidas do clima",
              "parameters": [
                { "name": "cidade", "description": "Sua cidade ou CEP padrão", "type": "STRING", "required": true },
                { "name": "estado", "description": "Seu estado ou UF padrão (opcional)", "type": "STRING", "required": false },
                { "name": "pais", "description": "Seu país padrão (opcional)", "type": "STRING", "required": false }
              ],
              "is_deleted": false
            }
            """;

    static final String DOT_CLIMA = """
            {
              "prefix": "DOT",
              "name": "clima",
              "description": "Consulte o clima: .clima ou .clima <cidade> ou .clima config <cidade>",
              "is_deleted": false
            }
            """;

    static final String DOT_CLIMA_CONFIG = """
            {
              "prefix": "DOT",
              "name": "clima-config",
              "description": "Configure sua localização: .clima-config <cidade>, [estado], [pais]",
              "is_deleted": false
            }
            """;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;

    public CommandRegistrar(KafkaTemplate<String, String> kafkaTemplate, WeatherBotProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = properties.topics().gatewayCommands();
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            kafkaTemplate.send(topic, "clima", SLASH_CLIMA);
            kafkaTemplate.send(topic, "clima-config", SLASH_CLIMA_CONFIG);
            kafkaTemplate.send(topic, "clima", DOT_CLIMA);
            kafkaTemplate.send(topic, "clima-config", DOT_CLIMA_CONFIG);
            log.info("Registered Discord commands: /clima, /clima-config, .clima, .clima-config");
        } catch (Exception e) {
            log.error("Failed to register Discord commands: {}", e.getMessage());
        }
    }
}
