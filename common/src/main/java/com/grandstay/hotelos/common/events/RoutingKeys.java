package com.grandstay.hotelos.common.events;

/**
 * Routing keys used on the shared topic exchange `hotelos.events`.
 * Keeping them in one place avoids typo-driven bugs (see Task 4 debug log entry DB-02).
 */
public final class RoutingKeys {
    public static final String EXCHANGE = "hotelos.events";

    public static final String ROOM_BOOKED          = "room.booked";
    public static final String ROOM_VACATED         = "room.vacated";
    public static final String ROOM_CLEANED         = "room.cleaned";
    public static final String ORDER_PLACED         = "order.placed";
    public static final String ORDER_COMPLETED      = "order.completed";
    public static final String MAINTENANCE_REPORTED = "maintenance.reported";
    public static final String MAINTENANCE_RESOLVED = "maintenance.resolved";

    private RoutingKeys() {}
}
