package com.leaguemate.api.service.impl;

import com.leaguemate.api.dto.StandingEntry;
import com.leaguemate.api.dto.TournamentStatsResponse;
import com.leaguemate.api.entity.*;
import com.leaguemate.api.exception.ResourceConflictException;
import com.leaguemate.api.exception.ResourceNotFoundException;
import com.leaguemate.api.repository.*;
import com.leaguemate.api.service.TournamentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private static final class TeamStats {

        private final String teamName;
        private int points;
        private int wins;
        private int draws;
        private int losses;
        private int goalsFor;
        private int goalsAgainst;

        private TeamStats(String teamName) {
            this.teamName = teamName;
        }

        private void registerWin(int scored, int conceded, int pointsForWin) {
            points += pointsForWin;
            wins++;
            addGoals(scored, conceded);
        }

        private void registerDraw(int scored, int conceded, int pointsForDraw) {
            points += pointsForDraw;
            draws++;
            addGoals(scored, conceded);
        }

        private void registerLoss(int scored, int conceded) {
            losses++;
            addGoals(scored, conceded);
        }

        private void addGoals(int scored, int conceded) {
            goalsFor += scored;
            goalsAgainst += conceded;
        }

        private StandingEntry toEntry() {
            return new StandingEntry(
                    teamName, points, wins, draws, losses,
                    goalsFor, goalsAgainst, goalsFor - goalsAgainst);
        }
    }

    @Override
    @Transactional
    public Tournament createTournament(Tournament tournament, String creatorUsername) {
        User creator = userRepository.findByUsername(creatorUsername)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + creatorUsername));

        tournament.setStatus(TournamentStatus.DRAFT);
        // Chi crea il torneo ne è il primo organizzatore: senza questo passaggio
        // un ORGANIZER non potrebbe gestire il torneo appena creato.
        tournament.getOrganizers().add(creator);
        return tournamentRepository.save(tournament);
    }

    @Override
    @Transactional(readOnly = true)
    public Tournament getTournamentById(Long id) {
        return tournamentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament not found with id: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Tournament> getAllTournaments(Pageable pageable) {
        return tournamentRepository.findAll(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Tournament> getTournamentsByStatus(TournamentStatus status, Pageable pageable) {
        return tournamentRepository.findByStatus(status, pageable);
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
        Tournament tournament = tournamentRepository.findWithRegistrationsById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tournament not found with id: " + tournamentId));

        if (tournament.getStatus() != TournamentStatus.DRAFT) {
            throw new ResourceConflictException(
                    "Cannot register teams to a tournament that is not in DRAFT status. Current status: "
                            + tournament.getStatus());
        }

        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found with id: " + teamId));

        boolean alreadyRegistered = tournament.getRegistrations().stream()
                .anyMatch(reg -> reg.getTeam().getId().equals(teamId));

        if (alreadyRegistered) {
            throw new ResourceConflictException("Team is already registered to this tournament");
        }

        TournamentRegistration registration = new TournamentRegistration();
        registration.setTournament(tournament);
        registration.setTeam(team);
        registration.setStatus(RegistrationStatus.CONFIRMED);

        return registrationRepository.save(registration);
    }

    @Override
    @Transactional
    public List<Round> generateRounds(Long tournamentId) {
        Tournament tournament = getTournamentById(tournamentId);

        if (tournament.getStatus() != TournamentStatus.DRAFT) {
            throw new ResourceConflictException(
                    "Rounds can only be generated for a tournament in DRAFT status. Current status: "
                            + tournament.getStatus());
        }

        List<Team> teams = registrationRepository
                .findConfirmedWithTeams(tournamentId, RegistrationStatus.CONFIRMED).stream()
                .map(TournamentRegistration::getTeam)
                .collect(Collectors.toCollection(ArrayList::new));

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

                if (homeTeam == null || awayTeam == null) {
                    continue;
                }

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
            generatedRounds.add(round);
        }

        if (tournament.isDoubleRoundRobin()) {
            generatedRounds.addAll(buildReturnLeg(generatedRounds, tournament));
        }

        tournament.getRounds().clear();
        tournament.getRounds().addAll(generatedRounds);
        tournament.setStatus(TournamentStatus.ACTIVE);

        tournamentRepository.save(tournament);
        return tournament.getRounds();
    }

    // Girone di ritorno: stesse giornate dell'andata, nello stesso ordine,
    // con casa e trasferta invertite (giornata N+k speculare alla giornata k).
    private List<Round> buildReturnLeg(List<Round> firstLeg, Tournament tournament) {
        List<Round> returnLeg = new ArrayList<>();

        for (Round firstLegRound : firstLeg) {
            Round round = new Round();
            round.setRoundNumber(firstLegRound.getRoundNumber() + firstLeg.size());
            round.setTournament(tournament);
            round.setMatches(new ArrayList<>());

            for (Match firstLegMatch : firstLegRound.getMatches()) {
                Match match = new Match();
                match.setHomeTeam(firstLegMatch.getAwayTeam());
                match.setAwayTeam(firstLegMatch.getHomeTeam());
                match.setStatus(MatchStatus.SCHEDULED);
                match.setRound(round);
                round.getMatches().add(match);
            }
            returnLeg.add(round);
        }
        return returnLeg;
    }

    @Override
    @Transactional
    public Tournament completeTournament(Long tournamentId) {
        Tournament tournament = getTournamentById(tournamentId);

        if (tournament.getStatus() != TournamentStatus.ACTIVE) {
            throw new ResourceConflictException(
                    "Only an ACTIVE tournament can be completed. Current status: " + tournament.getStatus());
        }

        long remaining = matchRepository.countMatchesByTournamentAndStatus(tournamentId, MatchStatus.SCHEDULED);
        if (remaining > 0) {
            throw new ResourceConflictException(
                    "Cannot complete the tournament: " + remaining + " matches still to be played");
        }

        tournament.setStatus(TournamentStatus.COMPLETED);
        return tournamentRepository.save(tournament);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StandingEntry> calculateStandings(Long tournamentId) {
        return computeStandings(getTournamentById(tournamentId));
    }

    private List<StandingEntry> computeStandings(Tournament tournament) {
        Long tournamentId = tournament.getId();
        Map<Long, TeamStats> table = new LinkedHashMap<>();

        registrationRepository.findConfirmedWithTeams(tournamentId, RegistrationStatus.CONFIRMED)
                .forEach(reg -> table.put(
                        reg.getTeam().getId(),
                        new TeamStats(reg.getTeam().getName())));

        matchRepository.findCompletedMatchesWithTeams(tournamentId, MatchStatus.COMPLETED)
                .forEach(match -> {
                    TeamStats home = table.get(match.getHomeTeam().getId());
                    TeamStats away = table.get(match.getAwayTeam().getId());

                    if (home == null || away == null) {
                        return;
                    }

                    int homeScore = match.getHomeScore();
                    int awayScore = match.getAwayScore();

                    if (homeScore > awayScore) {
                        home.registerWin(homeScore, awayScore, tournament.getPointsForWin());
                        away.registerLoss(awayScore, homeScore);
                    } else if (homeScore < awayScore) {
                        away.registerWin(awayScore, homeScore, tournament.getPointsForWin());
                        home.registerLoss(homeScore, awayScore);
                    } else {
                        home.registerDraw(homeScore, awayScore, tournament.getPointsForDraw());
                        away.registerDraw(awayScore, homeScore, tournament.getPointsForDraw());
                    }
                });

        return table.values().stream()
                .map(TeamStats::toEntry)
                .sorted(Comparator.comparingInt(StandingEntry::points).reversed()
                        .thenComparing(Comparator.comparingInt(StandingEntry::goalDifference).reversed())
                        .thenComparing(Comparator.comparingInt(StandingEntry::goalsFor).reversed())
                        .thenComparing(StandingEntry::teamName))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TournamentStatsResponse getTournamentStats(Long tournamentId) {
        Tournament tournament = getTournamentById(tournamentId);

        List<StandingEntry> standings = computeStandings(tournament);
        long registeredTeams = standings.size();

        Map<MatchStatus, Long> countsByStatus = new EnumMap<>(MatchStatus.class);
        matchRepository.countByStatus(tournamentId)
                .forEach(row -> countsByStatus.put(row.getStatus(), row.getTotal()));
        long playedMatches = countsByStatus.getOrDefault(MatchStatus.COMPLETED, 0L);
        long scheduledMatches = countsByStatus.getOrDefault(MatchStatus.SCHEDULED, 0L);

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
                playedMatches + scheduledMatches,
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