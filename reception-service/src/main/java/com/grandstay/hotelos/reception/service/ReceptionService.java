package com.grandstay.hotelos.reception.service;

import com.grandstay.hotelos.common.events.HotelEvents.*;
import com.grandstay.hotelos.common.events.RoutingKeys;
import com.grandstay.hotelos.reception.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class ReceptionService {

    private static final Logger log = LoggerFactory.getLogger(ReceptionService.class);

    private final RoomRepository rooms;
    private final BookingRepository bookings;
    private final RabbitTemplate rabbit;

    public ReceptionService(RoomRepository rooms,
                            BookingRepository bookings,
                            RabbitTemplate rabbit) {
        this.rooms = rooms;
        this.bookings = bookings;
        this.rabbit = rabbit;
    }

    // =========================
    // BOOK ROOM
    // =========================
    @Transactional
    public Booking book(String guestId,
                        int requestedBeds,
                        Instant in,
                        Instant out) {

        if (guestId == null || guestId.isBlank()) {
            throw new IllegalArgumentException("guestId is required");
        }

        if (in == null || out == null || out.isBefore(in)) {
            throw new IllegalArgumentException("Invalid check-in/check-out dates");
        }

        List<Room> all = rooms.findAll();

        Room room = RoomAllocator.allocate(all, requestedBeds)
                .orElseThrow(() -> new IllegalStateException("No suitable room available"));

        room.markOccupied();
        rooms.save(room);

        Booking booking = new Booking(
                room.getNumber(),
                guestId,
                in,
                out,
                room.getNightlyRate()
        );

        bookings.save(booking);

        rabbit.convertAndSend(
                RoutingKeys.EXCHANGE,
                RoutingKeys.ROOM_BOOKED,
                new RoomBooked(
                        booking.getId(),
                        room.getNumber(),
                        guestId,
                        in,
                        out,
                        Instant.now()
                )
        );

        log.info("Booked room {} for guest {} (bookingId={})",
                room.getNumber(), guestId, booking.getId());

        return booking;
    }

    // =========================
    // CHECKOUT
    // =========================
    @Transactional
    public double checkOut(int roomNumber) {

        Booking b = bookings.findFirstByRoomNumberAndClosedFalse(roomNumber)
                .orElseThrow(() -> new IllegalStateException(
                        "No open booking for room " + roomNumber));

        b.close();
        bookings.save(b);

        Room room = rooms.findById(roomNumber)
                .orElseThrow(() -> new IllegalStateException(
                        "Room not found: " + roomNumber));

        room.markDirty();
        rooms.save(room);

        rabbit.convertAndSend(
                RoutingKeys.EXCHANGE,
                RoutingKeys.ROOM_VACATED,
                new RoomVacated(roomNumber, b.getId(), Instant.now())
        );

        double total = b.total();

        log.info("Checked out room {} — total {}", roomNumber, total);

        return total;
    }

    // =========================
    // ROOMS LIST
    // =========================
    @Transactional(readOnly = true)
    public List<Room> listRooms() {
        return rooms.findAll();
    }
}