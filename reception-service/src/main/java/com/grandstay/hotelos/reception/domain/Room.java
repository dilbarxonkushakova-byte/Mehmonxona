package com.grandstay.hotelos.reception.domain;

import jakarta.persistence.*;

/**
 * A physical hotel room. Status is the single source of truth that the
 * allocation algorithm reads from (see RoomAllocator).
 *
 * Encapsulation (OOP pillar - Task 2.3): the `status` field is package-private
 * and can only be transitioned through the explicit setters below, which
 * enforce the legal state machine:
 *
 *   READY -> OCCUPIED   (on booking)
 *   OCCUPIED -> DIRTY   (on check-out)
 *   DIRTY -> READY      (on cleaning confirmation)
 *   *     -> OUT_OF_SERVICE (on critical maintenance)
 */
@Entity
@Table(name = "rooms")
public class Room {

    public enum Status { READY, OCCUPIED, DIRTY, OUT_OF_SERVICE }

    @Id
    private Integer number;          // 101..610

    private int floor;
    private int beds;                // 1 = single, 2 = double, etc.
    private double nightlyRate;

    @Enumerated(EnumType.STRING)
    private Status status = Status.READY;

    protected Room() {} // JPA

    public Room(int number, int floor, int beds, double nightlyRate) {
        this.number = number;
        this.floor = floor;
        this.beds = beds;
        this.nightlyRate = nightlyRate;
    }

    public Integer getNumber()     { return number; }
    public int     getFloor()      { return floor; }
    public int     getBeds()       { return beds; }
    public double  getNightlyRate(){ return nightlyRate; }
    public Status  getStatus()     { return status; }

    public void markOccupied() {
        if (status != Status.READY)
            throw new IllegalStateException("Room " + number + " not READY (current=" + status + ")");
        status = Status.OCCUPIED;
    }
    public void markDirty()         { status = Status.DIRTY; }
    public void markReady()         { status = Status.READY; }
    public void markOutOfService()  { status = Status.OUT_OF_SERVICE; }
}
