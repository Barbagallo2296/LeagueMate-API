package com.leaguemate.api.service;

import com.leaguemate.api.dto.StandingEntry;
import com.leaguemate.api.dto.TournamentStatsResponse;
import com.leaguemate.api.entity.Round;
import com.leaguemate.api.entity.Tournament;
import com.leaguemate.api.entity.TournamentRegistration;
import com.leaguemate.api.entity.TournamentStatus;
import com.leaguemate.api.entity.User;

import java.util.List;

public interface TournamentService {

    Tournament createTournament(Tournament tournament);
    Tournament getTournamentById(Long id);
    List<Tournament> getAllTournaments();
    List<Tournament> getTournamentsByStatus(TournamentStatus status);
    Tournament updateTournament(Long id, String name, String season, int pointsForWin, int pointsForDraw);
    void deleteTournament(Long id);

    TournamentRegistration registerTeamToTournament(Long tournamentId, Long teamId);

    List<Round> generateRounds(Long tournamentId);

    List<StandingEntry> calculateStandings(Long tournamentId);

    TournamentStatsResponse getTournamentStats(Long tournamentId);

    void addOrganizer(Long tournamentId, Long userId);
    void removeOrganizer(Long tournamentId, Long userId);
    List<User> getOrganizers(Long tournamentId);
}