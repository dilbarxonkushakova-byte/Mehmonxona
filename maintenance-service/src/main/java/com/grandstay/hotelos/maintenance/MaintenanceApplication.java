package com.grandstay.hotelos.maintenance;

import com.grandstay.hotelos.common.events.HotelEvents.*;
import com.grandstay.hotelos.common.events.RoutingKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.PriorityBlockingQueue;

@SpringBootApplication
public class MaintenanceApplication {
    public static void main(String[] args) { SpringApplication.run(MaintenanceApplication.class, args); }
}

@Configuration
class RabbitConfig {
    @Bean TopicExchange ex() { return new TopicExchange(RoutingKeys.EXCHANGE, true, false); }
    @Bean MessageConverter mc() { return new Jackson2JsonMessageConverter(); }
    @Bean RabbitTemplate rt(ConnectionFactory cf, MessageConverter mc) {
        RabbitTemplate t = new RabbitTemplate(cf); t.setMessageConverter(mc); return t;
    }
}

/**
 * Priority queue for maintenance tickets (Task 1.2 algorithm #2).
 *
 * Urgency scale (lower number = MORE urgent so PriorityBlockingQueue naturally
 * pops it first):
 *   1 — safety hazard (gas, fire, flood)        — must be picked up immediately
 *   2 — guest unable to use the room (no power, broken lock)
 *   3 — comfort issue (AC noisy, TV remote dead)
 *   4 — cosmetic (chipped paint)
 *
 * Tie-break by report time (older tickets first) so that two equally urgent
 * tickets are handled FIFO inside their priority band.
 */
@Service
class MaintenanceQueue {
    private static final Logger log = LoggerFactory.getLogger(MaintenanceQueue.class);

    public record Ticket(String id, int roomNumber, String issue, int urgency, Instant reportedAt) {
        public Ticket(int roomNumber, String issue, int urgency) {
            this(UUID.randomUUID().toString(), roomNumber, issue, urgency, Instant.now());
        }
    }

    private static final Comparator<Ticket> ORDER =
            Comparator.comparingInt(Ticket::urgency).thenComparing(Ticket::reportedAt);

    private final PriorityBlockingQueue<Ticket> open = new PriorityBlockingQueue<>(16, ORDER);
    private final Map<String, Ticket> all = new java.util.concurrent.ConcurrentHashMap<>();
    private final RabbitTemplate rabbit;

    MaintenanceQueue(RabbitTemplate rabbit) { this.rabbit = rabbit; }

    Ticket report(int roomNumber, String issue, int urgency) {
        if (urgency < 1 || urgency > 4) throw new IllegalArgumentException("urgency must be 1..4");
        Ticket t = new Ticket(roomNumber, issue, urgency);
        open.put(t);
        all.put(t.id(), t);
        rabbit.convertAndSend(RoutingKeys.EXCHANGE, RoutingKeys.MAINTENANCE_REPORTED,
                new MaintenanceReported(t.id(), roomNumber, issue, urgency, Instant.now()));
        log.info("Ticket {} reported: room {} urgency {} — {}", t.id(), roomNumber, urgency, issue);
        return t;
    }

    Optional<Ticket> peek() { return Optional.ofNullable(open.peek()); }
    List<Ticket> snapshot() { return open.stream().sorted(ORDER).toList(); }

    void resolve(String id, String technician) {
        Ticket t = all.get(id);
        if (t == null) throw new IllegalArgumentException("Unknown ticket: " + id);
        open.remove(t);
        rabbit.convertAndSend(RoutingKeys.EXCHANGE, RoutingKeys.MAINTENANCE_RESOLVED,
                new MaintenanceResolved(id, t.roomNumber(), technician, Instant.now()));
        log.info("Ticket {} resolved by {}", id, technician);
    }
}

@org.springframework.web.bind.annotation.CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/maintenance")
class MaintenanceController {
    private final MaintenanceQueue q;
    MaintenanceController(MaintenanceQueue q) { this.q = q; }

    public record ReportRequest(int roomNumber, String issue, int urgency) {}

    @GetMapping("/tickets") public List<MaintenanceQueue.Ticket> all() { return q.snapshot(); }
    @PostMapping("/tickets") public MaintenanceQueue.Ticket report(@RequestBody ReportRequest r) {
        return q.report(r.roomNumber(), r.issue(), r.urgency());
    }
    @PostMapping("/tickets/{id}/resolve")
    public Map<String, String> resolve(@PathVariable String id,
                                       @RequestParam(defaultValue = "tech-1") String technician) {
        q.resolve(id, technician);
        return Map.of("status", "resolved", "id", id, "technician", technician);
    }
}
