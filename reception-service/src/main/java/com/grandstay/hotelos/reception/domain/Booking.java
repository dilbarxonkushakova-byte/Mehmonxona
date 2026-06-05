package com.grandstay.hotelos.reception.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    private String id = UUID.randomUUID().toString();

    private int roomNumber;
    private String guestId;
    private Instant checkIn;
    private Instant checkOut;
    private double roomCharges;        // nightly * nights, frozen at booking
    private double extraCharges;       // accumulated room-service / maintenance fees
    private boolean closed;

    protected Booking() {}

    public Booking(int roomNumber, String guestId, Instant in, Instant out, double nightly) {
        this.roomNumber = roomNumber;
        this.guestId = guestId;
        this.checkIn = in;
        this.checkOut = out;
        long nights = Math.max(1, java.time.Duration.between(in, out).toDays());
        this.roomCharges = nightly * nights;
    }

    public String  getId()           { return id; }
    public int     getRoomNumber()   { return roomNumber; }
    public String  getGuestId()      { return guestId; }
    public Instant getCheckIn()      { return checkIn; }
    public Instant getCheckOut()     { return checkOut; }
    public double  getRoomCharges()  { return roomCharges; }
    public double  getExtraCharges() { return extraCharges; }
    public boolean isClosed()        { return closed; }
    public double  total()           { return roomCharges + extraCharges; }

    public void addCharge(double amount) {
        if (amount < 0) throw new IllegalArgumentException("Charge cannot be negative");
        this.extraCharges += amount;
    }
    public void close() { this.closed = true; }
}
