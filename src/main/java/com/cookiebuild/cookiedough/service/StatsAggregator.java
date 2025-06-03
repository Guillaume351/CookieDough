package com.cookiebuild.cookiedough.service;

import java.util.List;

import com.cookiebuild.cookiedough.model.GameStats;
import com.cookiebuild.cookiedough.model.Match;
import com.cookiebuild.cookiedough.model.PlayerData;
import com.cookiebuild.cookiedough.repository.MatchRepository;

public class StatsAggregator {
    private final MatchRepository matchRepository;
    private final PlayerStatsService statsService;

    public StatsAggregator(MatchRepository matchRepository, PlayerStatsService statsService) {
        this.matchRepository = matchRepository;
        this.statsService = statsService;
    }

    public void updatePlayerStats(PlayerData player, String gameType) {
        // TODO
    }

    public void updateAllPlayerStats() {
        // TODO: Implement batch update logic
    }
}
