package com.leaguemate.api.dto;

public record StandingEntry(
        String teamName,
        int points,
        int wins,
        int draws,
        int losses,
        int goalsFor,
        int goalsAgainst,
        int goalDifference
) {}