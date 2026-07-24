package com.leaguemate.api.service;

import com.leaguemate.api.entity.Role;
import com.leaguemate.api.entity.User;

import java.util.List;

public interface UserService {

    User registerUser(User user);

    User findByUsername(String username);

    User findById(Long id);

    List<User> findAll();

    User updateRole(Long id, Role role);
}