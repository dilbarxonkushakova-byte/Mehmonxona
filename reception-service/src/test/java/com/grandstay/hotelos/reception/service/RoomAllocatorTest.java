package com.grandstay.hotelos.reception.service;

import com.grandstay.hotelos.reception.domain.Room;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class RoomAllocatorTest {

    private Room ready(int n, int floor, int beds) {
        Room r = new Room(n, floor, beds, 100);
        return r;
    }
    private Room dirty(int n, int floor, int beds) {
        Room r = ready(n, floor, beds);
        r.markDirty();
        return r;
    }

    @Test
    void picksSmallestSufficientRoom() {
        Optional<Room> chosen = RoomAllocator.allocate(List.of(
                ready(301, 3, 3),
                ready(101, 1, 2),
                ready(201, 2, 1)), 1);
        assertTrue(chosen.isPresent());
        assertEquals(201, chosen.get().getNumber()); // beds=1 is the smallest sufficient
    }

    @Test
    void respectsBedRequirement() {
        Optional<Room> chosen = RoomAllocator.allocate(List.of(
                ready(101, 1, 1),
                ready(102, 1, 2)), 2);
        assertEquals(102, chosen.get().getNumber());
    }

    @Test
    void skipsDirtyRooms() {
        Optional<Room> chosen = RoomAllocator.allocate(List.of(
                dirty(101, 1, 2),
                ready(201, 2, 2)), 2);
        assertEquals(201, chosen.get().getNumber());
    }

    @Test
    void returnsEmptyWhenNothingFits() {
        assertTrue(RoomAllocator.allocate(List.of(ready(101, 1, 1)), 3).isEmpty());
    }

    @Test
    void rejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class,
                () -> RoomAllocator.allocate(List.of(), 0));
    }
}
