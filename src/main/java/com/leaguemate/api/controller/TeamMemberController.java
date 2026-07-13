package com.leaguemate.api.controller;

import com.leaguemate.api.dto.AddTeamMemberRequest;
import com.leaguemate.api.dto.TeamMemberResponse;
import com.leaguemate.api.service.TeamMemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/teams/{teamId}/members")
@RequiredArgsConstructor
public class TeamMemberController {

    private final TeamMemberService teamMemberService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
    public ResponseEntity<TeamMemberResponse> addMember(
            @PathVariable Long teamId,
            @Valid @RequestBody AddTeamMemberRequest request
    ) {
        TeamMemberResponse response = teamMemberService.addMemberToTeam(
                teamId, request.userId(), request.teamRole());
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<TeamMemberResponse>> getMembers(@PathVariable Long teamId) {
        return ResponseEntity.ok(teamMemberService.getMembersByTeam(teamId));
    }

    @DeleteMapping("/{memberId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
    public ResponseEntity<Void> removeMember(
            @PathVariable Long teamId,
            @PathVariable Long memberId
    ) {
        teamMemberService.removeMemberFromTeam(memberId);
        return ResponseEntity.noContent().build();
    }
}