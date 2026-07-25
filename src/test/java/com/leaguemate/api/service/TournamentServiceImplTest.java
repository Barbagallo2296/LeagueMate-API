package com.leaguemate.api.service;

import com.leaguemate.api.dto.StandingEntry;
import com.leaguemate.api.dto.TournamentStatsResponse;
import com.leaguemate.api.entity.*;
import com.leaguemate.api.exception.ResourceConflictException;
import com.leaguemate.api.exception.ResourceNotFoundException;
import com.leaguemate.api.repository.MatchRepository;
import com.leaguemate.api.repository.TeamRepository;
import com.leaguemate.api.repository.TournamentRegistrationRepository;
import com.leaguemate.api.repository.TournamentRepository;
import com.leaguemate.api.repository.UserRepository;
import com.leaguemate.api.service.impl.TournamentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TournamentServiceImplTest {

    @Mock
    private TournamentRepository tournamentRepository;
    @Mock
    private TournamentRegistrationRepository registrationRepository;
    @Mock
    private TeamRepository teamRepository;
    @Mock
    private MatchRepository matchRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TournamentServiceImpl tournamentService;

    private Tournament tournament;
    private User user;
    private Team teamA;
    private Team teamB;

    @BeforeEach
    void setUp() {
        tournament = new Tournament();
        tournament.setId(1L);
        tournament.setName("Champions League");
        tournament.setSeason("2026/2027");
        tournament.setStatus(TournamentStatus.DRAFT);
        tournament.setOrganizers(new HashSet<>());
        tournament.setRounds(new ArrayList<>());
        tournament.setRegistrations(new ArrayList<>());

        user = new User();
        user.setId(1L);
        user.setUsername("organizer1");
        user.setEmail("org@leaguemate.com");
        user.setFirstName("Marco");
        user.setLastName("Rossi");
        user.setRole(Role.ORGANIZER);

        teamA = new Team();
        teamA.setId(1L);
        teamA.setName("Team A");

        teamB = new Team();
        teamB.setId(2L);
        teamB.setName("Team B");
    }

    private TournamentRegistration createReg(Team team) {
        TournamentRegistration reg = new TournamentRegistration();
        reg.setTeam(team);
        reg.setTournament(tournament);
        reg.setStatus(RegistrationStatus.CONFIRMED);
        return reg;
    }

    private Match completedMatch(Team home, Team away, int homeScore, int awayScore) {
        Match match = new Match();
        match.setHomeTeam(home);
        match.setAwayTeam(away);
        match.setHomeScore(homeScore);
        match.setAwayScore(awayScore);
        match.setStatus(MatchStatus.COMPLETED);
        return match;
    }

    // --- CRUD ---

    @Test
    void createTournament_Success() {
        when(tournamentRepository.save(any(Tournament.class))).thenReturn(tournament);

        Tournament created = tournamentService.createTournament(tournament);

        assertNotNull(created);
        assertEquals(TournamentStatus.DRAFT, created.getStatus());
        verify(tournamentRepository, times(1)).save(tournament);
    }

    @Test
    void getTournamentById_Success() {
        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(tournament));

        Tournament found = tournamentService.getTournamentById(1L);

        assertNotNull(found);
        assertEquals("Champions League", found.getName());
    }

    @Test
    void getTournamentById_ThrowsNotFound_WhenDoesNotExist() {
        when(tournamentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> tournamentService.getTournamentById(99L));
    }

    @Test
    void getAllTournaments_ReturnsList() {
        Tournament t2 = new Tournament();
        t2.setId(2L);
        t2.setName("Europa League");

        when(tournamentRepository.findAll()).thenReturn(List.of(tournament, t2));

        List<Tournament> result = tournamentService.getAllTournaments();

        assertEquals(2, result.size());
        verify(tournamentRepository, times(1)).findAll();
    }

    @Test
    void getTournamentsByStatus_ReturnsFilteredList() {
        tournament.setStatus(TournamentStatus.ACTIVE);
        when(tournamentRepository.findByStatus(TournamentStatus.ACTIVE)).thenReturn(List.of(tournament));

        List<Tournament> result = tournamentService.getTournamentsByStatus(TournamentStatus.ACTIVE);

        assertFalse(result.isEmpty());
        assertEquals(TournamentStatus.ACTIVE, result.get(0).getStatus());
    }

    @Test
    void updateTournament_Success() {
        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(tournament));
        when(tournamentRepository.save(any(Tournament.class))).thenReturn(tournament);

        Tournament updated = tournamentService.updateTournament(1L, "Nuovo Nome", "2027/2028", 3, 1);

        assertEquals("Nuovo Nome", updated.getName());
        assertEquals("2027/2028", updated.getSeason());
        verify(tournamentRepository, times(1)).save(tournament);
    }

    @Test
    void updateTournament_ThrowsConflict_WhenCompleted() {
        tournament.setStatus(TournamentStatus.COMPLETED);
        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(tournament));

        assertThrows(ResourceConflictException.class,
                () -> tournamentService.updateTournament(1L, "X", "Y", 3, 1));
        verify(tournamentRepository, never()).save(any(Tournament.class));
    }

    @Test
    void deleteTournament_Success() {
        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(tournament));

        tournamentService.deleteTournament(1L);

        verify(tournamentRepository, times(1)).delete(tournament);
    }

    @Test
    void deleteTournament_ThrowsConflict_WhenActive() {
        tournament.setStatus(TournamentStatus.ACTIVE);
        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(tournament));

        assertThrows(ResourceConflictException.class, () -> tournamentService.deleteTournament(1L));
        verify(tournamentRepository, never()).delete(any(Tournament.class));
    }

    // --- Iscrizioni ---

    @Test
    void registerTeamToTournament_Success() {
        when(tournamentRepository.findWithRegistrationsById(1L)).thenReturn(Optional.of(tournament));
        when(teamRepository.findById(1L)).thenReturn(Optional.of(teamA));
        when(registrationRepository.save(any(TournamentRegistration.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        TournamentRegistration reg = tournamentService.registerTeamToTournament(1L, 1L);

        assertEquals(RegistrationStatus.CONFIRMED, reg.getStatus());
        assertEquals(teamA, reg.getTeam());
    }

    @Test
    void registerTeamToTournament_ThrowsConflict_WhenAlreadyRegistered() {
        tournament.getRegistrations().add(createReg(teamA));
        when(tournamentRepository.findWithRegistrationsById(1L)).thenReturn(Optional.of(tournament));
        when(teamRepository.findById(1L)).thenReturn(Optional.of(teamA));

        assertThrows(ResourceConflictException.class,
                () -> tournamentService.registerTeamToTournament(1L, 1L));
        verify(registrationRepository, never()).save(any(TournamentRegistration.class));
    }

    @Test
    void registerTeamToTournament_ThrowsConflict_WhenTournamentIsActive() {
        tournament.setStatus(TournamentStatus.ACTIVE);
        when(tournamentRepository.findWithRegistrationsById(1L)).thenReturn(Optional.of(tournament));

        assertThrows(ResourceConflictException.class,
                () -> tournamentService.registerTeamToTournament(1L, 1L));
    }

    // --- Generazione calendario (round-robin, metodo del cerchio) ---

    @Test
    void generateRounds_WithFourTeams_CreatesThreeRounds() {
        Team teamC = new Team();
        teamC.setId(3L);
        teamC.setName("Team C");
        Team teamD = new Team();
        teamD.setId(4L);
        teamD.setName("Team D");

        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(tournament));
        when(registrationRepository.findConfirmedWithTeams(1L, RegistrationStatus.CONFIRMED))
                .thenReturn(List.of(createReg(teamA), createReg(teamB), createReg(teamC), createReg(teamD)));
        when(tournamentRepository.save(any(Tournament.class))).thenReturn(tournament);

        List<Round> rounds = tournamentService.generateRounds(1L);

        assertEquals(3, rounds.size());
        assertEquals(2, rounds.get(0).getMatches().size());
        assertEquals(TournamentStatus.ACTIVE, tournament.getStatus());
    }

    @Test
    void generateRounds_WithThreeTeams_CreatesByeRound() {
        Team teamC = new Team();
        teamC.setId(3L);
        teamC.setName("Team C");

        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(tournament));
        when(registrationRepository.findConfirmedWithTeams(1L, RegistrationStatus.CONFIRMED))
                .thenReturn(List.of(createReg(teamA), createReg(teamB), createReg(teamC)));
        when(tournamentRepository.save(any(Tournament.class))).thenReturn(tournament);

        List<Round> rounds = tournamentService.generateRounds(1L);

        assertEquals(3, rounds.size());
        int totalMatches = rounds.stream().mapToInt(r -> r.getMatches().size()).sum();
        assertEquals(3, totalMatches);
    }

    @Test
    void generateRounds_WithSixTeams_CreatesFiveRoundsAndFifteenMatches() {
        Team teamC = new Team();
        teamC.setId(3L);
        teamC.setName("Team C");
        Team teamD = new Team();
        teamD.setId(4L);
        teamD.setName("Team D");
        Team teamE = new Team();
        teamE.setId(5L);
        teamE.setName("Team E");
        Team teamF = new Team();
        teamF.setId(6L);
        teamF.setName("Team F");

        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(tournament));
        when(registrationRepository.findConfirmedWithTeams(1L, RegistrationStatus.CONFIRMED))
                .thenReturn(List.of(
                        createReg(teamA), createReg(teamB), createReg(teamC),
                        createReg(teamD), createReg(teamE), createReg(teamF)));
        when(tournamentRepository.save(any(Tournament.class))).thenReturn(tournament);

        List<Round> rounds = tournamentService.generateRounds(1L);

        assertEquals(5, rounds.size());

        int totalMatches = rounds.stream().mapToInt(r -> r.getMatches().size()).sum();
        assertEquals(15, totalMatches);

        rounds.forEach(round -> assertEquals(3, round.getMatches().size()));

        Set<String> pairs = new HashSet<>();
        rounds.forEach(round -> round.getMatches().forEach(match -> {
            long a = match.getHomeTeam().getId();
            long b = match.getAwayTeam().getId();
            pairs.add(Math.min(a, b) + "-" + Math.max(a, b));
        }));
        assertEquals(15, pairs.size());
    }

    @Test
    void generateRounds_ThrowsConflict_WithLessThanTwoTeams() {
        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(tournament));
        when(registrationRepository.findConfirmedWithTeams(1L, RegistrationStatus.CONFIRMED))
                .thenReturn(List.of(createReg(teamA)));

        assertThrows(ResourceConflictException.class, () -> tournamentService.generateRounds(1L));
    }

    @Test
    void generateRounds_ThrowsConflict_WhenTournamentIsAlreadyActive() {
        tournament.setStatus(TournamentStatus.ACTIVE);
        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(tournament));

        assertThrows(ResourceConflictException.class, () -> tournamentService.generateRounds(1L));
        verify(tournamentRepository, never()).save(any(Tournament.class));
    }

    // --- Classifica ---

    @Test
    void calculateStandings_WithCompletedMatch_ReturnsSortedStandings() {
        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(tournament));
        when(registrationRepository.findConfirmedWithTeams(1L, RegistrationStatus.CONFIRMED))
                .thenReturn(List.of(createReg(teamA), createReg(teamB)));
        when(matchRepository.findCompletedMatchesWithTeams(1L, MatchStatus.COMPLETED))
                .thenReturn(List.of(completedMatch(teamA, teamB, 3, 1)));

        List<StandingEntry> standings = tournamentService.calculateStandings(1L);

        assertEquals(2, standings.size());
        assertEquals("Team A", standings.get(0).teamName());
        assertEquals(3, standings.get(0).points());
        assertEquals(1, standings.get(0).wins());
        assertEquals(2, standings.get(0).goalDifference());
        assertEquals("Team B", standings.get(1).teamName());
        assertEquals(0, standings.get(1).points());
        assertEquals(1, standings.get(1).losses());
        assertEquals(-2, standings.get(1).goalDifference());
    }

    @Test
    void calculateStandings_DrawAssignsOnePointEach() {
        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(tournament));
        when(registrationRepository.findConfirmedWithTeams(1L, RegistrationStatus.CONFIRMED))
                .thenReturn(List.of(createReg(teamA), createReg(teamB)));
        when(matchRepository.findCompletedMatchesWithTeams(1L, MatchStatus.COMPLETED))
                .thenReturn(List.of(completedMatch(teamA, teamB, 2, 2)));

        List<StandingEntry> standings = tournamentService.calculateStandings(1L);

        assertEquals(1, standings.get(0).points());
        assertEquals(1, standings.get(1).points());
        assertEquals(1, standings.get(0).draws());
        assertEquals(0, standings.get(0).goalDifference());
    }

    @Test
    void calculateStandings_UsesCustomPointsConfiguration() {
        tournament.setPointsForWin(5);
        tournament.setPointsForDraw(2);

        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(tournament));
        when(registrationRepository.findConfirmedWithTeams(1L, RegistrationStatus.CONFIRMED))
                .thenReturn(List.of(createReg(teamA), createReg(teamB)));
        when(matchRepository.findCompletedMatchesWithTeams(1L, MatchStatus.COMPLETED))
                .thenReturn(List.of(completedMatch(teamA, teamB, 1, 0)));

        List<StandingEntry> standings = tournamentService.calculateStandings(1L);

        assertEquals(5, standings.get(0).points());
    }

    @Test
    void calculateStandings_TieBreaksByGoalDifferenceThenGoalsFor() {
        Team teamC = new Team();
        teamC.setId(3L);
        teamC.setName("Team C");
        Team teamD = new Team();
        teamD.setId(4L);
        teamD.setName("Team D");

        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(tournament));
        when(registrationRepository.findConfirmedWithTeams(1L, RegistrationStatus.CONFIRMED))
                .thenReturn(List.of(createReg(teamA), createReg(teamB), createReg(teamC), createReg(teamD)));
        when(matchRepository.findCompletedMatchesWithTeams(1L, MatchStatus.COMPLETED))
                .thenReturn(List.of(
                        completedMatch(teamA, teamB, 5, 0),
                        completedMatch(teamC, teamD, 1, 0)));

        List<StandingEntry> standings = tournamentService.calculateStandings(1L);

        assertEquals("Team A", standings.get(0).teamName());
        assertEquals("Team C", standings.get(1).teamName());
        assertEquals(3, standings.get(0).points());
        assertEquals(3, standings.get(1).points());
    }

    @Test
    void calculateStandings_TeamsWithoutMatchesStartAtZero() {
        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(tournament));
        when(registrationRepository.findConfirmedWithTeams(1L, RegistrationStatus.CONFIRMED))
                .thenReturn(List.of(createReg(teamA), createReg(teamB)));
        when(matchRepository.findCompletedMatchesWithTeams(1L, MatchStatus.COMPLETED))
                .thenReturn(List.of());

        List<StandingEntry> standings = tournamentService.calculateStandings(1L);

        assertEquals(2, standings.size());
        assertTrue(standings.stream().allMatch(s -> s.points() == 0));
    }

    // --- Statistiche ---

    @Test
    void getTournamentStats_ReturnsCorrectStats() {
        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(tournament));
        when(registrationRepository.countConfirmedTeams(1L, RegistrationStatus.CONFIRMED)).thenReturn(2L);
        when(matchRepository.countMatchesByTournamentAndStatus(1L, MatchStatus.COMPLETED)).thenReturn(1L);
        when(matchRepository.countMatchesByTournamentAndStatus(1L, MatchStatus.SCHEDULED)).thenReturn(0L);
        when(registrationRepository.findConfirmedWithTeams(1L, RegistrationStatus.CONFIRMED))
                .thenReturn(List.of(createReg(teamA), createReg(teamB)));
        when(matchRepository.findCompletedMatchesWithTeams(1L, MatchStatus.COMPLETED))
                .thenReturn(List.of(completedMatch(teamA, teamB, 3, 1)));

        TournamentStatsResponse stats = tournamentService.getTournamentStats(1L);

        assertEquals(2L, stats.registeredTeams());
        assertEquals(1L, stats.playedMatches());
        assertEquals(0L, stats.remainingMatches());
        assertEquals(4, stats.totalGoals());
        assertEquals(4.0, stats.averageGoalsPerMatch());
        assertEquals("Team A", stats.topScoringTeam());
        assertEquals(3, stats.topScoringTeamGoals());
    }

    @Test
    void getTournamentStats_HandlesTournamentWithoutPlayedMatches() {
        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(tournament));
        when(registrationRepository.countConfirmedTeams(1L, RegistrationStatus.CONFIRMED)).thenReturn(2L);
        when(matchRepository.countMatchesByTournamentAndStatus(1L, MatchStatus.COMPLETED)).thenReturn(0L);
        when(matchRepository.countMatchesByTournamentAndStatus(1L, MatchStatus.SCHEDULED)).thenReturn(6L);
        when(registrationRepository.findConfirmedWithTeams(1L, RegistrationStatus.CONFIRMED))
                .thenReturn(List.of(createReg(teamA), createReg(teamB)));
        when(matchRepository.findCompletedMatchesWithTeams(1L, MatchStatus.COMPLETED))
                .thenReturn(List.of());

        TournamentStatsResponse stats = tournamentService.getTournamentStats(1L);

        assertEquals(0L, stats.playedMatches());
        assertEquals(6L, stats.remainingMatches());
        assertEquals(0, stats.totalGoals());
        assertEquals(0.0, stats.averageGoalsPerMatch());
    }

    // --- Co-organizzatori ---

    @Test
    void addOrganizer_Success() {
        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(tournament));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(tournamentRepository.save(any(Tournament.class))).thenReturn(tournament);

        tournamentService.addOrganizer(1L, 1L);

        assertTrue(tournament.getOrganizers().contains(user));
        verify(tournamentRepository, times(1)).save(tournament);
    }

    @Test
    void addOrganizer_ThrowsConflict_WhenAlreadyOrganizer() {
        tournament.getOrganizers().add(user);
        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(tournament));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThrows(ResourceConflictException.class, () -> tournamentService.addOrganizer(1L, 1L));
        verify(tournamentRepository, never()).save(any(Tournament.class));
    }

    @Test
    void removeOrganizer_Success() {
        tournament.getOrganizers().add(user);
        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(tournament));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(tournamentRepository.save(any(Tournament.class))).thenReturn(tournament);

        tournamentService.removeOrganizer(1L, 1L);

        assertFalse(tournament.getOrganizers().contains(user));
        verify(tournamentRepository, times(1)).save(tournament);
    }

    @Test
    void removeOrganizer_ThrowsNotFound_WhenNotAnOrganizer() {
        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(tournament));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThrows(ResourceNotFoundException.class, () -> tournamentService.removeOrganizer(1L, 1L));
    }

    @Test
    void getOrganizers_ReturnsList() {
        tournament.getOrganizers().add(user);
        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(tournament));

        List<User> organizers = tournamentService.getOrganizers(1L);

        assertEquals(1, organizers.size());
        assertEquals("organizer1", organizers.get(0).getUsername());
    }
}