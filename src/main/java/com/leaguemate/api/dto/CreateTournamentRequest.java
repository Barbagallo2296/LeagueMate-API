package com.leaguemate.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateTournamentRequest(
        @NotBlank(message = "Tournament name is required")
        String name,

        @NotBlank(message = "Season is required")
        String season
) {}