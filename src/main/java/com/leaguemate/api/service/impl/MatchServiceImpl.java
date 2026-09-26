package com.leaguemate.api.service.impl;

import com.leaguemate.api.entity.Match;
import com.leaguemate.api.entity.MatchStatus;
import com.leaguemate.api.entity.TournamentStatus;
import com.leaguemate.api.exception.ResourceConflictException;
import com.leaguemate.api.exception.ResourceNotFoundException;
import com.leaguemate.api.repository.MatchRepository;
import com.leaguemate.api.service.MatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MatchServiceImpl implements MatchService {

    private final MatchRepository matchRepository;

    @Override
    @Transactional
    public Match updateMatchResult(Long id, Integer homeScore, Integer awayScore) {
        Match match = matchRepository.findByIdWithTeams(id)
                .orElseThrow(() -> new ResourceNotFoundException("Match not found with id: " + id));

        TournamentStatus tournamentStatus = match.getRound().getTournament().getStatus();
        if (tournamentStatus != TournamentStatus.ACTIVE) {
            throw new ResourceConflictException(
                    "Match results can only be updated while the tournament is ACTIVE. Current status: "
                            + tournamentStatus);
        }

        match.setHomeScore(homeScore);
        match.setAwayScore(awayScore);
        match.setStatus(MatchStatus.COMPLETED);

        return matchRepository.save(match);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Match> getMatchesByRound(Long roundId) {
        return matchRepository.findByRoundIdWithTeams(roundId);
    }
}