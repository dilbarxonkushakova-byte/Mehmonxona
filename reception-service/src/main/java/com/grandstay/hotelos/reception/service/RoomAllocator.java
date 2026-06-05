package com.grandstay.hotelos.reception.service;

import com.grandstay.hotelos.reception.domain.Room;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Pure (no side effects, no Spring) "best-fit" room allocator.
 *
 * Algorithm (Task 1.1):
 *   1. Filter rooms to those with status = READY and beds >= requested.
 *   2. From that set, pick the one with the smallest bed count
 *      (so a guest asking for a single does not consume a suite).
 *   3. Tie-break by lower floor (less elevator load).
 *   4. Tie-break by lower room number (deterministic).
 *
 * Complexity: O(n) where n = number of rooms (~120). A linear scan is
 * intentional — the dataset is tiny and the cost of maintaining a
 * sorted index would dominate. Justification is repeated in the report.
 *
 * This class is deliberately a plain object so it can be unit-tested
 * without an application context (see RoomAllocatorTest).
 */
public final class RoomAllocator {

    private RoomAllocator() {}

    public static Optional<Room> allocate(List<Room> candidates, int requestedBeds) {
        if (requestedBeds < 1) throw new IllegalArgumentException("requestedBeds must be >= 1");
        return candidates.stream()
                .filter(r -> r.getStatus() == Room.Status.READY)
                .filter(r -> r.getBeds() >= requestedBeds)
                .min(Comparator
                        .comparingInt(Room::getBeds)
                        .thenComparingInt(Room::getFloor)
                        .thenComparingInt(Room::getNumber));
    }
}
