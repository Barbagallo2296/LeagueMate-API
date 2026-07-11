package com.leaguemate.api.controller;

import com.leaguemate.api.dto.CreateTeamRequest;
import com.leaguemate.api.entity.Team;
import com.leaguemate.api.service.TeamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;

    @PostMapping
    public ResponseEntity<Team> createTeam(@Valid @RequestBody CreateTeamRequest request) {
        Team team = new Team();
        team.setName(request.name());
        team.setLogoUrl(request.logoUrl());

        Team savedTeam = teamService.createTeam(team);
        return new ResponseEntity<>(savedTeam, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Team> getTeamById(@PathVariable Long id) {
        return ResponseEntity.ok(teamService.getTeamById(id));
    }

    @GetMapping
    public ResponseEntity<List<Team>> getAllTeams() {
        return ResponseEntity.ok(teamService.getAllTeams());
    }
}