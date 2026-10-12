package co.zeal.wellbeing.auth;

import co.zeal.wellbeing.shared.security.JwtProperties;
import co.zeal.wellbeing.shared.security.ZealClaims;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/** Emite el JWT de sesión que validan los tres microservicios. */
@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final Clock clock;

    public TokenService(JwtEncoder encoder, JwtProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    public IssuedToken issue(UserAccount account) {
        Instant issuedAt = Instant.now(clock);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(account.getId())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(properties.timeToLive()))
                .claim(ZealClaims.ROLE, account.getRole().name())
                .claim(ZealClaims.FULL_NAME, account.getFullName())
                .claim(ZealClaims.EMAIL, account.getEmail())
                .claim(ZealClaims.ORGANIZATION, account.getOrganization())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(value, properties.timeToLive().toSeconds());
    }

    public record IssuedToken(String value, long expiresInSeconds) {
    }
}
