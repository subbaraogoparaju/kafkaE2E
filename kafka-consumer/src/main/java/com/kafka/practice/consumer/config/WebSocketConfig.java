package com.kafka.practice.consumer.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Configures the STOMP-over-WebSocket message broker used to push live order
 * events to browser clients (e.g. the React UI).
 *
 * <p>Broker destinations:
 * <ul>
 *   <li>{@code /topic/**} — simple in-memory pub/sub broker; clients subscribe
 *       to {@code /topic/orders} to receive real-time {@link com.kafka.practice.consumer.model.OrderEvent} updates.</li>
 * </ul>
 *
 * <p>Application destination prefix {@code /app} is reserved for messages
 * routed to {@code @MessageMapping} controller methods (none currently defined).
 *
 * <p>The SockJS fallback allows clients that cannot use native WebSockets to
 * connect via HTTP long-polling or other transports.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * Sets up the in-memory simple broker on the {@code /topic} prefix and
     * defines {@code /app} as the prefix for application-level message handlers.
     *
     * @param config the broker registry to configure
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    /**
     * Registers the STOMP WebSocket endpoint at {@code /ws} with SockJS fallback.
     *
     * <p>All origin patterns are permitted ({@code *}) to support local
     * development from any host; tighten this in production.
     *
     * @param registry the endpoint registry to configure
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}