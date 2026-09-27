package com.leaguemate.api.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leaguemate.api.dto.CreateTeamRequest;
import com.leaguemate.api.dto.CreateTournamentRequest;
import com.leaguemate.api.dto.LoginRequest;
import com.leaguemate.api.dto.RegisterRequest;
import com.leaguemate.api.dto.UpdateTournamentRequest;
import com.leaguemate.api.entity.Role;
import com.leaguemate.api.entity.User;
import com.leaguemate.api.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Integrazione - Validazione degli input al limite")
class InputValidationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private TournamentRepository tournamentRepository;

    @Autowired
    private TournamentRegistrationRepository registrationRepository;

    @Autowired
    private TeamMemberRepository teamMemberRepository;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        matchRepository.deleteAll();
        registrationRepository.deleteAll();
        teamMemberRepository.deleteAll();
        tournamentRepository.deleteAll();
        teamRepository.deleteAll();
        userRepository.deleteAll();

        User admin = new User();
        admin.setUsername("admin");
        admin.setEmail("admin@leaguemate.com");
        admin.setPassword(passwordEncoder.encode("password123"));
        admin.setFirstName("Nome");
        admin.setLastName("Cognome");
        admin.setRole(Role.ADMIN);
        userRepository.save(admin);

        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("admin", "password123"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        adminToken = objectMapper.readTree(body).get("access_token").asText();
    }

    private static String repeat(char c, int times) {
        return String.valueOf(c).repeat(times);
    }

    private void register(RegisterRequest request, int expectedStatus) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    @DisplayName("Registrazione: email, nome e cognome oltre la lunghezza delle colonne danno 400")
    void register_RejectsFieldsLongerThanColumns() throws Exception {
        String longEmail = repeat('a', 92) + "@test.com";
        register(new RegisterRequest(longEmail, "lungo1", "password123", "Nome", "Cognome"), 400);
        register(new RegisterRequest("ok1@test.com", "lungo2", "password123", repeat('n', 51), "Cognome"), 400);
        register(new RegisterRequest("ok2@test.com", "lungo3", "password123", "Nome", repeat('c', 51)), 400);
    }

    @Test
    @DisplayName("Registrazione: una password oltre i 72 caratteri gestiti da BCrypt da' 400, non 500")
    void register_RejectsPasswordLongerThanBcryptLimit() throws Exception {
        register(new RegisterRequest("pwd@test.com", "pwdlunga", repeat('p', 73), "Nome", "Cognome"), 400);
        register(new RegisterRequest("pwd@test.com", "pwdlimite", repeat('p', 72), "Nome", "Cognome"), 201);
    }

    @Test
    @DisplayName("Login: una password lunghissima da' un errore client, non 500")
    void login_WithVeryLongPassword_IsAClientError() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("admin", repeat('x', 200)))))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("Torneo: nome oltre 100 caratteri e stagione oltre 20 danno 400")
    void createTournament_RejectsFieldsLongerThanColumns() throws Exception {
        mockMvc.perform(post("/api/tournaments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTournamentRequest(repeat('t', 101), "2026/2027"))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/tournaments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTournamentRequest("Torneo", repeat('s', 21)))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Squadra: un logo oltre 255 caratteri da' 400")
    void createTeam_RejectsLogoUrlLongerThanColumn() throws Exception {
        mockMvc.perform(post("/api/teams")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateTeamRequest("Squadra", "https://logo.example/" + repeat('l', 240)))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Torneo: un pareggio non puo' valere piu' di una vittoria")
    void updateTournament_RejectsDrawWorthMoreThanWin() throws Exception {
        String body = mockMvc.perform(post("/api/tournaments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTournamentRequest("Torneo", "2026/2027"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long tournamentId = objectMapper.readTree(body).get("id").asLong();

        mockMvc.perform(put("/api/tournaments/" + tournamentId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateTournamentRequest("Torneo", "2026/2027", 1, 3))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
