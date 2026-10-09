package co.zeal.wellbeing.shared.id;

import java.util.UUID;

/** Identificadores legibles y sin colisiones: {@code <prefijo>-<uuid>}. */
public final class Ids {

    private Ids() {
    }

    public static String next(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
