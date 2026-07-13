package com.leaguemate.api.repository;

import com.leaguemate.api.entity.Tournament;
import com.leaguemate.api.entity.TournamentStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TournamentRepository extends JpaRepository<Tournament, Long> {

    List<Tournament> findBySeason(String season);

    List<Tournament> findByStatus(TournamentStatus status);


    @Query("""
            SELECT DISTINCT t FROM Tournament t
            JOIN t.organizers o
            WHERE o.id = :userId
            """)
    List<Tournament> findTournamentsByOrganizerId(@Param("userId") Long userId);

    @EntityGraph(attributePaths = {"registrations", "registrations.team"})
    Optional<Tournament> findWithRegistrationsById(Long id);
}