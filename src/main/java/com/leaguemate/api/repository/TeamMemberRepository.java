package com.leaguemate.api.repository;

import com.leaguemate.api.entity.TeamMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {

    boolean existsByTeamIdAndUserId(Long teamId, Long userId);

    Optional<TeamMember> findByIdAndTeamId(Long id, Long teamId);

    @Query("""
            SELECT tm FROM TeamMember tm
            JOIN FETCH tm.user
            JOIN FETCH tm.team
            WHERE tm.team.id = :teamId
            ORDER BY tm.id
            """)
    List<TeamMember> findByTeamIdWithUserAndTeam(@Param("teamId") Long teamId);
}