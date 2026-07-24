package com.leaguemate.api.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leaguemate.api.dto.CreateTeamRequest;
import com.leaguemate.api.dto.CreateTournamentRequest;
import com.leaguemate.api.dto.LoginRequest;
import com.leaguemate.api.dto.UpdateMatchResultRequest;
import com.leaguemate.api.entity.Match;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Integrazione - Ciclo di vita completo di un torneo")
class TournamentFlowIntegrationTest {

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
        persistUser("player", "player@leaguemate.com", Role.USER);

        adminToken = obtainToken("admin");
        userToken = obtainToken("player");
    }

    private void persistUser(String username, String email, Role role) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("password123"));
        user.setFirstName("Nome");
        user.setLastName("Cognome");
        user.setRole(role);
        userRepository.save(user);
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
                        .content(objectMapper.writeValueAsString(
                                new CreateTeamRequest(name, null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("id").asLong();
    }

    private Long createTournament(String name) throws Exception {
        String body = mockMvc.perform(post("/api/tournaments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateTournamentRequest(name, "2026/2027"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("id").asLong();
    }

    private void registerTeam(Long tournamentId, Long teamId) throws Exception {
        mockMvc.perform(post("/api/tournaments/" + tournamentId + "/register-team/" + teamId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated());
    }

    private Long setUpTournamentWithFourTeams() throws Exception {
        Long tournamentId = createTournament("Grand Line Cup");

        registerTeam(tournamentId, createTeam("Straw Hat FC"));
        registerTeam(tournamentId, createTeam("Heart Pirates"));
        registerTeam(tournamentId, createTeam("Red Hair United"));
        registerTeam(tournamentId, createTeam("Blackbeard City"));

        return tournamentId;
    }

    @Test
    @DisplayName("Il calendario di 4 squadre genera 3 giornate da 2 partite")
    void generateRounds_WithFourTeams_CreatesSixMatches() throws Exception {
        Long tournamentId = setUpTournamentWithFourTeams();

        mockMvc.perform(post("/api/tournaments/" + tournamentId + "/generate-rounds")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        assertEquals(6, matchRepository.count());

        mockMvc.perform(get("/api/tournaments/" + tournamentId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("Ogni squadra incontra tutte le altre esattamente una volta")
    void generateRounds_EachPairMeetsExactlyOnce() throws Exception {
        Long tournamentId = setUpTournamentWithFourTeams();

        mockMvc.perform(post("/api/tournaments/" + tournamentId + "/generate-rounds")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        List<Match> matches = matchRepository.findAll();
        long distinctPairs = matches.stream()
                .map(m -> {
                    Match full = matchRepository.findByIdWithTeams(m.getId()).orElseThrow();
                    long a = full.getHomeTeam().getId();
                    long b = full.getAwayTeam().getId();
                    return Math.min(a, b) + "-" + Math.max(a, b);
                })
                .distinct()
                .count();

        assertEquals(6, matches.size());
        assertEquals(6, distinctPairs);
    }

    @Test
    @DisplayName("La classifica riflette il risultato inserito e ordina per punti")
    void standings_ReflectMatchResult() throws Exception {
        Long tournamentId = setUpTournamentWithFourTeams();

        mockMvc.perform(post("/api/tournaments/" + tournamentId + "/generate-rounds")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        Long matchId = matchRepository.findAll().get(0).getId();

        mockMvc.perform(put("/api/matches/" + matchId + "/result")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateMatchResultRequest(3, 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        mockMvc.perform(get("/api/tournaments/" + tournamentId + "/standings")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].points").value(3))
                .andExpect(jsonPath("$[0].wins").value(1))
                .andExpect(jsonPath("$[0].goalDifference").value(2))
                .andExpect(jsonPath("$[3].goalDifference").value(-2));
    }

    @Test
    @DisplayName("Le statistiche aggregano partite giocate, gol e miglior attacco")
    void stats_AggregateTournamentData() throws Exception {
        Long tournamentId = setUpTournamentWithFourTeams();

        mockMvc.perform(post("/api/tournaments/" + tournamentId + "/generate-rounds")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        Long matchId = matchRepository.findAll().get(0).getId();

        mockMvc.perform(put("/api/matches/" + matchId + "/result")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateMatchResultRequest(3, 1))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/tournaments/" + tournamentId + "/stats")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registeredTeams").value(4))
                .andExpect(jsonPath("$.totalMatches").value(6))
                .andExpect(jsonPath("$.playedMatches").value(1))
                .andExpect(jsonPath("$.remainingMatches").value(5))
                .andExpect(jsonPath("$.totalGoals").value(4))
                .andExpect(jsonPath("$.averageGoalsPerMatch").value(4.0));
    }

    @Test
    @DisplayName("Le partite di una giornata sono esposte con i nomi delle squadre")
    void matchesByRound_ExposeTeamNames() throws Exception {
        Long tournamentId = setUpTournamentWithFourTeams();

        mockMvc.perform(post("/api/tournaments/" + tournamentId + "/generate-rounds")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        Match sample = matchRepository.findByIdWithTeams(
                matchRepository.findAll().get(0).getId()).orElseThrow();
        Long roundId = sample.getRound().getId();

        mockMvc.perform(get("/api/matches/round/" + roundId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].homeTeamName").isNotEmpty())
                .andExpect(jsonPath("$[0].awayTeamName").isNotEmpty())
                .andExpect(jsonPath("$[0].status").value("SCHEDULED"));
    }

    @Test
    @DisplayName("Un torneo con meno di due squadre non genera calendario")
    void generateRounds_ReturnsConflict_WithLessThanTwoTeams() throws Exception {
        Long tournamentId = createTournament("Torneo Vuoto");
        registerTeam(tournamentId, createTeam("Unica Squadra"));

        mockMvc.perform(post("/api/tournaments/" + tournamentId + "/generate-rounds")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("La stessa squadra non puo' iscriversi due volte allo stesso torneo")
    void registerTeam_ReturnsConflict_WhenAlreadyRegistered() throws Exception {
        Long tournamentId = createTournament("Grand Line Cup");
        Long teamId = createTeam("Straw Hat FC");

        registerTeam(tournamentId, teamId);

        mockMvc.perform(post("/api/tournaments/" + tournamentId + "/register-team/" + teamId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Un torneo gia' avviato non accetta nuove iscrizioni")
    void registerTeam_ReturnsConflict_WhenTournamentIsActive() throws Exception {
        Long tournamentId = setUpTournamentWithFourTeams();

        mockMvc.perform(post("/api/tournaments/" + tournamentId + "/generate-rounds")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        Long lateTeam = createTeam("Squadra Ritardataria");

        mockMvc.perform(post("/api/tournaments/" + tournamentId + "/register-team/" + lateTeam)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Un utente senza privilegi non puo' generare il calendario")
    void generateRounds_ReturnsForbidden_ForPlainUser() throws Exception {
        Long tournamentId = setUpTournamentWithFourTeams();

        mockMvc.perform(post("/api/tournaments/" + tournamentId + "/generate-rounds")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        assertEquals(0, matchRepository.count());
    }

    @Test
    @DisplayName("Un torneo inesistente restituisce 404")
    void getTournament_ReturnsNotFound_WhenIdDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/tournaments/99999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}