package com.leaguemate.api.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUserProfileRequest(

        @Size(max = 500, message = "Bio cannot exceed 500 characters")
        String bio,

        @Size(max = 255, message = "Avatar URL cannot exceed 255 characters")
        String avatarUrl,

        @Pattern(
                regexp = "^$|^\\+?[0-9\\s]{6,20}$",
                message = "Phone number must contain only digits, spaces and an optional leading +"
        )
        String phoneNumber
) {}