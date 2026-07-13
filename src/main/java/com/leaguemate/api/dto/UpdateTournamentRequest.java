package com.leaguemate.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record UpdateTournamentRequest(
        @NotBlank(message = "Tournament name is required")
        String name,

        @NotBlank(message = "Season is required")
        String season,

        @Min(value = 1, message = "Points for win must be at least 1")
        int pointsForWin,

        @Min(value = 0, message = "Points for draw cannot be negative")
        int pointsForDraw
) {}