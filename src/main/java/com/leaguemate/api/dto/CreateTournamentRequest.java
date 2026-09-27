package com.leaguemate.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTournamentRequest(
        @NotBlank(message = "Tournament name is required")
        @Size(max = 100, message = "Tournament name cannot exceed 100 characters")
        String name,

        @NotBlank(message = "Season is required")
        @Size(max = 20, message = "Season cannot exceed 20 characters")
        String season,

        Boolean doubleRoundRobin
) {
    public CreateTournamentRequest(String name, String season) {
        this(name, season, null);
    }
}