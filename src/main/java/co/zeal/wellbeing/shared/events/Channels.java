package co.zeal.wellbeing.shared.events;

/** Canales Redis Pub/Sub que publica este servicio. El Real-Time Gateway escucha {@code zeal.*}. */
public final class Channels {

    public static final String SLOTS = "zeal.wellbeing.slots";
    public static final String FREED_SLOT_OFFERED = "zeal.wellbeing.freed-slots.offered";
    public static final String FREED_SLOT_TAKEN = "zeal.wellbeing.freed-slots.taken";
    public static final String ROOM_UPDATED = "zeal.wellbeing.rooms.updated";
    public static final String NOTIFICATIONS = "zeal.notifications";

    private Channels() {
    }
}
