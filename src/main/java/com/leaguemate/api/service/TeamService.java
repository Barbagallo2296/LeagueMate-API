package com.leaguemate.api.service;

import com.leaguemate.api.entity.Team;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TeamService {
    Team createTeam(Team team);
    Team getTeamById(Long id);
    Page<Team> getAllTeams(Pageable pageable);
    Team updateTeam(Long id, String name, String logoUrl);
    void deleteTeam(Long id);
}