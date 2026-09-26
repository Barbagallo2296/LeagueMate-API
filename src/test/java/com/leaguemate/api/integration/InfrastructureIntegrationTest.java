package com.leaguemate.api.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leaguemate.api.dto.LoginRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "security.login-rate-limit.capacity=3")
@DisplayName("Integrazione - Rate limit, health check e documentazione API")
class InfrastructureIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockHttpServletRequestBuilder loginFrom(String ip) throws Exception {
        return post("/api/auth/login")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest("nessuno", "passwordSbagliata")));
    }

    @Test
    @DisplayName("Oltre il limite di tentativi il login risponde 429 con Retry-After")
    void login_ReturnsTooManyRequests_AfterLimit() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(loginFrom("10.0.0.1")).andExpect(status().isUnauthorized());
        }

        mockMvc.perform(loginFrom("10.0.0.1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429));
    }

    @Test
    @DisplayName("Il limite e' per IP: un altro client non viene bloccato")
    void rateLimit_IsPerClientIp() throws Exception {
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(loginFrom("10.0.0.2"));
        }

        mockMvc.perform(loginFrom("10.0.0.3")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Il rate limit non riguarda gli altri endpoint")
    void rateLimit_DoesNotApplyToOtherEndpoints() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("L'health check e' pubblico e non espone dettagli")
    void health_IsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    @DisplayName("Gli altri endpoint di Actuator non sono esposti")
    void otherActuatorEndpoints_AreNotExposed() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("La specifica OpenAPI e' pubblica e dichiara l'autenticazione JWT")
    void openApiSpec_IsPublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("LeagueMate API"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
    }
}
