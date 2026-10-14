package dev.gdinizds.discordweatherbot;

import dev.gdinizds.discordweatherbot.config.NativeRuntimeHints;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ImportRuntimeHints;

@SpringBootApplication
@ImportRuntimeHints(NativeRuntimeHints.class)
public class DiscordWeatherBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(DiscordWeatherBotApplication.class, args);
    }
}
