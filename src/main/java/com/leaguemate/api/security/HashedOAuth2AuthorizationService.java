package com.leaguemate.api.security;

import com.leaguemate.api.entity.OAuth2AuthorizationRecord;
import com.leaguemate.api.repository.OAuth2AuthorizationRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class HashedOAuth2AuthorizationService implements OAuth2AuthorizationService {

    private final OAuth2AuthorizationRecordRepository repository;
    private final RegisteredClientRepository registeredClientRepository;

    @Override
    @Transactional
    public void save(OAuth2Authorization authorization) {
        Optional<OAuth2AuthorizationRecord> existing = repository.findById(authorization.getId());
        repository.save(toRecord(authorization, existing.orElse(null)));
    }

    @Override
    @Transactional
    public void remove(OAuth2Authorization authorization) {
        repository.deleteById(authorization.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public OAuth2Authorization findById(String id) {
        return repository.findById(id).map(this::toAuthorization).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        String hash = TokenHasher.sha512(token);
        Optional<OAuth2AuthorizationRecord> found;
        if (OAuth2TokenType.ACCESS_TOKEN.equals(tokenType)) {
            found = repository.findByAccessTokenHash(hash);
        } else if (OAuth2TokenType.REFRESH_TOKEN.equals(tokenType)) {
            found = repository.findByRefreshTokenHash(hash);
        } else if (tokenType == null) {
            found = repository.findByAccessTokenHash(hash).or(() -> repository.findByRefreshTokenHash(hash));
        } else {
            found = Optional.empty();
        }
        return found.map(this::toAuthorization).orElse(null);
    }

    @Transactional
    public int removeAllForPrincipal(String principalName) {
        return repository.deleteByPrincipalName(principalName);
    }

    @Transactional
    public int removeExpired() {
        return repository.deleteExpired(toLocal(Instant.now()));
    }

    private OAuth2AuthorizationRecord toRecord(OAuth2Authorization authorization, OAuth2AuthorizationRecord existing) {
        OAuth2AuthorizationRecord record = new OAuth2AuthorizationRecord();
        record.setId(authorization.getId());
        record.setRegisteredClientId(authorization.getRegisteredClientId());
        record.setPrincipalName(authorization.getPrincipalName());
        record.setAuthorizationGrantType(authorization.getAuthorizationGrantType().getValue());
        record.setAuthorizedScopes(StringUtils.collectionToCommaDelimitedString(authorization.getAuthorizedScopes()));

        OAuth2Authorization.Token<OAuth2AccessToken> accessToken = authorization.getAccessToken();
        if (accessToken == null) {
            throw new IllegalArgumentException("An authorization must contain an access token");
        }
        record.setAccessTokenHash(storedHash(accessToken, existing == null ? null : existing.getAccessTokenHash()));
        record.setAccessTokenIssuedAt(toLocal(accessToken.getToken().getIssuedAt()));
        record.setAccessTokenExpiresAt(toLocal(accessToken.getToken().getExpiresAt()));

        OAuth2Authorization.Token<OAuth2RefreshToken> refreshToken = authorization.getRefreshToken();
        if (refreshToken != null) {
            record.setRefreshTokenHash(storedHash(refreshToken, existing == null ? null : existing.getRefreshTokenHash()));
            record.setRefreshTokenIssuedAt(toLocal(refreshToken.getToken().getIssuedAt()));
            record.setRefreshTokenExpiresAt(toLocal(refreshToken.getToken().getExpiresAt()));
        }
        return record;
    }

    private OAuth2Authorization toAuthorization(OAuth2AuthorizationRecord record) {
        RegisteredClient registeredClient = registeredClientRepository.findById(record.getRegisteredClientId());
        if (registeredClient == null) {
            throw new DataRetrievalFailureException(
                    "Registered client " + record.getRegisteredClientId() + " not found");
        }

        Set<String> scopes = StringUtils.commaDelimitedListToSet(record.getAuthorizedScopes());

        OAuth2Authorization.Builder builder = OAuth2Authorization.withRegisteredClient(registeredClient)
                .id(record.getId())
                .principalName(record.getPrincipalName())
                .authorizationGrantType(new AuthorizationGrantType(record.getAuthorizationGrantType()))
                .authorizedScopes(scopes)
                .token(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, record.getAccessTokenHash(),
                        toInstant(record.getAccessTokenIssuedAt()), toInstant(record.getAccessTokenExpiresAt()), scopes));

        if (record.getRefreshTokenHash() != null) {
            builder.token(new OAuth2RefreshToken(record.getRefreshTokenHash(),
                    toInstant(record.getRefreshTokenIssuedAt()), toInstant(record.getRefreshTokenExpiresAt())));
        }
        return builder.build();
    }

    private static String storedHash(OAuth2Authorization.Token<? extends OAuth2Token> token, String currentHash) {
        String value = token.getToken().getTokenValue();
        return value.equals(currentHash) ? currentHash : TokenHasher.sha512(value);
    }

    private static LocalDateTime toLocal(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant toInstant(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.toInstant(ZoneOffset.UTC);
    }
}
