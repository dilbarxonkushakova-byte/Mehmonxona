package com.grandstay.hotelos.reception.bootstrap;

import com.grandstay.hotelos.reception.domain.Room;
import com.grandstay.hotelos.reception.domain.RoomRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Seeds 2 floors x 10 rooms = 20 rooms on first boot (per brief: "120 rooms
 * are not required; you may build with 10 rooms on 2 floors"). Room number =
 * floor * 100 + index; bed count alternates 1/2/3 so the allocator has
 * something to choose from.
 */
@Component
public class RoomSeeder implements CommandLineRunner {

    private final RoomRepository repo;
    public RoomSeeder(RoomRepository repo) { this.repo = repo; }

    @Override
    public void run(String... args) {
        if (repo.count() > 0) return;
        for (int floor = 1; floor <= 2; floor++) {
            for (int i = 1; i <= 10; i++) {
                int beds = (i % 3) + 1;            // 2, 3, 1, 2, 3, 1, ...
                double rate = 60.0 + beds * 25.0;  // simple rule
                repo.save(new Room(floor * 100 + i, floor, beds, rate));
            }
        }
    }
}
