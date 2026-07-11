package com.leaguemate.api.service.impl;

import com.leaguemate.api.entity.Team;
import com.leaguemate.api.repository.TeamRepository;
import com.leaguemate.api.service.TeamService;
import com.leaguemate.api.exception.ResourceNotFoundException;
import com.leaguemate.api.exception.ResourceConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TeamServiceImpl implements TeamService {

    private final TeamRepository teamRepository;

    @Override
    @Transactional
    public Team createTeam(Team team) {
        if (teamRepository.findByName(team.getName()).isPresent()) {
            throw new ResourceConflictException("Team name '" + team.getName() + "' is already taken");
        }
        team.setCreatedAt(LocalDateTime.now());
        return teamRepository.save(team);
    }

    @Override
    @Transactional(readOnly = true)
    public Team getTeamById(Long id) {
        return teamRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found with id: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Team> getAllTeams() {
        return teamRepository.findAll();
    }
}