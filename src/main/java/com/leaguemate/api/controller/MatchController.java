package com.leaguemate.api.controller;

import com.leaguemate.api.dto.MatchResponse;
import com.leaguemate.api.dto.UpdateMatchResultRequest;
import com.leaguemate.api.entity.Match;
import com.leaguemate.api.service.MatchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/matches")
@RequiredArgsConstructor
public class MatchController {

    private final MatchService matchService;

    @PutMapping("/{id}/result")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
    public ResponseEntity<MatchResponse> updateMatchResult(
            @PathVariable Long id,
            @Valid @RequestBody UpdateMatchResultRequest request
    ) {
        Match updated = matchService.updateMatchResult(id, request.homeScore(), request.awayScore());
        return ResponseEntity.ok(toResponse(updated));
    }

    @GetMapping("/round/{roundId}")
    public ResponseEntity<List<MatchResponse>> getMatchesByRound(@PathVariable Long roundId) {
        List<MatchResponse> matches = matchService.getMatchesByRound(roundId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(matches);
    }

    private MatchResponse toResponse(Match m) {
        return new MatchResponse(
                m.getId(),
                m.getHomeTeam().getId(),
                m.getHomeTeam().getName(),
                m.getAwayTeam().getId(),
                m.getAwayTeam().getName(),
                m.getHomeScore(),
                m.getAwayScore(),
                m.getStatus().name(),
                m.getRound().getRoundNumber()
        );
    }
}