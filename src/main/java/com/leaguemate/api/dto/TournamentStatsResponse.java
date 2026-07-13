package com.leaguemate.api.dto;

public record TournamentStatsResponse(
        Long tournamentId,
        String tournamentName,
        long registeredTeams,
        long totalMatches,
        long playedMatches,
        long remainingMatches,
        int totalGoals,
        double averageGoalsPerMatch,
        String topScoringTeam,
        int topScoringTeamGoals
) {}