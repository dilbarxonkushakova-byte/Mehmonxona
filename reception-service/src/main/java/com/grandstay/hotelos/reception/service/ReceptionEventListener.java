package com.grandstay.hotelos.reception.service;

import com.grandstay.hotelos.common.events.HotelEvents.*;
import com.grandstay.hotelos.reception.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import static com.grandstay.hotelos.reception.config.RabbitConfig.QUEUE;

/**
 * Reception's reaction to events from the rest of the hotel.
 *
 * NOTE: Multiple message types arrive on the SAME queue, so we must use the
 * class-level @RabbitListener + per-type @RabbitHandler dispatch pattern.
 * Putting @RabbitListener on each method would create multiple listeners on
 * the same queue and Spring AMQP would fail to deserialise any message whose
 * type did not match a given method's parameter.
 */
@Component
@RabbitListener(queues = QUEUE)
public class ReceptionEventListener {
    private static final Logger log = LoggerFactory.getLogger(ReceptionEventListener.class);
    private final RoomRepository rooms;
    private final BookingRepository bookings;

    public ReceptionEventListener(RoomRepository rooms, BookingRepository bookings) {
        this.rooms = rooms;
        this.bookings = bookings;
    }

    @RabbitHandler
    @Transactional
    public void onRoomCleaned(RoomCleaned ev) {
        rooms.findById(ev.roomNumber()).ifPresent(r -> {
            r.markReady();
            rooms.save(r);
            log.info("Room {} marked READY after cleaning by {}", ev.roomNumber(), ev.cleanedBy());
        });
    }

    @RabbitHandler
    @Transactional
    public void onOrderCompleted(OrderCompleted ev) {
        bookings.findFirstByRoomNumberAndClosedFalse(ev.roomNumber()).ifPresent(b -> {
            b.addCharge(ev.price());
            bookings.save(b);
            log.info("Charged {} to booking {} (room service order {})",
                    ev.price(), b.getId(), ev.orderId());
        });
    }

    @RabbitHandler
    public void onMaintenanceResolved(MaintenanceResolved ev) {
        log.info("Maintenance ticket {} on room {} resolved by {}",
                ev.ticketId(), ev.roomNumber(), ev.technician());
    }

    /** Catch-all so messages we don't model don't end up dead-lettered. */
    @RabbitHandler(isDefault = true)
    public void onOther(Object payload) {
        log.debug("Ignoring event of type {}", payload == null ? "null" : payload.getClass());
    }
}
