package com.leaguemate.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leaguemate.api.dto.CreateTournamentRequest;
import com.leaguemate.api.entity.Tournament;
import com.leaguemate.api.entity.TournamentStatus;
import com.leaguemate.api.security.CustomUserDetailsService;
import com.leaguemate.api.security.JwtService;
import com.leaguemate.api.security.SecurityConfig;
import com.leaguemate.api.service.TournamentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TournamentController.class)
@Import(SecurityConfig.class)
class TournamentControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private TournamentService tournamentService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @Test
    @WithMockUser(roles = "USER")
    void createTournament_ReturnsForbidden_WhenRoleIsUser() throws Exception {
        CreateTournamentRequest request = new CreateTournamentRequest("Torneo Test", "2025/2026");

        mockMvc.perform(post("/api/tournaments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void createTournament_ReturnsCreated_WhenRoleIsOrganizer() throws Exception {
        CreateTournamentRequest request = new CreateTournamentRequest("Torneo Test", "2025/2026");

        Tournament created = new Tournament();
        created.setId(1L);
        created.setName("Torneo Test");
        created.setSeason("2025/2026");
        created.setStatus(TournamentStatus.DRAFT);

        when(tournamentService.createTournament(any(Tournament.class))).thenReturn(created);

        mockMvc.perform(post("/api/tournaments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getAllTournaments_ReturnsOk_ForAnyAuthenticatedUser() throws Exception {
        when(tournamentService.getAllTournaments()).thenReturn(List.of());

        mockMvc.perform(get("/api/tournaments"))
                .andExpect(status().isOk());
    }
}