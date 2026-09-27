package com.leaguemate.api.security;

import com.leaguemate.api.repository.MatchRepository;
import com.leaguemate.api.repository.TournamentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("tournamentSecurity")
@RequiredArgsConstructor
public class TournamentSecurity {

    private final TournamentRepository tournamentRepository;
    private final MatchRepository matchRepository;

    public boolean isOrganizer(Long tournamentId, Authentication authentication) {
        return authentication != null
                && tournamentRepository.isOrganizer(tournamentId, authentication.getName());
    }

    public boolean isMatchOrganizer(Long matchId, Authentication authentication) {
        return authentication != null
                && matchRepository.isTournamentOrganizer(matchId, authentication.getName());
    }
}
