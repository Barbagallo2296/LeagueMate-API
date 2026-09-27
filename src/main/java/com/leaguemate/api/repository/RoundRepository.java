package com.leaguemate.api.repository;

import com.leaguemate.api.entity.Round;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoundRepository extends JpaRepository<Round, Long> {

    @Query("""
            SELECT DISTINCT r FROM Round r
            LEFT JOIN FETCH r.matches m
            LEFT JOIN FETCH m.homeTeam
            LEFT JOIN FETCH m.awayTeam
            WHERE r.tournament.id = :tournamentId
            ORDER BY r.roundNumber, m.id
            """)
    List<Round> findByTournamentIdWithMatches(@Param("tournamentId") Long tournamentId);
}
