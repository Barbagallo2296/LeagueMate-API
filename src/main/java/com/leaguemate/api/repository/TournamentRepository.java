package com.leaguemate.api.repository;

import com.leaguemate.api.entity.Tournament;
import com.leaguemate.api.entity.TournamentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TournamentRepository extends JpaRepository<Tournament, Long> {

    List<Tournament> findBySeason(String season);

    List<Tournament> findByStatus(TournamentStatus status);
}