package com.vora.reservation.infrastructure.realtime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebSocketConfigTest {

    private final WebSocketConfig config = new WebSocketConfig();

    @Test
    void shouldImplementWebSocketMessageBrokerConfigurer() {
        assertThat(config).isInstanceOf(org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer.class);
    }
}
