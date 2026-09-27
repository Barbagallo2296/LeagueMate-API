package com.leaguemate.api.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leaguemate.api.dto.AddTeamMemberRequest;
import com.leaguemate.api.dto.CreateTeamRequest;
import com.leaguemate.api.dto.CreateTournamentRequest;
import com.leaguemate.api.dto.LoginRequest;
import com.leaguemate.api.dto.UpdateMatchResultRequest;
import com.leaguemate.api.dto.UpdateTournamentRequest;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

        return objectMapper.readTree(body).get("access_token").asText();
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

        assertTrue(teamMemberRepository.existsById(memberId));
    }

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

        assertEquals(6, matchRepository.count());
    }

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

    @Test
    @DisplayName("Il calendario del torneo espone giornate e partite")
    void rounds_ExposeCalendarWithMatches() throws Exception {
        Long tournamentId = createOwnedTournamentWithTwoTeams();
        generateRounds(tournamentId, ownerToken);

        mockMvc.perform(get("/api/tournaments/" + tournamentId + "/rounds")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].roundNumber").value(1))
                .andExpect(jsonPath("$[0].matches.length()").value(1))
                .andExpect(jsonPath("$[0].matches[0].homeTeamName").isNotEmpty());

        mockMvc.perform(get("/api/tournaments/99999/rounds")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Le squadre iscritte e i tornei dell'organizzatore sono consultabili")
    void registeredTeams_AndMyTournaments() throws Exception {
        Long tournamentId = createOwnedTournamentWithTwoTeams();

        mockMvc.perform(get("/api/tournaments/" + tournamentId + "/teams")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").isNumber());

        mockMvc.perform(get("/api/tournaments/mine")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(tournamentId));

        mockMvc.perform(get("/api/tournaments/mine")
                        .header("Authorization", "Bearer " + otherOrganizerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Una giornata inesistente restituisce 404")
    void matchesOfUnknownRound_ReturnNotFound() throws Exception {
        mockMvc.perform(get("/api/matches/round/99999")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Un utente con ruolo USER non puo' diventare co-organizzatore")
    void addOrganizer_ReturnsConflict_ForPlainUser() throws Exception {
        Long tournamentId = createOwnedTournamentWithTwoTeams();
        Long playerId = userRepository.findByUsername("player").orElseThrow().getId();

        mockMvc.perform(post("/api/tournaments/" + tournamentId + "/organizers/" + playerId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("I punti non si modificano dopo l'avvio del torneo")
    void updatePoints_ReturnsConflict_AfterStart() throws Exception {
        Long tournamentId = createOwnedTournamentWithTwoTeams();
        generateRounds(tournamentId, ownerToken);

        mockMvc.perform(put("/api/tournaments/" + tournamentId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateTournamentRequest("Owner Cup", "2026/2027", 2, 1))))
                .andExpect(status().isConflict());

        mockMvc.perform(put("/api/tournaments/" + tournamentId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateTournamentRequest("Owner Cup Rinominata", "2026/2027", 3, 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Owner Cup Rinominata"));
    }

    @Test
    @DisplayName("Il preflight CORS dal frontend React e' accettato, da altre origini no")
    void corsPreflight_AllowsConfiguredOriginOnly() throws Exception {
        mockMvc.perform(options("/api/tournaments")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));

        mockMvc.perform(options("/api/tournaments")
                        .header("Origin", "http://sito-malevolo.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("L'ultimo organizzatore di un torneo non puo' essere rimosso")
    void removeOrganizer_ReturnsConflict_ForLastOrganizer() throws Exception {
        Long tournamentId = createOwnedTournamentWithTwoTeams();
        Long ownerId = userRepository.findByUsername("owner").orElseThrow().getId();

        mockMvc.perform(delete("/api/tournaments/" + tournamentId + "/organizers/" + ownerId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isConflict());

        Long intruderId = userRepository.findByUsername("intruder").orElseThrow().getId();
        mockMvc.perform(post("/api/tournaments/" + tournamentId + "/organizers/" + intruderId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/tournaments/" + tournamentId + "/organizers/" + ownerId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Chi crea una squadra ne diventa proprietario e capitano e la gestisce da solo")
    void teamOwner_ManagesOwnTeam_OthersAreForbidden() throws Exception {
        Long playerId = userRepository.findByUsername("player").orElseThrow().getId();
        Long intruderId = userRepository.findByUsername("intruder").orElseThrow().getId();

        String body = mockMvc.perform(post("/api/teams")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTeamRequest("Squadra di Player", null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ownerId").value(playerId))
                .andReturn().getResponse().getContentAsString();
        Long teamId = objectMapper.readTree(body).get("id").asLong();

        mockMvc.perform(get("/api/teams/" + teamId + "/members")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].username").value("player"))
                .andExpect(jsonPath("$[0].teamRole").value("CAPTAIN"));

        mockMvc.perform(put("/api/teams/" + teamId)
                        .header("Authorization", "Bearer " + otherOrganizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTeamRequest("Rubata", null))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/teams/" + teamId + "/members")
                        .header("Authorization", "Bearer " + otherOrganizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddTeamMemberRequest(intruderId, TeamRole.PLAYER))))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/teams/" + teamId)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTeamRequest("Squadra Rinominata", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Squadra Rinominata"));

        mockMvc.perform(post("/api/teams/" + teamId + "/members")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddTeamMemberRequest(intruderId, TeamRole.PLAYER))))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/api/teams/" + teamId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTeamRequest("Modificata da Admin", null))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Il telefono nel profilo e' visibile solo al proprietario o a un ADMIN")
    void getUserProfile_HidesPhone_FromOtherUsers() throws Exception {
        Long ownerId = userRepository.findByUsername("owner").orElseThrow().getId();

        mockMvc.perform(put("/api/users/" + ownerId + "/profile")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bio\": \"Organizzatore\", \"phoneNumber\": \"+39 333 1234567\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/users/" + ownerId + "/profile")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bio").value("Organizzatore"))
                .andExpect(jsonPath("$.phoneNumber").doesNotExist());

        mockMvc.perform(get("/api/users/" + ownerId + "/profile")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(jsonPath("$.phoneNumber").value("+39 333 1234567"));

        mockMvc.perform(get("/api/users/" + ownerId + "/profile")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.phoneNumber").value("+39 333 1234567"));
    }
}
