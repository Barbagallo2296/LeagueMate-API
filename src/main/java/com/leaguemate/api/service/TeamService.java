package com.leaguemate.api.service;

import com.leaguemate.api.entity.Team;

import java.util.List;

public interface TeamService {
    Team createTeam(Team team);
    Team getTeamById(Long id);
    List<Team> getAllTeams();
    Team updateTeam(Long id, String name, String logoUrl);
    void deleteTeam(Long id);
}