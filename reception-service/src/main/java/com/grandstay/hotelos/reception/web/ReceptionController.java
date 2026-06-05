package com.grandstay.hotelos.reception.web;

import com.grandstay.hotelos.reception.domain.Booking;
import com.grandstay.hotelos.reception.domain.Room;
import com.grandstay.hotelos.reception.service.ReceptionService;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reception")
public class ReceptionController {

    private final ReceptionService service;

    public ReceptionController(ReceptionService service) {
        this.service = service;
    }

    // =========================
    // DTO (Frontend bilan mos)
    // =========================
    public record BookingRequest(
            @NotBlank String guestId,
            @Min(1) int beds,
            String checkIn,
            String checkOut
    ) {}

    // =========================
    // BOOK ROOM
    // =========================
    @PostMapping("/bookings")
    public ResponseEntity<?> book(@RequestBody BookingRequest req) {

        Booking b = service.book(
                req.guestId(),
                req.beds(),
                Instant.parse(req.checkIn()),
                Instant.parse(req.checkOut())
        );

        return ResponseEntity.ok(Map.of(
                "bookingId", b.getId(),
                "roomNumber", b.getRoomNumber(),
                "roomCharges", b.getRoomCharges()
        ));
    }

    // =========================
    // CHECKOUT
    // =========================
    @PostMapping("/checkout/{roomNumber}")
    public Map<String, Object> checkout(@PathVariable int roomNumber) {
        double total = service.checkOut(roomNumber);

        return Map.of(
                "roomNumber", roomNumber,
                "total", total
        );
    }

    // =========================
    // ROOMS LIST
    // =========================
    @GetMapping("/rooms")
    public List<Room> rooms() {
        return service.listRooms();
    }
}