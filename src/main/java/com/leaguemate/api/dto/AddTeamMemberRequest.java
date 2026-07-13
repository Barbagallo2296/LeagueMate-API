package com.leaguemate.api.dto;

import com.leaguemate.api.entity.TeamRole;
import jakarta.validation.constraints.NotNull;

public record AddTeamMemberRequest(
        @NotNull(message = "User ID is required")
        Long userId,

        @NotNull(message = "Team role is required")
        TeamRole teamRole
) {}