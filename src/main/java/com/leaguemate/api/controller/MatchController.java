package com.leaguemate.api.controller;

import com.leaguemate.api.dto.UpdateMatchResultRequest;
import com.leaguemate.api.entity.Match;
import com.leaguemate.api.service.MatchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/matches")
@RequiredArgsConstructor
public class MatchController {

    private final MatchService matchService;

    @PutMapping("/{id}/result")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
    public ResponseEntity<Match> updateMatchResult(
            @PathVariable Long id,
            @Valid @RequestBody UpdateMatchResultRequest request
    ) {
        Match updatedMatch = matchService.updateMatchResult(id, request.homeScore(), request.awayScore());
        return ResponseEntity.ok(updatedMatch);
    }

    @GetMapping("/round/{roundId}")
    public ResponseEntity<List<Match>> getMatchesByRound(@PathVariable Long roundId) {
        return ResponseEntity.ok(matchService.getMatchesByRound(roundId));
    }
}