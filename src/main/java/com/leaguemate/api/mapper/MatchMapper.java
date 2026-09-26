package com.leaguemate.api.mapper;

import com.leaguemate.api.dto.MatchResponse;
import com.leaguemate.api.entity.Match;

public final class MatchMapper {

    private MatchMapper() {
    }

    /** Legge squadre e giornata della partita: vanno caricate con JOIN FETCH nel repository. */
    public static MatchResponse toResponse(Match match) {
        return new MatchResponse(
                match.getId(),
                match.getHomeTeam().getId(),
                match.getHomeTeam().getName(),
                match.getAwayTeam().getId(),
                match.getAwayTeam().getName(),
                match.getHomeScore(),
                match.getAwayScore(),
                match.getStatus().name(),
                match.getRound().getRoundNumber()
        );
    }
}
