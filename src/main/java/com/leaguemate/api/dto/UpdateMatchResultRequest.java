package com.leaguemate.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record UpdateMatchResultRequest(
        @NotNull(message = "Home score is required")
        @PositiveOrZero(message = "Score cannot be negative")
        Integer homeScore,

        @NotNull(message = "Away score is required")
        @PositiveOrZero(message = "Score cannot be negative")
        Integer awayScore
) {}