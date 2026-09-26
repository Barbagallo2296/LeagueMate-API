package com.leaguemate.api.controller;

import com.leaguemate.api.dto.CreateTeamRequest;
import com.leaguemate.api.dto.TeamResponse;
import com.leaguemate.api.dto.UpdateTeamRequest;
import com.leaguemate.api.entity.Team;
import com.leaguemate.api.mapper.TeamMapper;
import com.leaguemate.api.service.TeamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class TeamController {

    private static final Set<String> SORTABLE = Set.of("id", "name", "createdAt");

    private final TeamService teamService;

    @PostMapping
    public ResponseEntity<TeamResponse> createTeam(@Valid @RequestBody CreateTeamRequest request) {
        Team team = new Team();
        team.setName(request.name());
        team.setLogoUrl(request.logoUrl());

        Team saved = teamService.createTeam(team);
        return new ResponseEntity<>(TeamMapper.toResponse(saved), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TeamResponse> getTeamById(@PathVariable Long id) {
        return ResponseEntity.ok(TeamMapper.toResponse(teamService.getTeamById(id)));
    }

    @GetMapping
    public ResponseEntity<PagedModel<TeamResponse>> getAllTeams(
            @PageableDefault(size = 20, sort = "id") Pageable pageable
    ) {
        return ResponseEntity.ok(new PagedModel<>(teamService.getAllTeams(SortWhitelist.check(pageable, SORTABLE)).map(TeamMapper::toResponse)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
    public ResponseEntity<TeamResponse> updateTeam(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTeamRequest request
    ) {
        Team updated = teamService.updateTeam(id, request.name(), request.logoUrl());
        return ResponseEntity.ok(TeamMapper.toResponse(updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteTeam(@PathVariable Long id) {
        teamService.deleteTeam(id);
        return ResponseEntity.noContent().build();
    }
}
