package com.leaguemate.api.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leaguemate.api.dto.LoginRequest;
import com.leaguemate.api.entity.OAuth2AuthorizationRecord;
import com.leaguemate.api.entity.Role;
import com.leaguemate.api.entity.User;
import com.leaguemate.api.repository.*;
import com.leaguemate.api.security.ExpiredTokenCleanup;
import com.leaguemate.api.security.TokenHasher;
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

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Integrazione - Token opachi con Spring Authorization Server")
class TokenAuthenticationIntegrationTest {

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
    private OAuth2AuthorizationRecordRepository authorizationRecordRepository;

    @Autowired
    private ExpiredTokenCleanup expiredTokenCleanup;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        authorizationRecordRepository.deleteAll();
        matchRepository.deleteAll();
        registrationRepository.deleteAll();
        teamMemberRepository.deleteAll();
        tournamentRepository.deleteAll();
        teamRepository.deleteAll();
        userRepository.deleteAll();

        persistUser("alice", "alice@leaguemate.com");
        persistUser("bob", "bob@leaguemate.com");
    }

    private void persistUser(String username, String email) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("password123"));
        user.setFirstName("Nome");
        user.setLastName("Cognome");
        user.setRole(Role.USER);
        userRepository.save(user);
    }

    private JsonNode login(String username) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, "password123"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private String refreshBody(String refreshToken) throws Exception {
        return objectMapper.writeValueAsString(Map.of("refresh_token", refreshToken));
    }

    private int statusOfMe(String accessToken) throws Exception {
        return mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + accessToken))
                .andReturn().getResponse().getStatus();
    }

    private OAuth2AuthorizationRecord onlyRecord() {
        List<OAuth2AuthorizationRecord> records = authorizationRecordRepository.findAll();
        assertEquals(1, records.size());
        return records.get(0);
    }

    private static LocalDateTime minutesAgo(long minutes) {
        return LocalDateTime.now(ZoneOffset.UTC).minusMinutes(minutes);
    }

    @Test
    @DisplayName("Il login restituisce una coppia di token opachi e l'access token autentica le richieste")
    void login_ReturnsOpaqueTokenPair() throws Exception {
        JsonNode tokens = login("alice");
        String accessToken = tokens.get("access_token").asText();

        assertFalse(accessToken.chars().filter(c -> c == '.').count() == 2, "Non deve essere un JWT");
        assertNotEquals(accessToken, tokens.get("refresh_token").asText());
        assertEquals("Bearer", tokens.get("token_type").asText());
        assertTrue(tokens.get("expires_in").asLong() > 0 && tokens.get("expires_in").asLong() <= 900);

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"));
    }

    @Test
    @DisplayName("Nel database i token sono salvati solo come hash SHA-512")
    void tokens_AreStoredOnlyAsHash() throws Exception {
        JsonNode tokens = login("alice");
        String accessToken = tokens.get("access_token").asText();
        String refreshToken = tokens.get("refresh_token").asText();

        OAuth2AuthorizationRecord record = onlyRecord();

        assertEquals(TokenHasher.sha512(accessToken), record.getAccessTokenHash());
        assertEquals(TokenHasher.sha512(refreshToken), record.getRefreshTokenHash());
        assertEquals(128, record.getAccessTokenHash().length());
        assertNotEquals(accessToken, record.getAccessTokenHash());
        assertNotEquals(refreshToken, record.getRefreshTokenHash());
        assertEquals("alice", record.getPrincipalName());
    }

    @Test
    @DisplayName("Il refresh ruota i token: la vecchia coppia non e' piu' valida")
    void refresh_RotatesTokens() throws Exception {
        JsonNode first = login("alice");
        String oldAccess = first.get("access_token").asText();
        String oldRefresh = first.get("refresh_token").asText();

        String body = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(oldRefresh)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode second = objectMapper.readTree(body);
        String newAccess = second.get("access_token").asText();
        String newRefresh = second.get("refresh_token").asText();

        assertNotEquals(oldAccess, newAccess);
        assertNotEquals(oldRefresh, newRefresh);
        assertEquals(200, statusOfMe(newAccess));
        assertEquals(401, statusOfMe(oldAccess));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(oldRefresh)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or expired refresh token"));
    }

    @Test
    @DisplayName("Il logout revoca subito access token e refresh token della sessione")
    void logout_RevokesSession() throws Exception {
        JsonNode tokens = login("alice");
        String accessToken = tokens.get("access_token").asText();

        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or expired authentication token"));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(tokens.get("refresh_token").asText())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Il logout-all revoca tutte le sessioni dell'utente e solo le sue")
    void logoutAll_RevokesEverySessionOfTheUserOnly() throws Exception {
        String aliceLaptop = login("alice").get("access_token").asText();
        String alicePhone = login("alice").get("access_token").asText();
        String bob = login("bob").get("access_token").asText();

        mockMvc.perform(post("/api/auth/logout-all").header("Authorization", "Bearer " + aliceLaptop))
                .andExpect(status().isNoContent());

        assertEquals(401, statusOfMe(aliceLaptop));
        assertEquals(401, statusOfMe(alicePhone));
        assertEquals(200, statusOfMe(bob));
    }

    @Test
    @DisplayName("Un access token scaduto e' rifiutato, ma il refresh token permette di rinnovarlo")
    void expiredAccessToken_IsRejected_ButCanBeRefreshed() throws Exception {
        JsonNode tokens = login("alice");
        OAuth2AuthorizationRecord record = onlyRecord();
        record.setAccessTokenIssuedAt(minutesAgo(30));
        record.setAccessTokenExpiresAt(minutesAgo(1));
        authorizationRecordRepository.save(record);

        assertEquals(401, statusOfMe(tokens.get("access_token").asText()));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(tokens.get("refresh_token").asText())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Un refresh token scaduto e' rifiutato e l'autorizzazione viene eliminata")
    void expiredRefreshToken_IsRejectedAndRemoved() throws Exception {
        JsonNode tokens = login("alice");
        OAuth2AuthorizationRecord record = onlyRecord();
        record.setRefreshTokenIssuedAt(minutesAgo(60));
        record.setRefreshTokenExpiresAt(minutesAgo(1));
        authorizationRecordRepository.save(record);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(tokens.get("refresh_token").asText())))
                .andExpect(status().isUnauthorized());

        assertEquals(0, authorizationRecordRepository.count());
    }

    @Test
    @DisplayName("Refresh con token sconosciuto o mancante: 401 e 400")
    void refresh_RejectsUnknownOrMissingToken() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody("token-inventato")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Logout e logout-all richiedono un token valido")
    void logout_RequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/auth/logout")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/logout-all")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("La pulizia automatica elimina solo le autorizzazioni completamente scadute")
    void cleanup_RemovesOnlyExpiredAuthorizations() throws Exception {
        login("alice");
        login("bob");

        OAuth2AuthorizationRecord expired = authorizationRecordRepository.findAll().stream()
                .filter(r -> r.getPrincipalName().equals("alice"))
                .findFirst().orElseThrow();
        expired.setAccessTokenIssuedAt(minutesAgo(120));
        expired.setAccessTokenExpiresAt(minutesAgo(90));
        expired.setRefreshTokenIssuedAt(minutesAgo(120));
        expired.setRefreshTokenExpiresAt(minutesAgo(1));
        authorizationRecordRepository.save(expired);

        expiredTokenCleanup.removeExpiredAuthorizations();

        assertEquals(1, authorizationRecordRepository.count());
        assertEquals("bob", authorizationRecordRepository.findAll().get(0).getPrincipalName());
    }
}
