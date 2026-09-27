package com.leaguemate.api.repository;

import com.leaguemate.api.entity.Match;
import com.leaguemate.api.entity.MatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MatchRepository extends JpaRepository<Match, Long> {

    interface StatusCount {
        MatchStatus getStatus();
        long getTotal();
    }

    @Query("""
            SELECT m.status AS status, COUNT(m) AS total
            FROM Match m
            WHERE m.round.tournament.id = :tournamentId
            GROUP BY m.status
            """)
    List<StatusCount> countByStatus(@Param("tournamentId") Long tournamentId);

    @Query("""
            SELECT m FROM Match m
            JOIN FETCH m.homeTeam
            JOIN FETCH m.awayTeam
            JOIN FETCH m.round r
            JOIN FETCH r.tournament
            WHERE m.id = :id
            """)
    Optional<Match> findByIdWithTeams(@Param("id") Long id);

    @Query("""
            SELECT DISTINCT m FROM Match m
            JOIN FETCH m.homeTeam
            JOIN FETCH m.awayTeam
            JOIN FETCH m.round r
            WHERE r.tournament.id = :tournamentId
              AND m.status = :status
            """)
    List<Match> findCompletedMatchesWithTeams(
            @Param("tournamentId") Long tournamentId,
            @Param("status") MatchStatus status
    );

    @Query("""
            SELECT DISTINCT m FROM Match m
            JOIN FETCH m.homeTeam
            JOIN FETCH m.awayTeam
            JOIN FETCH m.round
            WHERE m.round.id = :roundId
            ORDER BY m.id
            """)
    List<Match> findByRoundIdWithTeams(@Param("roundId") Long roundId);

    @Query("""
            SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END
            FROM Match m
            JOIN m.round r
            JOIN r.tournament t
            JOIN t.organizers o
            WHERE m.id = :matchId
              AND o.username = :username
            """)
    boolean isTournamentOrganizer(@Param("matchId") Long matchId, @Param("username") String username);

    @Query("""
            SELECT COUNT(m) FROM Match m
            WHERE m.round.tournament.id = :tournamentId
              AND m.status = :status
            """)
    long countMatchesByTournamentAndStatus(
            @Param("tournamentId") Long tournamentId,
            @Param("status") MatchStatus status
    );
}