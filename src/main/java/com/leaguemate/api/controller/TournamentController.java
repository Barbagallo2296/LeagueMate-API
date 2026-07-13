package com.leaguemate.api.controller;

import com.leaguemate.api.dto.*;
import com.leaguemate.api.entity.Tournament;
import com.leaguemate.api.entity.TournamentStatus;
import com.leaguemate.api.entity.User;
import com.leaguemate.api.service.TournamentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tournaments")
@RequiredArgsConstructor
public class TournamentController {

    private final TournamentService tournamentService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
    public ResponseEntity<TournamentResponse> createTournament(@Valid @RequestBody CreateTournamentRequest request) {
        Tournament tournament = new Tournament();
        tournament.setName(request.name());
        tournament.setSeason(request.season());

        Tournament created = tournamentService.createTournament(tournament);
        return new ResponseEntity<>(toResponse(created), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TournamentResponse> getTournamentById(@PathVariable Long id) {
        return ResponseEntity.ok(toResponse(tournamentService.getTournamentById(id)));
    }

    @GetMapping
    public ResponseEntity<List<TournamentResponse>> getAllTournaments() {
        List<TournamentResponse> tournaments = tournamentService.getAllTournaments().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(tournaments);
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<TournamentResponse>> getTournamentsByStatus(@PathVariable TournamentStatus status) {
        List<TournamentResponse> tournaments = tournamentService.getTournamentsByStatus(status).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(tournaments);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
    public ResponseEntity<TournamentResponse> updateTournament(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTournamentRequest request
    ) {
        Tournament updated = tournamentService.updateTournament(
                id, request.name(), request.season(), request.pointsForWin(), request.pointsForDraw());
        return ResponseEntity.ok(toResponse(updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteTournament(@PathVariable Long id) {
        tournamentService.deleteTournament(id);
        return ResponseEntity.noContent().build();
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
        return ResponseEntity.ok(tournamentService.calculateStandings(tournamentId));
    }

    @GetMapping("/{tournamentId}/stats")
    public ResponseEntity<TournamentStatsResponse> getTournamentStats(@PathVariable Long tournamentId) {
        return ResponseEntity.ok(tournamentService.getTournamentStats(tournamentId));
    }

    // --- Co-organizzatori (@ManyToMany) ---

    @PostMapping("/{tournamentId}/organizers/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
    public ResponseEntity<Void> addOrganizer(
            @PathVariable Long tournamentId,
            @PathVariable Long userId
    ) {
        tournamentService.addOrganizer(tournamentId, userId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/{tournamentId}/organizers/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
    public ResponseEntity<Void> removeOrganizer(
            @PathVariable Long tournamentId,
            @PathVariable Long userId
    ) {
        tournamentService.removeOrganizer(tournamentId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{tournamentId}/organizers")
    public ResponseEntity<List<UserResponse>> getOrganizers(@PathVariable Long tournamentId) {
        List<UserResponse> organizers = tournamentService.getOrganizers(tournamentId).stream()
                .map(this::toUserResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(organizers);
    }

    private TournamentResponse toResponse(Tournament t) {
        return new TournamentResponse(
                t.getId(),
                t.getName(),
                t.getSeason(),
                t.getStatus() != null ? t.getStatus().name() : null,
                t.getPointsForWin(),
                t.getPointsForDraw(),
                t.getCreatedAt()
        );
    }

    private UserResponse toUserResponse(User u) {
        return new UserResponse(
                u.getId(),
                u.getEmail(),
                u.getUsername(),
                u.getFirstName(),
                u.getLastName(),
                u.getRole().name()
        );
    }
}