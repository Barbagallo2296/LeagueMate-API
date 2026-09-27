package com.leaguemate.api.service;

import com.leaguemate.api.dto.TokenResponse;
import com.leaguemate.api.entity.User;

public interface AuthService {
    User register(User user);
    TokenResponse login(String username, String password);
    TokenResponse refresh(String refreshToken);
    void logout(String accessToken);
    void logoutAll(String username);
}
