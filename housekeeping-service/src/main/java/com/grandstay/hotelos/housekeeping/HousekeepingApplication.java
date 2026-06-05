package com.grandstay.hotelos.housekeeping;

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
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

@SpringBootApplication
public class HousekeepingApplication {
    public static void main(String[] args) { SpringApplication.run(HousekeepingApplication.class, args); }
}

/** Topology — listens for room.vacated. */
@Configuration
class RabbitConfig {
    static final String QUEUE = "housekeeping.queue";
    @Bean TopicExchange ex()       { return new TopicExchange(RoutingKeys.EXCHANGE, true, false); }
    @Bean org.springframework.amqp.core.Queue q() { return new org.springframework.amqp.core.Queue(QUEUE, true); }
    @Bean Binding vacated(org.springframework.amqp.core.Queue q, TopicExchange ex) {
        return BindingBuilder.bind(q).to(ex).with(RoutingKeys.ROOM_VACATED);
    }
    @Bean MessageConverter mc()    { return new Jackson2JsonMessageConverter(); }
    @Bean RabbitTemplate rt(ConnectionFactory cf, MessageConverter mc) {
        RabbitTemplate t = new RabbitTemplate(cf); t.setMessageConverter(mc); return t;
    }
}

/**
 * FIFO cleaning queue (Task 1.2 algorithm #1). A ConcurrentLinkedDeque keeps
 * the order rooms were vacated in. Cleaners pull from the head via the REST
 * endpoint and confirm completion, which publishes room.cleaned.
 */
@Service
class CleaningQueue {
    private static final Logger log = LoggerFactory.getLogger(CleaningQueue.class);
    private final Deque<Integer> queue = new ConcurrentLinkedDeque<>();
    private final RabbitTemplate rabbit;

    CleaningQueue(RabbitTemplate rabbit) { this.rabbit = rabbit; }

    void enqueue(int room) {
        if (!queue.contains(room)) {
            queue.addLast(room);
            log.info("Room {} added to cleaning queue (size={})", room, queue.size());
        }
    }

    public Optional<Integer> nextRoom() { return Optional.ofNullable(queue.peekFirst()); }
    public List<Integer> snapshot()     { return new ArrayList<>(queue); }

    public void confirmCleaned(int room, String cleaner) {
        queue.remove(room);
        rabbit.convertAndSend(RoutingKeys.EXCHANGE, RoutingKeys.ROOM_CLEANED,
                new RoomCleaned(room, cleaner, Instant.now()));
        log.info("Room {} cleaned by {} — published room.cleaned", room, cleaner);
    }
}

@Component
class VacatedListener {
    private final CleaningQueue cleaning;
    VacatedListener(CleaningQueue cleaning) { this.cleaning = cleaning; }

    @RabbitListener(queues = RabbitConfig.QUEUE)
    public void onVacated(RoomVacated ev) { cleaning.enqueue(ev.roomNumber()); }
}

@org.springframework.web.bind.annotation.CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/housekeeping")
class HousekeepingController {
    private final CleaningQueue cleaning;
    HousekeepingController(CleaningQueue cleaning) { this.cleaning = cleaning; }

    @GetMapping("/queue") public List<Integer> queue() { return cleaning.snapshot(); }

    @PostMapping("/clean/{room}")
    public Map<String, Object> markClean(@PathVariable int room,
                                         @RequestParam(defaultValue = "anonymous") String cleaner) {
        cleaning.confirmCleaned(room, cleaner);
        return Map.of("room", room, "cleanedBy", cleaner);
    }
}
