package com.leaguemate.api.controller;

import com.leaguemate.api.dto.CreateTournamentRequest;
import com.leaguemate.api.dto.StandingEntry;
import com.leaguemate.api.entity.Tournament;
import com.leaguemate.api.entity.TournamentStatus;
import com.leaguemate.api.service.TournamentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tournaments")
@RequiredArgsConstructor
public class TournamentController {

    private final TournamentService tournamentService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
    public ResponseEntity<Tournament> createTournament(@Valid @RequestBody CreateTournamentRequest request) {
        Tournament tournament = new Tournament();
        tournament.setName(request.name());
        tournament.setSeason(request.season());
        Tournament created = tournamentService.createTournament(tournament);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Tournament> getTournamentById(@PathVariable Long id) {
        return ResponseEntity.ok(tournamentService.getTournamentById(id));
    }

    @GetMapping
    public ResponseEntity<List<Tournament>> getAllTournaments() {
        return ResponseEntity.ok(tournamentService.getAllTournaments());
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<Tournament>> getTournamentsByStatus(@PathVariable TournamentStatus status) {
        return ResponseEntity.ok(tournamentService.getTournamentsByStatus(status));
    }

    @PostMapping("/{tournamentId}/register-team/{teamId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
    public ResponseEntity<Void> registerTeamToTournament(
            @PathVariable Long tournamentId,
            @PathVariable Long teamId
    ) {
        tournamentService.registerTeamToTournament(tournamentId, teamId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/{tournamentId}/generate-rounds")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
    public ResponseEntity<Void> generateRounds(@PathVariable Long tournamentId) {
        tournamentService.generateRounds(tournamentId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{tournamentId}/standings")
    public ResponseEntity<List<StandingEntry>> calculateStandings(@PathVariable Long tournamentId) {
        List<StandingEntry> standings = tournamentService.calculateStandings(tournamentId);
        return ResponseEntity.ok(standings);
    }
}