package co.zeal.wellbeing.security;

import co.zeal.wellbeing.support.IntegrationTest;
import co.zeal.wellbeing.support.Sessions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HU-11 y SEG-3 de punta a punta con tokens reales: aislamiento del Wellbeing Domain y 404 ante lo ajeno. */
@AutoConfigureMockMvc
class WellbeingApiSecurityIT extends IntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    Sessions sessions;
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void passwordLoginNoLongerExists() throws Exception {
        mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"email\":\"david.cajamarca@nexura.co\",\"password\":\"Zeal2026*\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void requestsWithoutTokenGet401() throws Exception {
        mvc.perform(get("/api/wellbeing/psicologos")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }

    /** HU-11 + SEG-3: el administrador recibe 404 (ya no 403) y cada intento queda auditado. */
    @Test
    void adminNeverReachesTheWellbeingDomainAndGets404() throws Exception {
        String admin = sessions.bearer("talento.humano@nexura.co");
        Integer before = jdbc.queryForObject(
                "select count(*) from security_audit where event_type = 'ACCESO_DENEGADO' and user_id = 'usr-admin-001'",
                Integer.class);
        for (String path : new String[]{"/api/wellbeing/psicologos", "/api/wellbeing/reservas/mias",
                "/api/wellbeing/reservas/x/sala", "/api/chats/a:b/mensajes"}) {
            mvc.perform(get(path).header("Authorization", admin))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message", is("El recurso solicitado no existe.")));
        }
        Integer after = jdbc.queryForObject(
                "select count(*) from security_audit where event_type = 'ACCESO_DENEGADO' and user_id = 'usr-admin-001'",
                Integer.class);
        assertThat(after - before).isEqualTo(4);
    }

    @Test
    void employeesCannotUsePsychologistEndpoints() throws Exception {
        mvc.perform(get("/api/wellbeing/franjas/mias?desde=2026-01-01&hasta=2026-01-02")
                        .header("Authorization", sessions.bearer("david.cajamarca@nexura.co")))
                .andExpect(status().isNotFound());
    }

    @Test
    void psychologistDirectoryMatchesTheFrontendContract() throws Exception {
        mvc.perform(get("/api/wellbeing/psicologos").header("Authorization", sessions.bearer("david.cajamarca@nexura.co")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").isString())
                .andExpect(jsonPath("$[0].title").isString())
                .andExpect(jsonPath("$[0].focusAreas").isArray());
    }
}
