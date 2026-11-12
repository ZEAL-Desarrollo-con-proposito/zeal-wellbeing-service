package co.zeal.wellbeing.shared.video;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

/**
 * Firma el permiso de entrada a una sala de LiveKit: un JWT HS256 con la API key como emisor, la
 * persona como sujeto y el permiso {@code video} limitado a una sola sala.
 */
@Component
public class LiveKitTokenIssuer {

    private final LiveKitProperties properties;
    private final MACSigner signer;

    public LiveKitTokenIssuer(LiveKitProperties properties) throws JOSEException {
        this.properties = properties;
        this.signer = new MACSigner(properties.apiSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String issue(RoomGrant grant, Instant now, Instant expiresAt) {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(properties.apiKey())
                .subject(grant.identity())
                .claim("name", grant.displayName())
                .claim("metadata", grant.metadata())
                .notBeforeTime(Date.from(now))
                .expirationTime(Date.from(expiresAt))
                .claim("video", Map.of(
                        "room", grant.room(),
                        "roomJoin", true,
                        "canSubscribe", true,
                        "canPublish", true,
                        "canPublishData", true,
                        "roomAdmin", grant.host()))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).build(), claims);
        try {
            jwt.sign(signer);
        } catch (JOSEException ex) {
            throw new IllegalStateException("No se pudo firmar el permiso de la sala " + grant.room(), ex);
        }
        return jwt.serialize();
    }

    public String publicUrl() {
        return properties.publicUrl();
    }

    /** Quién entra a qué sala; {@code host} puede administrarla (silenciar, retirar participantes). */
    public record RoomGrant(String room, String identity, String displayName, String metadata, boolean host) {
    }
}
