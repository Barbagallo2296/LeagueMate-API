package com.leaguemate.api.service;

import com.leaguemate.api.entity.Match;
import com.leaguemate.api.entity.MatchStatus;
import com.leaguemate.api.entity.Round;
import com.leaguemate.api.entity.Team;
import com.leaguemate.api.exception.ResourceNotFoundException;
import com.leaguemate.api.repository.MatchRepository;
import com.leaguemate.api.service.impl.MatchServiceImpl;
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
class MatchServiceImplTest {

    @Mock
    private MatchRepository matchRepository;

    @InjectMocks
    private MatchServiceImpl matchService;

    private Match match;

    @BeforeEach
    void setUp() {
        Team home = new Team();
        home.setId(1L);
        home.setName("Straw Hat FC");

        Team away = new Team();
        away.setId(2L);
        away.setName("Heart Pirates");

        Round round = new Round();
        round.setId(1L);
        round.setRoundNumber(1);

        match = new Match();
        match.setId(1L);
        match.setHomeTeam(home);
        match.setAwayTeam(away);
        match.setRound(round);
        match.setStatus(MatchStatus.SCHEDULED);
    }

    @Test
    void updateMatchResult_Success() {
        when(matchRepository.findByIdWithTeams(1L)).thenReturn(Optional.of(match));
        when(matchRepository.save(any(Match.class))).thenReturn(match);

        Match updated = matchService.updateMatchResult(1L, 3, 1);

        assertEquals(3, updated.getHomeScore());
        assertEquals(1, updated.getAwayScore());
        assertEquals(MatchStatus.COMPLETED, updated.getStatus());
        verify(matchRepository, times(1)).save(match);
    }

    @Test
    void updateMatchResult_ThrowsNotFound_WhenMatchDoesNotExist() {
        when(matchRepository.findByIdWithTeams(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> matchService.updateMatchResult(99L, 1, 0));
        verify(matchRepository, never()).save(any(Match.class));
    }

    @Test
    void updateMatchResult_AcceptsGoallessDraw() {
        when(matchRepository.findByIdWithTeams(1L)).thenReturn(Optional.of(match));
        when(matchRepository.save(any(Match.class))).thenReturn(match);

        Match updated = matchService.updateMatchResult(1L, 0, 0);

        assertEquals(0, updated.getHomeScore());
        assertEquals(0, updated.getAwayScore());
        assertEquals(MatchStatus.COMPLETED, updated.getStatus());
    }

    @Test
    void updateMatchResult_LoadsTeamsEagerly() {
        when(matchRepository.findByIdWithTeams(1L)).thenReturn(Optional.of(match));
        when(matchRepository.save(any(Match.class))).thenReturn(match);

        Match updated = matchService.updateMatchResult(1L, 2, 2);

        assertEquals("Straw Hat FC", updated.getHomeTeam().getName());
        assertEquals("Heart Pirates", updated.getAwayTeam().getName());
        assertEquals(1, updated.getRound().getRoundNumber());
        verify(matchRepository, never()).findById(anyLong());
    }

    @Test
    void getMatchesByRound_ReturnsList() {
        when(matchRepository.findByRoundIdWithTeams(1L)).thenReturn(List.of(match));

        List<Match> result = matchService.getMatchesByRound(1L);

        assertEquals(1, result.size());
        assertEquals("Straw Hat FC", result.get(0).getHomeTeam().getName());
        verify(matchRepository, times(1)).findByRoundIdWithTeams(1L);
    }
}