package com.leaguemate.api.mapper;

import com.leaguemate.api.dto.TournamentResponse;
import com.leaguemate.api.entity.Tournament;

public final class TournamentMapper {

    private TournamentMapper() {
    }

    public static TournamentResponse toResponse(Tournament tournament) {
        return new TournamentResponse(
                tournament.getId(),
                tournament.getName(),
                tournament.getSeason(),
                tournament.getStatus() != null ? tournament.getStatus().name() : null,
                tournament.getPointsForWin(),
                tournament.getPointsForDraw(),
                tournament.isDoubleRoundRobin(),
                tournament.getCreatedAt()
        );
    }
}
