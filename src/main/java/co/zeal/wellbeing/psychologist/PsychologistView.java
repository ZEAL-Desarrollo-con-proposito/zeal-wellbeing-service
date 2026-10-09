package co.zeal.wellbeing.psychologist;

import java.time.Instant;
import java.util.List;

/** Contrato de {@code isPsychologist()} del frontend. {@code nextFreeSlot} es {@code null} sin bloques libres. */
public record PsychologistView(String id, String fullName, String title, List<String> focusAreas,
                               TimeRange nextFreeSlot) {

    public record TimeRange(Instant startsAt, Instant endsAt) {
    }
}
