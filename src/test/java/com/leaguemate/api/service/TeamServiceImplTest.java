package com.leaguemate.api.service;

import com.leaguemate.api.entity.Team;
import com.leaguemate.api.entity.TournamentRegistration;
import com.leaguemate.api.exception.ResourceConflictException;
import com.leaguemate.api.exception.ResourceNotFoundException;
import com.leaguemate.api.repository.TeamRepository;
import com.leaguemate.api.service.impl.TeamServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeamServiceImplTest {

    @Mock
    private TeamRepository teamRepository;

    @InjectMocks
    private TeamServiceImpl teamService;

    private Team team;

    @BeforeEach
    void setUp() {
        team = new Team();
        team.setId(1L);
        team.setName("Straw Hat FC");
        team.setLogoUrl("https://images.com/luffy.png");
        team.setRegistrations(new ArrayList<>());
    }

    @Test
    void createTeam_Success() {
        when(teamRepository.findByName("Straw Hat FC")).thenReturn(Optional.empty());
        when(teamRepository.save(any(Team.class))).thenReturn(team);

        Team created = teamService.createTeam(team);

        assertNotNull(created);
        assertEquals("Straw Hat FC", created.getName());
        verify(teamRepository, times(1)).save(team);
    }

    @Test
    void createTeam_ThrowsConflict_WhenNameExists() {
        when(teamRepository.findByName("Straw Hat FC")).thenReturn(Optional.of(team));

        assertThrows(ResourceConflictException.class, () -> teamService.createTeam(team));
        verify(teamRepository, never()).save(any(Team.class));
    }

    @Test
    void getTeamById_Success() {
        when(teamRepository.findById(1L)).thenReturn(Optional.of(team));

        Team found = teamService.getTeamById(1L);

        assertNotNull(found);
        assertEquals("Straw Hat FC", found.getName());
    }

    @Test
    void getTeamById_ThrowsNotFound_WhenDoesNotExist() {
        when(teamRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> teamService.getTeamById(99L));
    }

    @Test
    void getAllTeams_ReturnsList() {
        Team t2 = new Team();
        t2.setId(2L);
        t2.setName("Heart Pirates");

        when(teamRepository.findAll()).thenReturn(List.of(team, t2));

        List<Team> teams = teamService.getAllTeams();

        assertEquals(2, teams.size());
    }

    @Test
    void updateTeam_Success() {
        when(teamRepository.findById(1L)).thenReturn(Optional.of(team));
        when(teamRepository.findByName("Nuovo Nome")).thenReturn(Optional.empty());
        when(teamRepository.save(any(Team.class))).thenReturn(team);

        Team updated = teamService.updateTeam(1L, "Nuovo Nome", "https://new.png");

        assertEquals("Nuovo Nome", updated.getName());
        assertEquals("https://new.png", updated.getLogoUrl());
        verify(teamRepository, times(1)).save(team);
    }

    @Test
    void updateTeam_ThrowsConflict_WhenNameTakenByAnotherTeam() {
        Team otherTeam = new Team();
        otherTeam.setId(2L);
        otherTeam.setName("Heart Pirates");

        when(teamRepository.findById(1L)).thenReturn(Optional.of(team));
        when(teamRepository.findByName("Heart Pirates")).thenReturn(Optional.of(otherTeam));

        assertThrows(ResourceConflictException.class,
                () -> teamService.updateTeam(1L, "Heart Pirates", null));
        verify(teamRepository, never()).save(any(Team.class));
    }

    @Test
    void deleteTeam_Success() {
        when(teamRepository.findById(1L)).thenReturn(Optional.of(team));

        teamService.deleteTeam(1L);

        verify(teamRepository, times(1)).delete(team);
    }

    @Test
    void deleteTeam_ThrowsConflict_WhenTeamIsRegistered() {
        team.getRegistrations().add(new TournamentRegistration());
        when(teamRepository.findById(1L)).thenReturn(Optional.of(team));

        assertThrows(ResourceConflictException.class, () -> teamService.deleteTeam(1L));
        verify(teamRepository, never()).delete(any(Team.class));
    }
}