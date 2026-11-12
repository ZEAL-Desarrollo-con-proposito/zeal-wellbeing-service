package co.zeal.wellbeing.support;

import co.zeal.wellbeing.auth.UserAccountRepository;
import co.zeal.wellbeing.auth.session.SessionService;
import org.springframework.stereotype.Component;

/**
 * Abre sesiones reales de ZEAL (access token firmado + refresh token en la base) sin pasar por
 * Microsoft, para las pruebas que solo necesitan "estar logueado" como una cuenta semilla.
 */
@Component
public class Sessions {

    private final SessionService sessions;
    private final UserAccountRepository accounts;

    public Sessions(SessionService sessions, UserAccountRepository accounts) {
        this.sessions = sessions;
        this.accounts = accounts;
    }

    public SessionService.IssuedSession open(String email) {
        return sessions.open(accounts.findByEmail(email).orElseThrow(), false);
    }

    public String bearer(String email) {
        return "Bearer " + open(email).access().value();
    }
}
