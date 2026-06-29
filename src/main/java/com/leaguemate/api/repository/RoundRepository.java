package com.leaguemate.api.repository;

import com.leaguemate.api.entity.Round;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoundRepository extends JpaRepository<Round, Long> {

    List<Round> findByTournamentIdOrderByRoundNumberAsc(Long tournamentId);
}