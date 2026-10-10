package co.zeal.wellbeing.reservation;

import java.util.Map;

public record SlotRealtimeEvent(String type, Map<String, Object> slot) {}
