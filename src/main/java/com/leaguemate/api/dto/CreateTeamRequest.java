package com.leaguemate.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTeamRequest(
        @NotBlank(message = "Team name is required")
        @Size(min = 2, max = 50, message = "Team name must be between 2 and 50 characters")
        String name,

        @Size(max = 255, message = "Logo URL cannot exceed 255 characters")
        String logoUrl
) {}