package com.grandstay.hotelos.common.events;

import java.time.Instant;

/**
 * Central registry of all domain events that cross service boundaries through
 * RabbitMQ. Using sealed records guarantees that the event family is closed
 * and that every consumer can pattern-match exhaustively.
 *
 * Exchange topology (see RabbitMqTopology in each service):
 *   exchange: hotelos.events    (topic)
 *   routing keys:
 *     room.booked          -> Housekeeping (none yet), Dashboard
 *     room.vacated         -> Housekeeping, Dashboard
 *     room.cleaned         -> Reception, Dashboard
 *     order.placed         -> RoomService, Dashboard
 *     order.completed      -> Reception (billing), Dashboard
 *     maintenance.reported -> Maintenance, Dashboard
 *     maintenance.resolved -> Reception, Dashboard
 *
 * Security note (LO3 task 3.2): events that fan out to the dashboard MUST NOT
 * contain raw guest PII (passport, full card number). The DTOs below carry
 * only the data needed by downstream services.
 */
public final class HotelEvents {

    private HotelEvents() {}

    public record RoomBooked(String bookingId, int roomNumber, String guestId,
                             Instant checkIn, Instant checkOut, Instant occurredAt) {}

    public record RoomVacated(int roomNumber, String bookingId, Instant occurredAt) {}

    public record RoomCleaned(int roomNumber, String cleanedBy, Instant occurredAt) {}

    public record OrderPlaced(String orderId, int roomNumber, String item,
                              double price, Instant occurredAt) {}

    public record OrderCompleted(String orderId, int roomNumber, double price,
                                 Instant occurredAt) {}

    public record MaintenanceReported(String ticketId, int roomNumber, String issue,
                                      int urgency, Instant occurredAt) {}

    public record MaintenanceResolved(String ticketId, int roomNumber,
                                      String technician, Instant occurredAt) {}
}
