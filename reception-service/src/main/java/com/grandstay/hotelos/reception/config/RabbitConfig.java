package com.grandstay.hotelos.reception.config;

import com.grandstay.hotelos.common.events.RoutingKeys;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topic exchange + per-service queue. Reception listens for:
 *   - room.cleaned   (so it can mark a room as Ready)
 *   - order.completed (so it can append charges to the bill)
 *   - maintenance.resolved (audit / billing context)
 */
@Configuration
public class RabbitConfig {

    public static final String QUEUE = "reception.queue";

    @Bean TopicExchange exchange() { return new TopicExchange(RoutingKeys.EXCHANGE, true, false); }
    @Bean Queue receptionQueue()   { return new Queue(QUEUE, true); }

    @Bean Binding cleanedBinding(Queue receptionQueue, TopicExchange exchange) {
        return BindingBuilder.bind(receptionQueue).to(exchange).with(RoutingKeys.ROOM_CLEANED);
    }
    @Bean Binding orderCompletedBinding(Queue receptionQueue, TopicExchange exchange) {
        return BindingBuilder.bind(receptionQueue).to(exchange).with(RoutingKeys.ORDER_COMPLETED);
    }
    @Bean Binding maintenanceResolvedBinding(Queue receptionQueue, TopicExchange exchange) {
        return BindingBuilder.bind(receptionQueue).to(exchange).with(RoutingKeys.MAINTENANCE_RESOLVED);
    }

    @Bean MessageConverter jsonConverter() { return new Jackson2JsonMessageConverter(); }

    @Bean RabbitTemplate rabbitTemplate(ConnectionFactory cf, MessageConverter mc) {
        RabbitTemplate t = new RabbitTemplate(cf);
        t.setMessageConverter(mc);
        t.setExchange(RoutingKeys.EXCHANGE);
        return t;
    }
}
