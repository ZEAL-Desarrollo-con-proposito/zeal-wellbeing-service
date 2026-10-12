package co.zeal.wellbeing.security;

import co.zeal.wellbeing.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HU-01 y HU-11 de punta a punta: login real, token real y aislamiento del Wellbeing Domain. */
@AutoConfigureMockMvc
class WellbeingApiSecurityIT extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void loginReturnsTheSessionContract() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\" David.Cajamarca@nexura.co \",\"password\":\"Zeal2026*\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType", is("Bearer")))
                .andExpect(jsonPath("$.user.id", is("usr-empleado-001")))
                .andExpect(jsonPath("$.user.role", is("EMPLEADO")))
                .andExpect(jsonPath("$.user.organization", is("Nexura S.A.S.")));
    }

    @Test
    void wrongPasswordIsRejectedWithAReadableMessage() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"david.cajamarca@nexura.co\",\"password\":\"otra\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", is("Correo o contraseña incorrectos")));
    }

    @Test
    void requestsWithoutTokenGet401() throws Exception {
        mvc.perform(get("/api/wellbeing/psicologos")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }

    @Test
    void adminNeverReachesTheWellbeingDomain() throws Exception {
        String admin = tokenFor("talento.humano@nexura.co");
        for (String path : new String[]{"/api/wellbeing/psicologos", "/api/wellbeing/reservas/mias",
                "/api/wellbeing/reservas/x/sala", "/api/chats/a:b/mensajes"}) {
            mvc.perform(get(path).header("Authorization", "Bearer " + admin))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message", is("No tienes permiso para esta operación.")));
        }
    }

    @Test
    void employeesCannotUsePsychologistEndpoints() throws Exception {
        String employee = tokenFor("david.cajamarca@nexura.co");
        mvc.perform(get("/api/wellbeing/franjas/mias?desde=2026-01-01&hasta=2026-01-02")
                        .header("Authorization", "Bearer " + employee))
                .andExpect(status().isForbidden());
    }

    @Test
    void psychologistDirectoryMatchesTheFrontendContract() throws Exception {
        String employee = tokenFor("david.cajamarca@nexura.co");
        mvc.perform(get("/api/wellbeing/psicologos").header("Authorization", "Bearer " + employee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").isString())
                .andExpect(jsonPath("$[0].title").isString())
                .andExpect(jsonPath("$[0].focusAreas").isArray());
    }

    private String tokenFor(String email) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Zeal2026*\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return body.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
    }
}
