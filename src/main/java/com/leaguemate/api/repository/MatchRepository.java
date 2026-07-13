package com.leaguemate.api.repository;

import com.leaguemate.api.entity.Match;
import com.leaguemate.api.entity.MatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MatchRepository extends JpaRepository<Match, Long> {

    List<Match> findByRoundId(Long roundId);

    List<Match> findByRoundTournamentIdAndStatus(Long tournamentId, MatchStatus status);


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
            SELECT COUNT(m) FROM Match m
            WHERE m.round.tournament.id = :tournamentId
              AND m.status = :status
            """)
    long countMatchesByTournamentAndStatus(
            @Param("tournamentId") Long tournamentId,
            @Param("status") MatchStatus status
    );
}