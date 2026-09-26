package com.leaguemate.api.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leaguemate.api.dto.LoginRequest;
import com.leaguemate.api.dto.RegisterRequest;
import com.leaguemate.api.entity.Role;
import com.leaguemate.api.entity.User;
import com.leaguemate.api.repository.UserRepository;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Integrazione - Autenticazione e autorizzazione")
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
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
        LoginRequest login = new LoginRequest(username, "password123");

        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(body).get("token").asText();
    }

    @Test
    @DisplayName("Registrazione: crea l'utente e non espone la password")
    void register_CreatesUser_AndNeverReturnsPassword() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "luffy@leaguemate.com", "luffy", "password123", "Monkey", "Luffy");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("luffy"))
                .andExpect(jsonPath("$.email").value("luffy@leaguemate.com"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist());

        assertTrue(userRepository.existsByUsername("luffy"));
    }

    @Test
    @DisplayName("Registrazione: la password viene salvata cifrata con BCrypt")
    void register_StoresHashedPassword() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "nami@leaguemate.com", "nami", "password123", "Nami", "Navigator");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        User saved = userRepository.findByUsername("nami").orElseThrow();

        assertNotEquals("password123", saved.getPassword());
        assertTrue(saved.getPassword().startsWith("$2a$"));
        assertTrue(passwordEncoder.matches("password123", saved.getPassword()));
    }

    @Test
    @DisplayName("Registrazione: username duplicato restituisce 409")
    void register_ReturnsConflict_WhenUsernameAlreadyExists() throws Exception {
        persistUser("luffy", "luffy@leaguemate.com", Role.USER);

        RegisterRequest request = new RegisterRequest(
                "altra@leaguemate.com", "luffy", "password123", "Monkey", "Luffy");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("Registrazione: email non valida restituisce 400")
    void register_ReturnsBadRequest_WhenEmailIsInvalid() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "non-una-email", "zoro", "password123", "Roronoa", "Zoro");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        assertFalse(userRepository.existsByUsername("zoro"));
    }

    @Test
    @DisplayName("Login: credenziali corrette restituiscono un token JWT")
    void login_ReturnsToken_WhenCredentialsAreValid() throws Exception {
        persistUser("manuel22", "manuel@leaguemate.com", Role.ADMIN);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("manuel22", "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    @DisplayName("Login: password errata e utente inesistente danno la stessa risposta 401")
    void login_ReturnsIdenticalUnauthorized_ForWrongPasswordAndUnknownUser() throws Exception {
        persistUser("manuel22", "manuel@leaguemate.com", Role.ADMIN);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("manuel22", "passwordSbagliata"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("utenteInesistente", "password123"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    @DisplayName("Flusso completo: registrazione, login e accesso a un endpoint protetto")
    void fullFlow_RegisterThenLoginThenAccessProtectedEndpoint() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "sanji@leaguemate.com", "sanji", "password123", "Vinsmoke", "Sanji");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        String token = obtainToken("sanji");

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("sanji"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    @DisplayName("Token JWT malformato restituisce 401 dal filtro")
    void malformedToken_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer token-non-valido"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or expired authentication token"));
    }

    @Test
    @DisplayName("Endpoint riservato ad ADMIN: 403 con ruolo USER, 200 con ruolo ADMIN")
    void adminEndpoint_IsForbiddenForUser_AndAllowedForAdmin() throws Exception {
        persistUser("player", "player@leaguemate.com", Role.USER);
        persistUser("boss", "boss@leaguemate.com", Role.ADMIN);

        String userToken = obtainToken("player");
        String adminToken = obtainToken("boss");

        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("Endpoint protetto senza token viene rifiutato")
    void protectedEndpoint_IsRejected_WhenTokenIsMissing() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().is4xxClientError());
    }
}