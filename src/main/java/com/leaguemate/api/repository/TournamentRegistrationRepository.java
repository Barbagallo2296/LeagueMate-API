package com.leaguemate.api.repository;

import com.leaguemate.api.entity.RegistrationStatus;
import com.leaguemate.api.entity.TournamentRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TournamentRegistrationRepository extends JpaRepository<TournamentRegistration, Long> {

    @Query("""
            SELECT DISTINCT r FROM TournamentRegistration r
            JOIN FETCH r.team
            WHERE r.tournament.id = :tournamentId
              AND r.status = :status
            """)
    List<TournamentRegistration> findConfirmedWithTeams(
            @Param("tournamentId") Long tournamentId,
            @Param("status") RegistrationStatus status
    );
}