package com.grandstay.hotelos.roomservice;

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
import java.util.concurrent.ConcurrentHashMap;

@SpringBootApplication
public class RoomServiceApplication {
    public static void main(String[] args) { SpringApplication.run(RoomServiceApplication.class, args); }
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
 * Simple menu + in-memory order ledger. A real kitchen would persist this,
 * but for the prototype an in-memory map proves the message flow end-to-end.
 *
 * Demonstrates Single Responsibility (Task 2.2 - clean code): this class only
 * tracks orders and prices; transport (REST) and messaging (AMQP) live
 * elsewhere.
 */
@Service
class RoomServiceCatalog {
    private static final Logger log = LoggerFactory.getLogger(RoomServiceCatalog.class);

    private final Map<String, Double> menu = Map.of(
            "Club Sandwich", 14.50,
            "Caesar Salad",  12.00,
            "Sparkling Water", 4.00,
            "Espresso", 3.50);

    private final Map<String, Order> orders = new ConcurrentHashMap<>();
    private final RabbitTemplate rabbit;

    RoomServiceCatalog(RabbitTemplate rabbit) { this.rabbit = rabbit; }

    record Order(String id, int roomNumber, String item, double price, Instant placedAt, boolean delivered) {}

    Map<String, Double> menu() { return menu; }

    Order place(int roomNumber, String item) {
        Double price = menu.get(item);
        if (price == null) throw new IllegalArgumentException("Item not on menu: " + item);

        String id = UUID.randomUUID().toString();
        Order o = new Order(id, roomNumber, item, price, Instant.now(), false);
        orders.put(id, o);

        rabbit.convertAndSend(RoutingKeys.EXCHANGE, RoutingKeys.ORDER_PLACED,
                new OrderPlaced(id, roomNumber, item, price, Instant.now()));
        log.info("Order {} placed: room {}, item {}, price {}", id, roomNumber, item, price);
        return o;
    }

    Order deliver(String id) {
        Order o = orders.get(id);
        if (o == null) throw new IllegalArgumentException("Unknown order: " + id);
        Order delivered = new Order(o.id, o.roomNumber, o.item, o.price, o.placedAt, true);
        orders.put(id, delivered);

        rabbit.convertAndSend(RoutingKeys.EXCHANGE, RoutingKeys.ORDER_COMPLETED,
                new OrderCompleted(id, o.roomNumber, o.price, Instant.now()));
        log.info("Order {} delivered — published order.completed", id);
        return delivered;
    }

    List<Order> all() { return new ArrayList<>(orders.values()); }
}

@org.springframework.web.bind.annotation.CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/roomservice")
class RoomServiceController {
    private final RoomServiceCatalog catalog;
    RoomServiceController(RoomServiceCatalog catalog) { this.catalog = catalog; }

    public record OrderRequest(int roomNumber, String item) {}

    @GetMapping("/menu")     public Map<String, Double> menu()       { return catalog.menu(); }
    @GetMapping("/orders")   public List<RoomServiceCatalog.Order> orders() { return catalog.all(); }
    @PostMapping("/orders")  public RoomServiceCatalog.Order place(@RequestBody OrderRequest r) {
        return catalog.place(r.roomNumber(), r.item());
    }
    @PostMapping("/orders/{id}/deliver")
    public RoomServiceCatalog.Order deliver(@PathVariable String id) { return catalog.deliver(id); }
}

// USHBU KOD RoomService Ctonrolleri hisoblandi