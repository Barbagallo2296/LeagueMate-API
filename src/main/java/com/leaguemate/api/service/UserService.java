package com.leaguemate.api.service;

import com.leaguemate.api.entity.User;

public interface UserService {
    User registerUser(User user);
    User findByUsername(String username);
}