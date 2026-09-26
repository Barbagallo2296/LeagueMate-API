package com.leaguemate.api.controller;

import com.leaguemate.api.dto.*;
import com.leaguemate.api.entity.Tournament;
import com.leaguemate.api.entity.TournamentStatus;
import com.leaguemate.api.mapper.TournamentMapper;
import com.leaguemate.api.mapper.UserMapper;
import com.leaguemate.api.service.TournamentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/tournaments")
@RequiredArgsConstructor
public class TournamentController {

    private static final String OWNER_OR_ADMIN =
            "hasRole('ADMIN') or (hasRole('ORGANIZER') and @tournamentSecurity.isOrganizer(#tournamentId, authentication))";

    private static final Set<String> SORTABLE = Set.of("id", "name", "season", "status", "createdAt");

    private final TournamentService tournamentService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
    public ResponseEntity<TournamentResponse> createTournament(
            @Valid @RequestBody CreateTournamentRequest request,
            Authentication authentication
    ) {
        Tournament tournament = new Tournament();
        tournament.setName(request.name());
        tournament.setSeason(request.season());
        tournament.setDoubleRoundRobin(Boolean.TRUE.equals(request.doubleRoundRobin()));

        Tournament created = tournamentService.createTournament(tournament, authentication.getName());
        return new ResponseEntity<>(TournamentMapper.toResponse(created), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TournamentResponse> getTournamentById(@PathVariable Long id) {
        return ResponseEntity.ok(TournamentMapper.toResponse(tournamentService.getTournamentById(id)));
    }

    @GetMapping
    public ResponseEntity<PagedModel<TournamentResponse>> getAllTournaments(
            @PageableDefault(size = 20, sort = "id") Pageable pageable
    ) {
        return ResponseEntity.ok(new PagedModel<>(
                tournamentService.getAllTournaments(SortWhitelist.check(pageable, SORTABLE)).map(TournamentMapper::toResponse)));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<PagedModel<TournamentResponse>> getTournamentsByStatus(
            @PathVariable TournamentStatus status,
            @PageableDefault(size = 20, sort = "id") Pageable pageable
    ) {
        return ResponseEntity.ok(new PagedModel<>(
                tournamentService.getTournamentsByStatus(status, SortWhitelist.check(pageable, SORTABLE)).map(TournamentMapper::toResponse)));
    }

    @PutMapping("/{tournamentId}")
    @PreAuthorize(OWNER_OR_ADMIN)
    public ResponseEntity<TournamentResponse> updateTournament(
            @PathVariable Long tournamentId,
            @Valid @RequestBody UpdateTournamentRequest request
    ) {
        Tournament updated = tournamentService.updateTournament(
                tournamentId, request.name(), request.season(), request.pointsForWin(), request.pointsForDraw());
        return ResponseEntity.ok(TournamentMapper.toResponse(updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteTournament(@PathVariable Long id) {
        tournamentService.deleteTournament(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{tournamentId}/register-team/{teamId}")
    @PreAuthorize(OWNER_OR_ADMIN)
    public ResponseEntity<Void> registerTeamToTournament(
            @PathVariable Long tournamentId,
            @PathVariable Long teamId
    ) {
        tournamentService.registerTeamToTournament(tournamentId, teamId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/{tournamentId}/generate-rounds")
    @PreAuthorize(OWNER_OR_ADMIN)
    public ResponseEntity<Void> generateRounds(@PathVariable Long tournamentId) {
        tournamentService.generateRounds(tournamentId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{tournamentId}/complete")
    @PreAuthorize(OWNER_OR_ADMIN)
    public ResponseEntity<TournamentResponse> completeTournament(@PathVariable Long tournamentId) {
        return ResponseEntity.ok(TournamentMapper.toResponse(tournamentService.completeTournament(tournamentId)));
    }

    @GetMapping("/{tournamentId}/standings")
    public ResponseEntity<List<StandingEntry>> calculateStandings(@PathVariable Long tournamentId) {
        return ResponseEntity.ok(tournamentService.calculateStandings(tournamentId));
    }

    @GetMapping("/{tournamentId}/stats")
    public ResponseEntity<TournamentStatsResponse> getTournamentStats(@PathVariable Long tournamentId) {
        return ResponseEntity.ok(tournamentService.getTournamentStats(tournamentId));
    }


    @PostMapping("/{tournamentId}/organizers/{userId}")
    @PreAuthorize(OWNER_OR_ADMIN)
    public ResponseEntity<Void> addOrganizer(
            @PathVariable Long tournamentId,
            @PathVariable Long userId
    ) {
        tournamentService.addOrganizer(tournamentId, userId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/{tournamentId}/organizers/{userId}")
    @PreAuthorize(OWNER_OR_ADMIN)
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
                .map(UserMapper::toPublicResponse)
                .toList();
        return ResponseEntity.ok(organizers);
    }
}
