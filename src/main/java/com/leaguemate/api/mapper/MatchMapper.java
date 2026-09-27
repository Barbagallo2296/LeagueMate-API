package com.leaguemate.api.mapper;

import com.leaguemate.api.dto.MatchResponse;
import com.leaguemate.api.dto.RoundResponse;
import com.leaguemate.api.entity.Match;
import com.leaguemate.api.entity.Round;

public final class MatchMapper {

    private MatchMapper() {
    }

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

    public static RoundResponse toRoundResponse(Round round) {
        return new RoundResponse(
                round.getId(),
                round.getRoundNumber(),
                round.getMatches().stream().map(MatchMapper::toResponse).toList()
        );
    }
}
