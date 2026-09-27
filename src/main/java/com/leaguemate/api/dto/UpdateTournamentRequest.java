package com.leaguemate.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateTournamentRequest(
        @NotBlank(message = "Tournament name is required")
        @Size(max = 100, message = "Tournament name cannot exceed 100 characters")
        String name,

        @NotBlank(message = "Season is required")
        @Size(max = 20, message = "Season cannot exceed 20 characters")
        String season,

        @Min(value = 1, message = "Points for win must be at least 1")
        int pointsForWin,

        @Min(value = 0, message = "Points for draw cannot be negative")
        int pointsForDraw
) {}