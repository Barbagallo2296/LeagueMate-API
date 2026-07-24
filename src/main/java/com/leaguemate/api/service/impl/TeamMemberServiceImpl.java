package com.leaguemate.api.service.impl;

import com.leaguemate.api.dto.TeamMemberResponse;
import com.leaguemate.api.entity.Team;
import com.leaguemate.api.entity.TeamMember;
import com.leaguemate.api.entity.TeamRole;
import com.leaguemate.api.entity.User;
import com.leaguemate.api.exception.ResourceConflictException;
import com.leaguemate.api.exception.ResourceNotFoundException;
import com.leaguemate.api.repository.TeamMemberRepository;
import com.leaguemate.api.repository.TeamRepository;
import com.leaguemate.api.repository.UserRepository;
import com.leaguemate.api.service.TeamMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TeamMemberServiceImpl implements TeamMemberService {

    private final TeamMemberRepository teamMemberRepository;
    private final TeamRepository teamRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public TeamMemberResponse addMemberToTeam(Long teamId, Long userId, TeamRole teamRole) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found with id: " + teamId));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        if (teamMemberRepository.existsByTeamIdAndUserId(teamId, userId)) {
            throw new ResourceConflictException("User is already a member of this team");
        }

        TeamMember member = new TeamMember();
        member.setTeam(team);
        member.setUser(user);
        member.setTeamRole(teamRole);
        member.setJoinedAt(LocalDateTime.now());

        return toResponse(teamMemberRepository.save(member));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeamMemberResponse> getMembersByTeam(Long teamId) {
        if (!teamRepository.existsById(teamId)) {
            throw new ResourceNotFoundException("Team not found with id: " + teamId);
        }

        return teamMemberRepository.findByTeamIdWithUserAndTeam(teamId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public void removeMemberFromTeam(Long memberId) {
        TeamMember member = teamMemberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Team member not found with id: " + memberId));

        teamMemberRepository.delete(member);
    }

    private TeamMemberResponse toResponse(TeamMember member) {
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