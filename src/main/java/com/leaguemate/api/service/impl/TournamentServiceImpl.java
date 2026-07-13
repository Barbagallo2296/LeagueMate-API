package com.leaguemate.api.service.impl;

import com.leaguemate.api.dto.StandingEntry;
import com.leaguemate.api.dto.TournamentStatsResponse;
import com.leaguemate.api.entity.*;
import com.leaguemate.api.exception.ResourceConflictException;
import com.leaguemate.api.exception.ResourceNotFoundException;
import com.leaguemate.api.repository.*;
import com.leaguemate.api.service.TournamentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TournamentServiceImpl implements TournamentService {

    private final TournamentRepository tournamentRepository;
    private final TeamRepository teamRepository;
    private final TournamentRegistrationRepository registrationRepository;
    private final MatchRepository matchRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public Tournament createTournament(Tournament tournament) {
        tournament.setStatus(TournamentStatus.DRAFT);
        tournament.setCreatedAt(LocalDateTime.now());
        return tournamentRepository.save(tournament);
    }

    @Override
    public Tournament getTournamentById(Long id) {
        return tournamentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament not found with id: " + id));
    }

    @Override
    public List<Tournament> getAllTournaments() {
        return tournamentRepository.findAll();
    }

    @Override
    public List<Tournament> getTournamentsByStatus(TournamentStatus status) {
        return tournamentRepository.findByStatus(status);
    }

    @Override
    @Transactional
    public Tournament updateTournament(Long id, String name, String season, int pointsForWin, int pointsForDraw) {
        Tournament tournament = getTournamentById(id);

        if (tournament.getStatus() == TournamentStatus.COMPLETED) {
            throw new ResourceConflictException("Cannot modify a completed tournament");
        }

        tournament.setName(name);
        tournament.setSeason(season);
        tournament.setPointsForWin(pointsForWin);
        tournament.setPointsForDraw(pointsForDraw);

        return tournamentRepository.save(tournament);
    }

    @Override
    @Transactional
    public void deleteTournament(Long id) {
        Tournament tournament = getTournamentById(id);

        if (tournament.getStatus() == TournamentStatus.ACTIVE) {
            throw new ResourceConflictException(
                    "Cannot delete an active tournament. Complete it first.");
        }

        tournamentRepository.delete(tournament);
    }

    @Override
    @Transactional
    public TournamentRegistration registerTeamToTournament(Long tournamentId, Long teamId) {
        Tournament tournament = getTournamentById(tournamentId);

        if (tournament.getStatus() != TournamentStatus.DRAFT) {
            throw new ResourceConflictException(
                    "Cannot register teams to a tournament that is not in DRAFT status. Current status: "
                            + tournament.getStatus());
        }

        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found with id: " + teamId));

        boolean alreadyRegistered = registrationRepository.findByTournamentId(tournamentId).stream()
                .anyMatch(reg -> reg.getTeam().getId().equals(teamId));

        if (alreadyRegistered) {
            throw new ResourceConflictException("Team is already registered to this tournament");
        }

        TournamentRegistration registration = new TournamentRegistration();
        registration.setTournament(tournament);
        registration.setTeam(team);
        registration.setRegisteredAt(LocalDateTime.now());
        registration.setStatus(RegistrationStatus.CONFIRMED);

        return registrationRepository.save(registration);
    }

    @Override
    @Transactional
    public List<Round> generateRounds(Long tournamentId) {
        Tournament tournament = getTournamentById(tournamentId);

        List<Team> teams = registrationRepository.findByTournamentId(tournamentId).stream()
                .filter(reg -> reg.getStatus() == RegistrationStatus.CONFIRMED)
                .map(TournamentRegistration::getTeam)
                .collect(Collectors.toList());

        if (teams.size() < 2) {
            throw new ResourceConflictException("Cannot generate rounds with less than 2 teams");
        }

        if (teams.size() % 2 != 0) {
            teams.add(null);
        }

        int numTeams = teams.size();
        int numRounds = numTeams - 1;
        int matchesPerRound = numTeams / 2;

        List<Round> generatedRounds = new ArrayList<>();

        for (int roundIdx = 0; roundIdx < numRounds; roundIdx++) {
            Round round = new Round();
            round.setRoundNumber(roundIdx + 1);
            round.setTournament(tournament);
            round.setMatches(new ArrayList<>());

            for (int matchIdx = 0; matchIdx < matchesPerRound; matchIdx++) {
                int homeIdx = (roundIdx + matchIdx) % (numTeams - 1);
                int awayIdx = (numTeams - 1 - matchIdx + roundIdx) % (numTeams - 1);

                if (matchIdx == 0) {
                    awayIdx = numTeams - 1;
                }

                Team homeTeam = teams.get(homeIdx);
                Team awayTeam = teams.get(awayIdx);

                if (homeTeam != null && awayTeam != null) {
                    Match match = new Match();
                    if (roundIdx % 2 == 0) {
                        match.setHomeTeam(homeTeam);
                        match.setAwayTeam(awayTeam);
                    } else {
                        match.setHomeTeam(awayTeam);
                        match.setAwayTeam(homeTeam);
                    }
                    match.setStatus(MatchStatus.SCHEDULED);
                    match.setRound(round);
                    round.getMatches().add(match);
                }
            }
            generatedRounds.add(round);
        }

        tournament.getRounds().clear();
        tournament.getRounds().addAll(generatedRounds);
        tournament.setStatus(TournamentStatus.ACTIVE);

        tournamentRepository.save(tournament);
        return tournament.getRounds();
    }

    @Override
    @Transactional(readOnly = true)
    public List<StandingEntry> calculateStandings(Long tournamentId) {
        Tournament tournament = getTournamentById(tournamentId);

        Map<String, List<Integer>> statsMap = new HashMap<>();

        registrationRepository.findConfirmedWithTeams(tournamentId, RegistrationStatus.CONFIRMED)
                .forEach(reg -> statsMap.put(reg.getTeam().getName(), Arrays.asList(0, 0, 0, 0, 0, 0)));

        matchRepository.findCompletedMatchesWithTeams(tournamentId, MatchStatus.COMPLETED)
                .forEach(match -> {
                    String home = match.getHomeTeam().getName();
                    String away = match.getAwayTeam().getName();
                    int hScore = match.getHomeScore();
                    int aScore = match.getAwayScore();

                    List<Integer> homeStats = new ArrayList<>(statsMap.get(home));
                    List<Integer> awayStats = new ArrayList<>(statsMap.get(away));

                    homeStats.set(4, homeStats.get(4) + hScore);
                    homeStats.set(5, homeStats.get(5) + aScore);
                    awayStats.set(4, awayStats.get(4) + aScore);
                    awayStats.set(5, awayStats.get(5) + hScore);

                    if (hScore > aScore) {
                        homeStats.set(0, homeStats.get(0) + tournament.getPointsForWin());
                        homeStats.set(1, homeStats.get(1) + 1);
                        awayStats.set(3, awayStats.get(3) + 1);
                    } else if (hScore < aScore) {
                        awayStats.set(0, awayStats.get(0) + tournament.getPointsForWin());
                        awayStats.set(1, awayStats.get(1) + 1);
                        homeStats.set(3, homeStats.get(3) + 1);
                    } else {
                        homeStats.set(0, homeStats.get(0) + tournament.getPointsForDraw());
                        awayStats.set(0, awayStats.get(0) + tournament.getPointsForDraw());
                        homeStats.set(2, homeStats.get(2) + 1);
                        awayStats.set(2, awayStats.get(2) + 1);
                    }

                    statsMap.put(home, homeStats);
                    statsMap.put(away, awayStats);
                });

        return statsMap.entrySet().stream()
                .map(entry -> {
                    String name = entry.getKey();
                    List<Integer> s = entry.getValue();
                    int goalDiff = s.get(4) - s.get(5);
                    return new StandingEntry(name, s.get(0), s.get(1), s.get(2), s.get(3), s.get(4), s.get(5), goalDiff);
                })
                .sorted(Comparator.comparingInt(StandingEntry::points).reversed()
                        .thenComparing(Comparator.comparingInt(StandingEntry::goalDifference).reversed()))
                .collect(Collectors.toList());
    }

    /**
     * Statistiche aggregate del torneo. Usa query JPQL di aggregazione (COUNT)
     * e riusa il calcolo della classifica per individuare il miglior attacco.
     */
    @Override
    @Transactional(readOnly = true)
    public TournamentStatsResponse getTournamentStats(Long tournamentId) {
        Tournament tournament = getTournamentById(tournamentId);

        long registeredTeams = registrationRepository.countConfirmedTeams(
                tournamentId, RegistrationStatus.CONFIRMED);

        long playedMatches = matchRepository.countMatchesByTournamentAndStatus(
                tournamentId, MatchStatus.COMPLETED);

        long scheduledMatches = matchRepository.countMatchesByTournamentAndStatus(
                tournamentId, MatchStatus.SCHEDULED);

        long totalMatches = playedMatches + scheduledMatches;

        List<StandingEntry> standings = calculateStandings(tournamentId);

        int totalGoals = standings.stream()
                .mapToInt(StandingEntry::goalsFor)
                .sum();

        double avgGoals = playedMatches > 0
                ? Math.round((double) totalGoals / playedMatches * 100.0) / 100.0
                : 0.0;

        StandingEntry topScorer = standings.stream()
                .max(Comparator.comparingInt(StandingEntry::goalsFor))
                .orElse(null);

        return new TournamentStatsResponse(
                tournament.getId(),
                tournament.getName(),
                registeredTeams,
                totalMatches,
                playedMatches,
                scheduledMatches,
                totalGoals,
                avgGoals,
                topScorer != null ? topScorer.teamName() : null,
                topScorer != null ? topScorer.goalsFor() : 0
        );
    }

    @Override
    @Transactional
    public void addOrganizer(Long tournamentId, Long userId) {
        Tournament tournament = getTournamentById(tournamentId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        if (tournament.getOrganizers().contains(user)) {
            throw new ResourceConflictException("User is already an organizer of this tournament");
        }

        tournament.getOrganizers().add(user);
        tournamentRepository.save(tournament);
    }

    @Override
    @Transactional
    public void removeOrganizer(Long tournamentId, Long userId) {
        Tournament tournament = getTournamentById(tournamentId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        if (!tournament.getOrganizers().contains(user)) {
            throw new ResourceNotFoundException("User is not an organizer of this tournament");
        }

        tournament.getOrganizers().remove(user);
        tournamentRepository.save(tournament);
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> getOrganizers(Long tournamentId) {
        Tournament tournament = getTournamentById(tournamentId);
        return new ArrayList<>(tournament.getOrganizers());
    }
}