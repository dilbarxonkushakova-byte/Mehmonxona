package com.grandstay.hotelos.dashboard;

import com.grandstay.hotelos.common.events.HotelEvents.*;
import com.grandstay.hotelos.common.events.RoutingKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.socket.config.annotation.*;

import java.time.Instant;
import java.util.Map;

@SpringBootApplication
public class DashboardApplication {
    public static void main(String[] args) { SpringApplication.run(DashboardApplication.class, args); }
}


/** STOMP over WebSocket. Browser subscribes to /topic/events. */
@Configuration
@EnableWebSocketMessageBroker
class WsConfig implements WebSocketMessageBrokerConfigurer {
    @Override public void configureMessageBroker(MessageBrokerRegistry r) {
        r.enableSimpleBroker("/topic");
        r.setApplicationDestinationPrefixes("/app");
    }
    @Override public void registerStompEndpoints(StompEndpointRegistry r) {
        r.addEndpoint("/ws").setAllowedOriginPatterns("*").withSockJS();
    }
}

/** Listens to the full firehose of hotel events and forwards a SANITISED
 *  projection to the WebSocket clients. Per Task 3.2 we do NOT relay raw
 *  guest IDs, prices or PII — only what an operator needs to see on screen. */
@Configuration
class DashboardRabbit {
    static final String QUEUE = "dashboard.queue";
    @Bean TopicExchange ex() { return new TopicExchange(RoutingKeys.EXCHANGE, true, false); }
    @Bean Queue q()          { return new Queue(QUEUE, true); }
    @Bean Binding bindAll(Queue q, TopicExchange ex) {
        // single wildcard binding catches every hotel event
        return BindingBuilder.bind(q).to(ex).with("#");
    }
    @Bean MessageConverter mc() { return new Jackson2JsonMessageConverter(); }
    @Bean RabbitTemplate rt(ConnectionFactory cf, MessageConverter mc) {
        RabbitTemplate t = new RabbitTemplate(cf); t.setMessageConverter(mc); return t;
    }
}

@Component
class DashboardForwarder {
    private static final Logger log = LoggerFactory.getLogger(DashboardForwarder.class);
    private final SimpMessagingTemplate ws;

    DashboardForwarder(SimpMessagingTemplate ws) { this.ws = ws; }

    @RabbitListener(queues = DashboardRabbit.QUEUE)
    public void onEvent(Object payload) {
        Map<String, Object> safe = sanitise(payload);
        if (safe == null) return;
        log.debug("Forwarding to dashboard: {}", safe);
        ws.convertAndSend("/topic/events", safe);
    }

    /** Map domain events to a screen-safe shape. Guest IDs are NOT included. */
    private Map<String, Object> sanitise(Object e) {
        return switch (e) {
            case RoomBooked b ->
                    Map.of("type", "room.booked",   "room", b.roomNumber(),  "at", b.occurredAt().toString());
            case RoomVacated v ->
                    Map.of("type", "room.vacated",  "room", v.roomNumber(),  "at", v.occurredAt().toString());
            case RoomCleaned c ->
                    Map.of("type", "room.cleaned",  "room", c.roomNumber(),
                           "by",   c.cleanedBy(),   "at",  c.occurredAt().toString());
            case OrderPlaced o ->
                    Map.of("type", "order.placed",  "room", o.roomNumber(),
                           "item", o.item(),        "at",  o.occurredAt().toString());
            case OrderCompleted o ->
                    Map.of("type", "order.completed","room", o.roomNumber(), "at", o.occurredAt().toString());
            case MaintenanceReported m ->
                    Map.of("type", "maintenance.reported","room", m.roomNumber(),
                           "issue", m.issue(), "urgency", m.urgency(), "at", m.occurredAt().toString());
            case MaintenanceResolved m ->
                    Map.of("type", "maintenance.resolved","room", m.roomNumber(),
                           "by", m.technician(), "at", m.occurredAt().toString());
            default -> {
                log.warn("Unknown event type {}", e == null ? "null" : e.getClass());
                yield Map.of("type", "unknown", "at", Instant.now().toString());
            }
        };
    }
}
