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

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

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

    @BeforeEach
    void setUp() {
        tournament = new Tournament();
        tournament.setId(1L);
        tournament.setName("Champions League");
        tournament.setSeason("2026/2027");
        tournament.setOrganizers(new HashSet<>());

        user = new User();
        user.setId(1L);
        user.setUsername("organizer1");
        user.setEmail("org@leaguemate.com");
        user.setFirstName("Marco");
        user.setLastName("Rossi");
        user.setRole(Role.ORGANIZER);
    }

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

        assertNotNull(result);
        assertEquals(2, result.size());
        verify(tournamentRepository, times(1)).findAll();
    }

    @Test
    void getTournamentsByStatus_ReturnsFilteredList() {
        TournamentStatus targetStatus = TournamentStatus.ACTIVE;
        tournament.setStatus(targetStatus);

        when(tournamentRepository.findByStatus(targetStatus)).thenReturn(List.of(tournament));

        List<Tournament> result = tournamentService.getTournamentsByStatus(targetStatus);

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals(targetStatus, result.get(0).getStatus());
        verify(tournamentRepository, times(1)).findByStatus(targetStatus);
    }

    @Test
    void updateTournament_Success() {
        tournament.setStatus(TournamentStatus.DRAFT);
        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(tournament));
        when(tournamentRepository.save(any(Tournament.class))).thenReturn(tournament);

        Tournament updated = tournamentService.updateTournament(
                1L, "Nuovo Nome", "2027/2028", 3, 1);

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
        tournament.setStatus(TournamentStatus.DRAFT);
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

    @Test
    void generateRounds_WithFourTeams_CreatesThreeRounds() {
        Team teamA = new Team(); teamA.setId(1L); teamA.setName("Team A");
        Team teamB = new Team(); teamB.setId(2L); teamB.setName("Team B");
        Team teamC = new Team(); teamC.setId(3L); teamC.setName("Team C");
        Team teamD = new Team(); teamD.setId(4L); teamD.setName("Team D");

        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(tournament));
        when(registrationRepository.findByTournamentId(1L)).thenReturn(List.of(
                createReg(teamA), createReg(teamB), createReg(teamC), createReg(teamD)
        ));
        when(tournamentRepository.save(any(Tournament.class))).thenReturn(tournament);

        List<Round> rounds = tournamentService.generateRounds(1L);

        assertNotNull(rounds);
        assertEquals(3, rounds.size());
        assertEquals(2, rounds.get(0).getMatches().size());
        assertEquals(TournamentStatus.ACTIVE, tournament.getStatus());
    }

    @Test
    void calculateStandings_WithCompletedMatch_ReturnsSortedStandings() {
        Team teamA = new Team(); teamA.setId(1L); teamA.setName("Team A");
        Team teamB = new Team(); teamB.setId(2L); teamB.setName("Team B");

        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(tournament));
        when(registrationRepository.findConfirmedWithTeams(1L, RegistrationStatus.CONFIRMED))
                .thenReturn(List.of(createReg(teamA), createReg(teamB)));

        Match match = new Match();
        match.setHomeTeam(teamA);
        match.setAwayTeam(teamB);
        match.setHomeScore(3);
        match.setAwayScore(1);
        match.setStatus(MatchStatus.COMPLETED);

        when(matchRepository.findCompletedMatchesWithTeams(1L, MatchStatus.COMPLETED))
                .thenReturn(List.of(match));

        List<StandingEntry> standings = tournamentService.calculateStandings(1L);

        assertEquals(2, standings.size());
        assertEquals("Team A", standings.get(0).teamName());
        assertEquals(3, standings.get(0).points());
        assertEquals(2, standings.get(0).goalDifference());
        assertEquals("Team B", standings.get(1).teamName());
        assertEquals(0, standings.get(1).points());
        assertEquals(-2, standings.get(1).goalDifference());
    }

    @Test
    void getTournamentStats_ReturnsCorrectStats() {
        Team teamA = new Team(); teamA.setId(1L); teamA.setName("Team A");
        Team teamB = new Team(); teamB.setId(2L); teamB.setName("Team B");

        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(tournament));
        when(registrationRepository.countConfirmedTeams(1L, RegistrationStatus.CONFIRMED))
                .thenReturn(2L);
        when(matchRepository.countMatchesByTournamentAndStatus(1L, MatchStatus.COMPLETED))
                .thenReturn(1L);
        when(matchRepository.countMatchesByTournamentAndStatus(1L, MatchStatus.SCHEDULED))
                .thenReturn(0L);
        when(registrationRepository.findConfirmedWithTeams(1L, RegistrationStatus.CONFIRMED))
                .thenReturn(List.of(createReg(teamA), createReg(teamB)));

        Match match = new Match();
        match.setHomeTeam(teamA);
        match.setAwayTeam(teamB);
        match.setHomeScore(3);
        match.setAwayScore(1);
        match.setStatus(MatchStatus.COMPLETED);

        when(matchRepository.findCompletedMatchesWithTeams(1L, MatchStatus.COMPLETED))
                .thenReturn(List.of(match));

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
    void getOrganizers_ReturnsList() {
        tournament.getOrganizers().add(user);
        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(tournament));

        List<User> organizers = tournamentService.getOrganizers(1L);

        assertEquals(1, organizers.size());
        assertEquals("organizer1", organizers.get(0).getUsername());
    }

    private TournamentRegistration createReg(Team team) {
        TournamentRegistration reg = new TournamentRegistration();
        reg.setTeam(team);
        reg.setStatus(RegistrationStatus.CONFIRMED);
        return reg;
    }
}