package dev.gdinizds.discordweatherbot.application.port.out;

import dev.gdinizds.discordweatherbot.domain.model.OutboundResponse;

public interface ReplyPublisherPort {
    void publish(OutboundResponse response);
}
