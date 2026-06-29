package com.leaguemate.api.service;

import com.leaguemate.api.entity.User;

public interface AuthService {
    User register(User user);
    String login(String username, String password);
}