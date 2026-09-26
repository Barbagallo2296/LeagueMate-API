package com.leaguemate.api.controller;

import com.leaguemate.api.dto.AuthResponse;
import com.leaguemate.api.dto.LoginRequest;
import com.leaguemate.api.dto.RegisterRequest;
import com.leaguemate.api.dto.UserResponse;
import com.leaguemate.api.entity.User;
import com.leaguemate.api.mapper.UserMapper;
import com.leaguemate.api.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        User registered = authService.register(UserMapper.fromRegisterRequest(request));

        return new ResponseEntity<>(UserMapper.toResponse(registered), HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        String token = authService.login(request.username(), request.password());
        return ResponseEntity.ok(new AuthResponse(token));
    }
}