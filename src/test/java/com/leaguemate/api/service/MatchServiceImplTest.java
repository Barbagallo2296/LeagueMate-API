package com.leaguemate.api.service;

import com.leaguemate.api.entity.Match;
import com.leaguemate.api.entity.MatchStatus;
import com.leaguemate.api.exception.ResourceNotFoundException;
import com.leaguemate.api.repository.MatchRepository;
import com.leaguemate.api.service.impl.MatchServiceImpl;
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
class MatchServiceImplTest {

    @Mock
    private MatchRepository matchRepository;

    @InjectMocks
    private MatchServiceImpl matchService;

    private Match match;

    @BeforeEach
    void setUp() {
        match = new Match();
        match.setId(1L);
        match.setStatus(MatchStatus.SCHEDULED); // Corretto da PENDING a SCHEDULED
    }

    @Test
    void updateMatchResult_Success() {
        when(matchRepository.findById(1L)).thenReturn(Optional.of(match));
        when(matchRepository.save(any(Match.class))).thenReturn(match);

        Match updated = matchService.updateMatchResult(1L, 3, 1);

        assertNotNull(updated);
        assertEquals(3, updated.getHomeScore());
        assertEquals(1, updated.getAwayScore());
        assertEquals(MatchStatus.COMPLETED, updated.getStatus());
        verify(matchRepository, times(1)).save(match);
    }

    @Test
    void updateMatchResult_ThrowsNotFound_WhenMatchDoesNotExist() {
        when(matchRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> matchService.updateMatchResult(99L, 2, 2));
        verify(matchRepository, never()).save(any(Match.class));
    }
}