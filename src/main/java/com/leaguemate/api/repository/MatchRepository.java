package com.leaguemate.api.repository;

import com.leaguemate.api.entity.Match;
import com.leaguemate.api.entity.MatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MatchRepository extends JpaRepository<Match, Long> {

    List<Match> findByRoundId(Long roundId);

    List<Match> findByRoundTournamentIdAndStatus(Long tournamentId, MatchStatus status);
}