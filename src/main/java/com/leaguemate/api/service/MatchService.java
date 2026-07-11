package com.leaguemate.api.service;

import com.leaguemate.api.entity.Match;
import java.util.List;

public interface MatchService {
    Match updateMatchResult(Long id, Integer homeScore, Integer awayScore);
    List<Match> getMatchesByRound(Long roundId);
}