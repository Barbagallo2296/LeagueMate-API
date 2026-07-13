package com.leaguemate.api.service.impl;

import com.leaguemate.api.entity.Team;
import com.leaguemate.api.exception.ResourceConflictException;
import com.leaguemate.api.exception.ResourceNotFoundException;
import com.leaguemate.api.repository.TeamRepository;
import com.leaguemate.api.service.TeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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

    @Override
    @Transactional
    public Team updateTeam(Long id, String name, String logoUrl) {
        Team team = getTeamById(id);

        Optional<Team> existing = teamRepository.findByName(name);
        if (existing.isPresent() && !existing.get().getId().equals(id)) {
            throw new ResourceConflictException("Team name '" + name + "' is already taken");
        }

        team.setName(name);
        team.setLogoUrl(logoUrl);
        return teamRepository.save(team);
    }

    @Override
    @Transactional
    public void deleteTeam(Long id) {
        Team team = getTeamById(id);

        if (!team.getRegistrations().isEmpty()) {
            throw new ResourceConflictException(
                    "Cannot delete a team that is registered to one or more tournaments");
        }

        teamRepository.delete(team);
    }
}