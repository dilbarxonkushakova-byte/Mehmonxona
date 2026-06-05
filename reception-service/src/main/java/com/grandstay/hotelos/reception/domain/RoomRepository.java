package com.grandstay.hotelos.reception.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RoomRepository extends JpaRepository<Room, Integer> {
    List<Room> findByStatus(Room.Status status);
}
