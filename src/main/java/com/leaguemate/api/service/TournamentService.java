package com.leaguemate.api.service;

import com.leaguemate.api.dto.StandingEntry;
import com.leaguemate.api.dto.TournamentStatsResponse;
import com.leaguemate.api.entity.Round;
import com.leaguemate.api.entity.Team;
import com.leaguemate.api.entity.Tournament;
import com.leaguemate.api.entity.TournamentRegistration;
import com.leaguemate.api.entity.TournamentStatus;
import com.leaguemate.api.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface TournamentService {

    Tournament createTournament(Tournament tournament, String creatorUsername);
    Tournament getTournamentById(Long id);
    Page<Tournament> getAllTournaments(Pageable pageable);
    Page<Tournament> getTournamentsByStatus(TournamentStatus status, Pageable pageable);
    Tournament updateTournament(Long id, String name, String season, int pointsForWin, int pointsForDraw);
    void deleteTournament(Long id);

    TournamentRegistration registerTeamToTournament(Long tournamentId, Long teamId);

    List<Round> generateRounds(Long tournamentId);

    List<Round> getRounds(Long tournamentId);

    List<Team> getRegisteredTeams(Long tournamentId);

    List<Tournament> getTournamentsOrganizedBy(Long userId);

    Tournament completeTournament(Long tournamentId);

    List<StandingEntry> calculateStandings(Long tournamentId);

    TournamentStatsResponse getTournamentStats(Long tournamentId);

    void addOrganizer(Long tournamentId, Long userId);
    void removeOrganizer(Long tournamentId, Long userId);
    List<User> getOrganizers(Long tournamentId);
}