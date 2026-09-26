package com.leaguemate.api.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leaguemate.api.dto.AddTeamMemberRequest;
import com.leaguemate.api.dto.CreateTeamRequest;
import com.leaguemate.api.dto.CreateTournamentRequest;
import com.leaguemate.api.dto.LoginRequest;
import com.leaguemate.api.dto.UpdateMatchResultRequest;
import com.leaguemate.api.entity.Role;
import com.leaguemate.api.entity.TeamRole;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Integrazione - Autorizzazione a livello di risorsa e gestione errori")
class AuthorizationRulesIntegrationTest {

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
    private String ownerToken;
    private String otherOrganizerToken;
    private String userToken;

    @BeforeEach
    void setUp() throws Exception {
        matchRepository.deleteAll();
        registrationRepository.deleteAll();
        teamMemberRepository.deleteAll();
        tournamentRepository.deleteAll();
        teamRepository.deleteAll();
        userRepository.deleteAll();

        persistUser("admin", "admin@leaguemate.com", Role.ADMIN);
        persistUser("owner", "owner@leaguemate.com", Role.ORGANIZER);
        persistUser("intruder", "intruder@leaguemate.com", Role.ORGANIZER);
        persistUser("player", "player@leaguemate.com", Role.USER);

        adminToken = obtainToken("admin");
        ownerToken = obtainToken("owner");
        otherOrganizerToken = obtainToken("intruder");
        userToken = obtainToken("player");
    }

    private User persistUser(String username, String email, Role role) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("password123"));
        user.setFirstName("Nome");
        user.setLastName("Cognome");
        user.setRole(role);
        return userRepository.save(user);
    }

    private String obtainToken(String username) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(username, "password123"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("token").asText();
    }

    private Long createTeam(String name) throws Exception {
        String body = mockMvc.perform(post("/api/teams")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTeamRequest(name, null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("id").asLong();
    }

    /** Torneo creato da "owner" con due squadre iscritte: un calendario da una sola partita. */
    private Long createOwnedTournamentWithTwoTeams() throws Exception {
        String body = mockMvc.perform(post("/api/tournaments")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateTournamentRequest("Owner Cup", "2026/2027"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long tournamentId = objectMapper.readTree(body).get("id").asLong();

        for (String team : new String[]{"Straw Hat FC", "Heart Pirates"}) {
            mockMvc.perform(post("/api/tournaments/" + tournamentId + "/register-team/" + createTeam(team))
                            .header("Authorization", "Bearer " + ownerToken))
                    .andExpect(status().isCreated());
        }
        return tournamentId;
    }

    private void generateRounds(Long tournamentId, String token) throws Exception {
        mockMvc.perform(post("/api/tournaments/" + tournamentId + "/generate-rounds")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    // --- Ownership dei tornei ---

    @Test
    @DisplayName("Il creatore del torneo ne diventa organizzatore")
    void createTournament_AddsCreatorToOrganizers() throws Exception {
        Long tournamentId = createOwnedTournamentWithTwoTeams();

        mockMvc.perform(get("/api/tournaments/" + tournamentId + "/organizers")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].username").value("owner"));
    }

    @Test
    @DisplayName("Un ORGANIZER che non organizza il torneo riceve 403")
    void otherOrganizer_IsForbidden_OnTournamentHeDoesNotOrganize() throws Exception {
        Long tournamentId = createOwnedTournamentWithTwoTeams();

        mockMvc.perform(post("/api/tournaments/" + tournamentId + "/generate-rounds")
                        .header("Authorization", "Bearer " + otherOrganizerToken))
                .andExpect(status().isForbidden());

        assertEquals(0, matchRepository.count());

        generateRounds(tournamentId, ownerToken);
        Long matchId = matchRepository.findAll().get(0).getId();

        mockMvc.perform(put("/api/matches/" + matchId + "/result")
                        .header("Authorization", "Bearer " + otherOrganizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateMatchResultRequest(1, 0))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Un co-organizzatore aggiunto dal creatore puo' gestire il torneo")
    void addedCoOrganizer_CanManageTournament() throws Exception {
        Long tournamentId = createOwnedTournamentWithTwoTeams();
        Long intruderId = userRepository.findByUsername("intruder").orElseThrow().getId();

        mockMvc.perform(post("/api/tournaments/" + tournamentId + "/organizers/" + intruderId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isCreated());

        generateRounds(tournamentId, otherOrganizerToken);
    }

    // --- Ciclo di vita: chiusura del torneo ---

    @Test
    @DisplayName("Un torneo si chiude solo a partite concluse e poi i risultati sono bloccati")
    void completeTournament_ThenMatchResultsAreLocked() throws Exception {
        Long tournamentId = createOwnedTournamentWithTwoTeams();
        generateRounds(tournamentId, ownerToken);
        Long matchId = matchRepository.findAll().get(0).getId();

        mockMvc.perform(post("/api/tournaments/" + tournamentId + "/complete")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isConflict());

        mockMvc.perform(put("/api/matches/" + matchId + "/result")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateMatchResultRequest(2, 1))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/tournaments/" + tournamentId + "/complete")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        mockMvc.perform(put("/api/matches/" + matchId + "/result")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateMatchResultRequest(0, 5))))
                .andExpect(status().isConflict());
    }

    // --- Membri delle squadre ---

    @Test
    @DisplayName("Un membro non si rimuove passando l'id di un'altra squadra")
    void removeMember_ReturnsNotFound_WhenTeamIdDoesNotMatch() throws Exception {
        Long teamA = createTeam("Straw Hat FC");
        Long teamB = createTeam("Heart Pirates");
        Long playerId = userRepository.findByUsername("player").orElseThrow().getId();

        String body = mockMvc.perform(post("/api/teams/" + teamA + "/members")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AddTeamMemberRequest(playerId, TeamRole.PLAYER))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long memberId = objectMapper.readTree(body).get("id").asLong();

        mockMvc.perform(delete("/api/teams/" + teamB + "/members/" + memberId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());

        assertEquals(1, teamMemberRepository.count());
    }

    // --- Privacy ---

    @Test
    @DisplayName("L'email di un utente e' visibile solo a lui stesso o a un ADMIN")
    void getUserById_HidesEmail_FromOtherUsers() throws Exception {
        Long ownerId = userRepository.findByUsername("owner").orElseThrow().getId();

        mockMvc.perform(get("/api/users/" + ownerId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("owner"))
                .andExpect(jsonPath("$.email").doesNotExist());

        mockMvc.perform(get("/api/users/" + ownerId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(jsonPath("$.email").value("owner@leaguemate.com"));

        mockMvc.perform(get("/api/users/" + ownerId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.email").value("owner@leaguemate.com"));
    }

    // --- Paginazione ---

    @Test
    @DisplayName("Le liste sono paginate e ordinabili solo sui campi ammessi")
    void lists_ArePaginated_AndSortIsWhitelisted() throws Exception {
        createTeam("Straw Hat FC");
        createTeam("Heart Pirates");
        createTeam("Red Hair United");

        mockMvc.perform(get("/api/teams?page=0&size=2&sort=name,asc")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].name").value("Heart Pirates"))
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.page.totalPages").value(2));

        mockMvc.perform(get("/api/tournaments?sort=organizers.password")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isBadRequest());
    }

    // --- Andata e ritorno ---

    @Test
    @DisplayName("Un torneo andata e ritorno genera il doppio delle partite")
    void doubleRoundRobinTournament_GeneratesReturnLeg() throws Exception {
        String body = mockMvc.perform(post("/api/tournaments")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateTournamentRequest("Andata e Ritorno", "2026/2027", true))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.doubleRoundRobin").value(true))
                .andReturn().getResponse().getContentAsString();
        Long tournamentId = objectMapper.readTree(body).get("id").asLong();

        for (String team : new String[]{"Straw Hat FC", "Heart Pirates", "Red Hair United"}) {
            mockMvc.perform(post("/api/tournaments/" + tournamentId + "/register-team/" + createTeam(team))
                            .header("Authorization", "Bearer " + ownerToken))
                    .andExpect(status().isCreated());
        }

        generateRounds(tournamentId, ownerToken);

        // 3 squadre: 3 partite di andata + 3 di ritorno
        assertEquals(6, matchRepository.count());
    }

    // --- Codici di errore ---

    @Test
    @DisplayName("Una richiesta senza token riceve 401, non 403")
    void requestWithoutToken_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/tournaments"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("Un body JSON malformato riceve 400, non 500")
    void malformedJson_ReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/teams")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"name\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("Un path variable non valido riceve 400, non 500")
    void invalidPathVariable_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/tournaments/status/INESISTENTE")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/tournaments/abc")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }
}
