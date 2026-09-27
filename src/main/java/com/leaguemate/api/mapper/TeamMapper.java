package com.leaguemate.api.mapper;

import com.leaguemate.api.dto.TeamMemberResponse;
import com.leaguemate.api.dto.TeamResponse;
import com.leaguemate.api.entity.Team;
import com.leaguemate.api.entity.TeamMember;

public final class TeamMapper {

    private TeamMapper() {
    }

    public static TeamResponse toResponse(Team team) {
        return new TeamResponse(
                team.getId(),
                team.getName(),
                team.getLogoUrl(),
                team.getOwner() != null ? team.getOwner().getId() : null,
                team.getCreatedAt()
        );
    }

    public static TeamMemberResponse toMemberResponse(TeamMember member) {
        return new TeamMemberResponse(
                member.getId(),
                member.getUser().getId(),
                member.getUser().getUsername(),
                member.getUser().getFirstName(),
                member.getUser().getLastName(),
                member.getTeam().getId(),
                member.getTeam().getName(),
                member.getTeamRole().name(),
                member.getJoinedAt()
        );
    }
}
