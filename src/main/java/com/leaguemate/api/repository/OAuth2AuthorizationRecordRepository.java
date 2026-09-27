package com.leaguemate.api.repository;

import com.leaguemate.api.entity.OAuth2AuthorizationRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface OAuth2AuthorizationRecordRepository extends JpaRepository<OAuth2AuthorizationRecord, String> {

    Optional<OAuth2AuthorizationRecord> findByAccessTokenHash(String accessTokenHash);

    Optional<OAuth2AuthorizationRecord> findByRefreshTokenHash(String refreshTokenHash);

    @Modifying
    @Query("DELETE FROM OAuth2AuthorizationRecord r WHERE r.principalName = :principalName")
    int deleteByPrincipalName(@Param("principalName") String principalName);

    @Modifying
    @Query("""
            DELETE FROM OAuth2AuthorizationRecord r
            WHERE r.accessTokenExpiresAt < :now
              AND (r.refreshTokenExpiresAt IS NULL OR r.refreshTokenExpiresAt < :now)
            """)
    int deleteExpired(@Param("now") LocalDateTime now);
}
