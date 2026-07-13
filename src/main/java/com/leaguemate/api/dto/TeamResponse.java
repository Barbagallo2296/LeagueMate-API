package com.leaguemate.api.dto;

import java.time.LocalDateTime;

public record TeamResponse(
        Long id,
        String name,
        String logoUrl,
        LocalDateTime createdAt
) {}