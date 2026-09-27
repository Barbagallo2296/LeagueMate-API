package com.leaguemate.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "oauth2_authorizations")
@Getter
@Setter
@NoArgsConstructor
public class OAuth2AuthorizationRecord {

    @Id
    @Column(length = 100)
    private String id;

    @Column(name = "registered_client_id", nullable = false, length = 100)
    private String registeredClientId;

    @Column(name = "principal_name", nullable = false, length = 50)
    private String principalName;

    @Column(name = "authorization_grant_type", nullable = false, length = 100)
    private String authorizationGrantType;

    @Column(name = "authorized_scopes", length = 500)
    private String authorizedScopes;

    @Column(name = "access_token_hash", nullable = false, unique = true, length = 128)
    private String accessTokenHash;

    @Column(name = "access_token_issued_at", nullable = false)
    private LocalDateTime accessTokenIssuedAt;

    @Column(name = "access_token_expires_at", nullable = false)
    private LocalDateTime accessTokenExpiresAt;

    @Column(name = "refresh_token_hash", unique = true, length = 128)
    private String refreshTokenHash;

    @Column(name = "refresh_token_issued_at")
    private LocalDateTime refreshTokenIssuedAt;

    @Column(name = "refresh_token_expires_at")
    private LocalDateTime refreshTokenExpiresAt;
}
