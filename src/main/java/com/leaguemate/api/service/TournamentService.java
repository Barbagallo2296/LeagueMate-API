package com.leaguemate.api.service;

import com.leaguemate.api.dto.StandingEntry; // Importiamo il nuovo Record!
import com.leaguemate.api.entity.Tournament;
import com.leaguemate.api.entity.TournamentRegistration;
import com.leaguemate.api.entity.Round;
import com.leaguemate.api.entity.TournamentStatus;

import java.util.List;

public interface TournamentService {

    Tournament createTournament(Tournament tournament);
    Tournament getTournamentById(Long id);
    List<Tournament> getAllTournaments();
    List<Tournament> getTournamentsByStatus(TournamentStatus status);

    TournamentRegistration registerTeamToTournament(Long tournamentId, Long teamId);

    List<Round> generateRounds(Long tournamentId);

    List<StandingEntry> calculateStandings(Long tournamentId);
}