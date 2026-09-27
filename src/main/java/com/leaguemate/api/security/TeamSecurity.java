package com.leaguemate.api.security;

import com.leaguemate.api.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("teamSecurity")
@RequiredArgsConstructor
public class TeamSecurity {

    private final TeamRepository teamRepository;

    public boolean isOwner(Long teamId, Authentication authentication) {
        return authentication != null
                && teamRepository.existsByIdAndOwnerUsername(teamId, authentication.getName());
    }
}
