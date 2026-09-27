package com.leaguemate.api.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class StoredTokenIntrospector implements OpaqueTokenIntrospector {

    public static final String USER_ATTRIBUTE = "user";

    private final HashedOAuth2AuthorizationService authorizationService;
    private final UserDetailsService userDetailsService;

    @Override
    public OAuth2AuthenticatedPrincipal introspect(String token) {
        OAuth2Authorization authorization = authorizationService.findByToken(token, OAuth2TokenType.ACCESS_TOKEN);
        if (authorization == null) {
            throw new BadOpaqueTokenException("Unknown access token");
        }

        OAuth2Authorization.Token<OAuth2AccessToken> accessToken = authorization.getAccessToken();
        if (accessToken == null || !accessToken.isActive()) {
            throw new BadOpaqueTokenException("Expired access token");
        }

        UserDetails user;
        try {
            user = userDetailsService.loadUserByUsername(authorization.getPrincipalName());
        } catch (UsernameNotFoundException ex) {
            throw new BadOpaqueTokenException("The token owner no longer exists");
        }

        List<GrantedAuthority> authorities = List.copyOf(user.getAuthorities());
        return new DefaultOAuth2AuthenticatedPrincipal(user.getUsername(), Map.of(USER_ATTRIBUTE, user), authorities);
    }
}
