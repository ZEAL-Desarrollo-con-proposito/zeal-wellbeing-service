package co.zeal.wellbeing.freedslot;

import co.zeal.wellbeing.psychologist.PsychologistSummary;

import java.time.Instant;

/** Lo que ve un destinatario del cupo: contrato de {@code isFreedSlotOffer()} del frontend. */
public record FreedSlotOfferView(String id, PsychologistSummary psychologist, Instant startsAt, Instant endsAt,
                                 Instant expiresAt) {

    public static FreedSlotOfferView of(FreedSlotOffer offer, PsychologistSummary psychologist) {
        return new FreedSlotOfferView(offer.getId(), psychologist, offer.getStartsAt(), offer.getEndsAt(),
                offer.getExpiresAt());
    }
}
