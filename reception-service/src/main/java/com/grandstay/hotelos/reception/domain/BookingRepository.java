package com.grandstay.hotelos.reception.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, String> {
    Optional<Booking> findFirstByRoomNumberAndClosedFalse(int roomNumber);
}
