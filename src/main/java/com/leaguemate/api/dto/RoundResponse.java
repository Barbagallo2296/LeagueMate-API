package com.leaguemate.api.dto;

import java.util.List;

public record RoundResponse(
        Long id,
        int roundNumber,
        List<MatchResponse> matches
) {}
