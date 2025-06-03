package com.cookiebuild.cookiedough.repository;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import com.cookiebuild.cookiedough.model.Match;
import com.cookiebuild.cookiedough.model.PlayerData;

public interface MatchRepository {
    Match save(Match match);

    Match findById(UUID id);

    List<Match> findByPlayer(PlayerData player);

    List<Match> findByGameType(String gameType);

    List<Match> findByDateRange(Date start, Date end);

    List<Match> findByPlayerAndGameType(PlayerData player, String gameType);

    void delete(Match match);
}
