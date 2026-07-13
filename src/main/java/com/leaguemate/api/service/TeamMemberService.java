package com.leaguemate.api.service;

import com.leaguemate.api.dto.TeamMemberResponse;
import com.leaguemate.api.entity.TeamRole;

import java.util.List;

public interface TeamMemberService {
    TeamMemberResponse addMemberToTeam(Long teamId, Long userId, TeamRole teamRole);
    List<TeamMemberResponse> getMembersByTeam(Long teamId);
    void removeMemberFromTeam(Long memberId);
}