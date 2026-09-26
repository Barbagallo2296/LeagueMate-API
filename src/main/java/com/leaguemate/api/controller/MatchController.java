package com.leaguemate.api.controller;

import com.leaguemate.api.dto.MatchResponse;
import com.leaguemate.api.dto.UpdateMatchResultRequest;
import com.leaguemate.api.entity.Match;
import com.leaguemate.api.mapper.MatchMapper;
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
    @PreAuthorize("hasRole('ADMIN') or (hasRole('ORGANIZER') and @tournamentSecurity.isMatchOrganizer(#id, authentication))")
    public ResponseEntity<MatchResponse> updateMatchResult(
            @PathVariable Long id,
            @Valid @RequestBody UpdateMatchResultRequest request
    ) {
        Match updated = matchService.updateMatchResult(id, request.homeScore(), request.awayScore());
        return ResponseEntity.ok(MatchMapper.toResponse(updated));
    }

    @GetMapping("/round/{roundId}")
    public ResponseEntity<List<MatchResponse>> getMatchesByRound(@PathVariable Long roundId) {
        List<MatchResponse> matches = matchService.getMatchesByRound(roundId).stream()
                .map(MatchMapper::toResponse)
                .toList();
        return ResponseEntity.ok(matches);
    }
}
