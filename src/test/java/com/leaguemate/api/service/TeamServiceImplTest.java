package com.leaguemate.api.service;

import com.leaguemate.api.entity.Team;
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
        team.setName("Real Madrid");
        team.setLogoUrl("https://logo.com/real.png");
    }

    @Test
    void createTeam_Success() {
        when(teamRepository.findByName(team.getName())).thenReturn(Optional.empty());
        when(teamRepository.save(any(Team.class))).thenReturn(team);

        Team created = teamService.createTeam(team);

        assertNotNull(created);
        assertEquals("Real Madrid", created.getName());
        verify(teamRepository, times(1)).save(team);
    }

    @Test
    void createTeam_ThrowsConflict_WhenNameExists() {
        when(teamRepository.findByName(team.getName())).thenReturn(Optional.of(team));

        assertThrows(ResourceConflictException.class, () -> teamService.createTeam(team));
        verify(teamRepository, never()).save(any(Team.class));
    }

    @Test
    void getTeamById_Success() {
        when(teamRepository.findById(1L)).thenReturn(Optional.of(team));

        Team found = teamService.getTeamById(1L);

        assertNotNull(found);
        assertEquals(1L, found.getId());
    }

    @Test
    void getTeamById_ThrowsNotFound_WhenDoesNotExist() {
        when(teamRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> teamService.getTeamById(99L));
    }
}