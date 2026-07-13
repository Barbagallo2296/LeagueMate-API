package com.leaguemate.api.service;

import com.leaguemate.api.entity.Tournament;
import com.leaguemate.api.entity.TournamentStatus;
import com.leaguemate.api.exception.ResourceNotFoundException;
import com.leaguemate.api.repository.TournamentRepository;
import com.leaguemate.api.repository.TournamentRegistrationRepository;
import com.leaguemate.api.repository.TeamRepository;
import com.leaguemate.api.repository.MatchRepository;
import com.leaguemate.api.service.impl.TournamentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TournamentServiceImplTest {

    @Mock
    private TournamentRepository tournamentRepository;
    @Mock
    private TournamentRegistrationRepository registrationRepository;
    @Mock
    private TeamRepository teamRepository;
    @Mock
    private MatchRepository matchRepository;

    @InjectMocks
    private TournamentServiceImpl tournamentService;

    private Tournament tournament;

    @BeforeEach
    void setUp() {
        tournament = new Tournament();
        tournament.setId(1L);
        tournament.setName("Champions League");
        tournament.setSeason("2026/2027");
    }

    @Test
    void createTournament_Success() {
        when(tournamentRepository.save(any(Tournament.class))).thenReturn(tournament);

        Tournament created = tournamentService.createTournament(tournament);

        assertNotNull(created);
        assertEquals(TournamentStatus.DRAFT, created.getStatus());
        verify(tournamentRepository, times(1)).save(tournament);
    }

    @Test
    void getTournamentById_Success() {
        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(tournament));

        Tournament found = tournamentService.getTournamentById(1L);

        assertNotNull(found);
        assertEquals("Champions League", found.getName());
    }

    @Test
    void getTournamentById_ThrowsNotFound_WhenDoesNotExist() {
        when(tournamentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> tournamentService.getTournamentById(99L));
    }

    @Test
    void getAllTournaments_ReturnsList() {
        Tournament t2 = new Tournament();
        t2.setId(2L);
        t2.setName("Europa League");
        List<Tournament> mockList = List.of(tournament, t2);

        when(tournamentRepository.findAll()).thenReturn(mockList);

        List<Tournament> result = tournamentService.getAllTournaments();

        assertNotNull(result);
        assertEquals(2, result.size());
        verify(tournamentRepository, times(1)).findAll();
    }

    @Test
    void getTournamentsByStatus_ReturnsFilteredList() {
        TournamentStatus targetStatus = TournamentStatus.ACTIVE;
        tournament.setStatus(targetStatus);
        List<Tournament> mockList = List.of(tournament);

        when(tournamentRepository.findByStatus(targetStatus)).thenReturn(mockList);

        List<Tournament> result = tournamentService.getTournamentsByStatus(targetStatus);

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals(targetStatus, result.get(0).getStatus());
        verify(tournamentRepository, times(1)).findByStatus(targetStatus);
    }
}