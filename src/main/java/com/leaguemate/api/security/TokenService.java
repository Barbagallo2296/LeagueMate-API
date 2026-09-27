package com.leaguemate.api.security;

import com.leaguemate.api.dto.TokenResponse;
import com.leaguemate.api.exception.InvalidTokenException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContext;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.DefaultOAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

@Service
public class TokenService {

    private final HashedOAuth2AuthorizationService authorizationService;
    private final RegisteredClientRepository registeredClientRepository;
    private final OAuth2TokenGenerator<OAuth2Token> tokenGenerator;
    private final UserDetailsService userDetailsService;
    private final AuthorizationServerContext authorizationServerContext;

    public TokenService(HashedOAuth2AuthorizationService authorizationService,
                        RegisteredClientRepository registeredClientRepository,
                        OAuth2TokenGenerator<OAuth2Token> tokenGenerator,
                        UserDetailsService userDetailsService,
                        AuthorizationServerSettings authorizationServerSettings) {
        this.authorizationService = authorizationService;
        this.registeredClientRepository = registeredClientRepository;
        this.tokenGenerator = tokenGenerator;
        this.userDetailsService = userDetailsService;
        this.authorizationServerContext = new AuthorizationServerContext() {
            @Override
            public String getIssuer() {
                return authorizationServerSettings.getIssuer();
            }

            @Override
            public AuthorizationServerSettings getAuthorizationServerSettings() {
                return authorizationServerSettings;
            }
        };
    }

    @Transactional
    public TokenResponse issue(Authentication authenticatedUser) {
        RegisteredClient client = webClient();
        OAuth2AccessToken accessToken = generateAccessToken(client, authenticatedUser);
        OAuth2RefreshToken refreshToken = generateRefreshToken(client, authenticatedUser);

        OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(client)
                .principalName(authenticatedUser.getName())
                .authorizationGrantType(AuthorizationServerConfig.PASSWORD_GRANT)
                .authorizedScopes(Set.of())
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
        authorizationService.save(authorization);

        return toResponse(accessToken, refreshToken.getTokenValue());
    }

    @Transactional(noRollbackFor = InvalidTokenException.class)
    public TokenResponse refresh(String refreshTokenValue) {
        OAuth2Authorization authorization = authorizationService.findByToken(refreshTokenValue, OAuth2TokenType.REFRESH_TOKEN);
        if (authorization == null || authorization.getRefreshToken() == null) {
            throw new InvalidTokenException("Invalid or expired refresh token");
        }
        if (!authorization.getRefreshToken().isActive()) {
            authorizationService.remove(authorization);
            throw new InvalidTokenException("Invalid or expired refresh token");
        }

        Authentication principal = loadPrincipal(authorization.getPrincipalName());
        RegisteredClient client = webClient();
        OAuth2AccessToken accessToken = generateAccessToken(client, principal);

        OAuth2Authorization.Builder rotated = OAuth2Authorization.from(authorization).accessToken(accessToken);
        String refreshTokenForClient = refreshTokenValue;
        if (!client.getTokenSettings().isReuseRefreshTokens()) {
            OAuth2RefreshToken refreshToken = generateRefreshToken(client, principal);
            rotated.refreshToken(refreshToken);
            refreshTokenForClient = refreshToken.getTokenValue();
        }
        authorizationService.save(rotated.build());

        return toResponse(accessToken, refreshTokenForClient);
    }

    @Transactional
    public void revoke(String accessTokenValue) {
        OAuth2Authorization authorization = authorizationService.findByToken(accessTokenValue, OAuth2TokenType.ACCESS_TOKEN);
        if (authorization != null) {
            authorizationService.remove(authorization);
        }
    }

    @Transactional
    public void revokeAll(String principalName) {
        authorizationService.removeAllForPrincipal(principalName);
    }

    private Authentication loadPrincipal(String username) {
        try {
            UserDetails user = userDetailsService.loadUserByUsername(username);
            return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        } catch (UsernameNotFoundException ex) {
            throw new InvalidTokenException("Invalid or expired refresh token");
        }
    }

    private OAuth2AccessToken generateAccessToken(RegisteredClient client, Authentication principal) {
        OAuth2Token generated = tokenGenerator.generate(tokenContext(client, principal, OAuth2TokenType.ACCESS_TOKEN));
        if (generated == null) {
            throw new IllegalStateException("The token generator failed to generate the access token");
        }
        return new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, generated.getTokenValue(),
                generated.getIssuedAt(), generated.getExpiresAt(), Set.of());
    }

    private OAuth2RefreshToken generateRefreshToken(RegisteredClient client, Authentication principal) {
        OAuth2Token generated = tokenGenerator.generate(tokenContext(client, principal, OAuth2TokenType.REFRESH_TOKEN));
        if (!(generated instanceof OAuth2RefreshToken refreshToken)) {
            throw new IllegalStateException("The token generator failed to generate the refresh token");
        }
        return refreshToken;
    }

    private DefaultOAuth2TokenContext tokenContext(RegisteredClient client, Authentication principal, OAuth2TokenType tokenType) {
        return DefaultOAuth2TokenContext.builder()
                .registeredClient(client)
                .principal(principal)
                .authorizationServerContext(authorizationServerContext)
                .authorizationGrantType(AuthorizationServerConfig.PASSWORD_GRANT)
                .authorizedScopes(Set.of())
                .tokenType(tokenType)
                .build();
    }

    private RegisteredClient webClient() {
        RegisteredClient client = registeredClientRepository.findByClientId(AuthorizationServerConfig.WEB_CLIENT_ID);
        if (client == null) {
            throw new IllegalStateException("Registered client " + AuthorizationServerConfig.WEB_CLIENT_ID + " is missing");
        }
        return client;
    }

    private static TokenResponse toResponse(OAuth2AccessToken accessToken, String refreshTokenValue) {
        long expiresIn = Duration.between(Instant.now(), accessToken.getExpiresAt()).toSeconds();
        return new TokenResponse(accessToken.getTokenValue(), refreshTokenValue,
                accessToken.getTokenType().getValue(), Math.max(expiresIn, 0));
    }
}
