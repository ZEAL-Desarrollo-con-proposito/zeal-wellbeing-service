package co.zeal.wellbeing.organization;

import co.zeal.wellbeing.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Cada empresa ve solo a sus empleados, y solo sus datos de contacto. */
@AutoConfigureMockMvc
class OrganizationApiIT extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void eachCompanySeesOnlyItsOwnEmployeesInAlphabeticalOrder() throws Exception {
        mvc.perform(get("/api/organizacion/empleados").header("Authorization", bearer("talento.humano@nexura.co")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].fullName",
                        contains("Camilo Rincón", "David Cajamarca", "Mariana Torres", "Sofía Mejía")))
                .andExpect(jsonPath("$[0].email", is("camilo.rincon@nexura.co")))
                .andExpect(jsonPath("$[0].role").doesNotExist());

        mvc.perform(get("/api/organizacion/empleados").header("Authorization", bearer("gestion.humana@orbitalabs.co")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[*].fullName",
                        contains("Andrea Gómez", "Felipe Restrepo", "Natalia Vargas", "Santiago Herrera")));
    }

    @Test
    void onlyTheCompanyAdministratorCanListEmployees() throws Exception {
        mvc.perform(get("/api/organizacion/empleados").header("Authorization", bearer("andrea.gomez@orbitalabs.co")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/organizacion/empleados").header("Authorization", bearer("laura.martinez@zeal.co")))
                .andExpect(status().isForbidden());
    }

    @Test
    void newCompanyAdministratorStillCannotReachTheWellbeingDomain() throws Exception {
        mvc.perform(get("/api/wellbeing/psicologos").header("Authorization", bearer("gestion.humana@orbitalabs.co")))
                .andExpect(status().isForbidden());
    }

    private String bearer(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Zeal2026*\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.accessToken");
    }
}
