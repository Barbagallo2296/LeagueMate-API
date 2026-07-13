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
import java.util.stream.Collectors;

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

        boolean alreadyMember = teamMemberRepository.findAll().stream()
                .anyMatch(m -> m.getTeam().getId().equals(teamId) && m.getUser().getId().equals(userId));

        if (alreadyMember) {
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
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found with id: " + teamId));

        return team.getMembers().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
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