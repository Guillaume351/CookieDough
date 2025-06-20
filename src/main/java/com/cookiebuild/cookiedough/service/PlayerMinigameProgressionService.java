package com.cookiebuild.cookiedough.service;

import java.util.List;
import java.util.UUID;

import com.cookiebuild.cookiedough.model.PlayerMatchPerformance;
import com.cookiebuild.cookiedough.model.PlayerMinigameProgression;
import com.cookiebuild.cookiedough.model.PlayerMinigameProgressionId;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.TypedQuery;

public class PlayerMinigameProgressionService {

    private final EntityManager entityManager;

    public PlayerMinigameProgressionService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public static final String MICROBATTLES = "MicroBattles";
    public static final String PITCHOUT = "Pitchout";

    // Récompenses par action
    private static final int XP_PER_WIN = 100;
    private static final int XP_PER_LOSS = 25;
    private static final int XP_PER_KILL = 10;
    private static final int XP_PER_DEATH = 5;

    private static final int COINS_PER_WIN = 50;
    private static final int COINS_PER_LOSS = 10;
    private static final int COINS_PER_KILL = 5;

    /**
     * Récupère ou crée la progression d'un joueur pour un mini-jeu
     */
    public PlayerMinigameProgression getOrCreateProgression(UUID playerId, String minigame) {
        PlayerMinigameProgressionId id = new PlayerMinigameProgressionId(playerId, minigame);
        PlayerMinigameProgression progression = entityManager.find(PlayerMinigameProgression.class, id);

        if (progression == null) {
            progression = new PlayerMinigameProgression(playerId, minigame);
            saveProgression(progression);
        }

        return progression;
    }

    /**
     * Sauvegarde la progression d'un joueur
     */
    public void saveProgression(PlayerMinigameProgression progression) {
        EntityTransaction transaction = entityManager.getTransaction();
        try {
            if (!transaction.isActive()) {
                transaction.begin();
            }
            entityManager.merge(progression);
            transaction.commit();
        } catch (Exception e) {
            if (transaction.isActive()) {
                transaction.rollback();
            }
            throw e;
        }
    }

    /**
     * Calcule les statistiques cumulatives à partir des performances de match
     */
    public PlayerGameStats calculateStats(UUID playerId, String minigame) {
        // Utiliser la même approche que LobbyScoreboard - récupérer les performances et
        // calculer en Java
        String jpql = """
                SELECT pmp
                FROM PlayerMatchPerformance pmp
                WHERE pmp.player.id = :playerId
                AND pmp.match.gameType = :minigame
                """;

        TypedQuery<PlayerMatchPerformance> query = entityManager.createQuery(jpql, PlayerMatchPerformance.class);
        query.setParameter("playerId", playerId);
        query.setParameter("minigame", minigame);

        List<PlayerMatchPerformance> performances = query.getResultList();

        // Calculer les statistiques en Java comme dans LobbyScoreboard
        int totalMatches = performances.size();
        int wins = (int) performances.stream()
                .filter(p -> p.getMatch().getWinners().stream()
                        .anyMatch(winner -> winner.getId().equals(playerId)))
                .count();
        int losses = totalMatches - wins;
        int totalKills = performances.stream().mapToInt(PlayerMatchPerformance::getKillsInMatch).sum();
        int totalDeaths = performances.stream().mapToInt(PlayerMatchPerformance::getDeathsInMatch).sum();
        int totalAssists = performances.stream().mapToInt(PlayerMatchPerformance::getAssistsInMatch).sum();

        return new PlayerGameStats(totalMatches, wins, losses, totalKills, totalDeaths, totalAssists);

    }

    /**
     * Récompense un joueur après un match
     */
    public void rewardPlayer(UUID playerId, String minigame, boolean won, int kills, int deaths) {
        PlayerMinigameProgression progression = getOrCreateProgression(playerId, minigame);

        // Calculer les récompenses
        int xpGained = 0;
        int coinsGained = 0;

        if (won) {
            xpGained += XP_PER_WIN;
            coinsGained += COINS_PER_WIN;
        } else {
            xpGained += XP_PER_LOSS;
            coinsGained += COINS_PER_LOSS;
        }

        xpGained += kills * XP_PER_KILL;
        xpGained += deaths * XP_PER_DEATH;
        coinsGained += kills * COINS_PER_KILL;

        // Appliquer les récompenses
        progression.addExperience(xpGained);
        progression.addCoins(coinsGained);

        saveProgression(progression);
    }

    // Méthodes pour les kits
    public boolean hasUnlockedKit(UUID playerId, String minigame, String kitName) {
        PlayerMinigameProgression progression = getOrCreateProgression(playerId, minigame);
        return progression.hasUnlockedKit(kitName);
    }

    public boolean canAffordKit(UUID playerId, String minigame, int price) {
        PlayerMinigameProgression progression = getOrCreateProgression(playerId, minigame);
        return progression.getCoins() >= price;
    }

    public boolean hasRequiredLevel(UUID playerId, String minigame, int requiredLevel) {
        PlayerMinigameProgression progression = getOrCreateProgression(playerId, minigame);
        return progression.getLevel() >= requiredLevel;
    }

    public boolean purchaseKit(UUID playerId, String minigame, String kitName, int price) {
        PlayerMinigameProgression progression = getOrCreateProgression(playerId, minigame);

        if (progression.hasUnlockedKit(kitName)) {
            return false; // Déjà débloqué
        }

        if (!progression.spendCoins(price)) {
            return false; // Pas assez de pièces
        }

        progression.unlockKit(kitName);
        saveProgression(progression);
        return true;
    }

    public int getCoins(UUID playerId, String minigame) {
        PlayerMinigameProgression progression = getOrCreateProgression(playerId, minigame);
        return progression.getCoins();
    }

    public int getLevel(UUID playerId, String minigame) {
        PlayerMinigameProgression progression = getOrCreateProgression(playerId, minigame);
        return progression.getLevel();
    }

    /**
     * Classe pour encapsuler les statistiques calculées
     */
    public static class PlayerGameStats {
        private final int totalMatches;
        private final int wins;
        private final int losses;
        private final int kills;
        private final int deaths;
        private final int assists;

        public PlayerGameStats(int totalMatches, int wins, int losses, int kills, int deaths, int assists) {
            this.totalMatches = totalMatches;
            this.wins = wins;
            this.losses = losses;
            this.kills = kills;
            this.deaths = deaths;
            this.assists = assists;
        }

        public int getTotalMatches() {
            return totalMatches;
        }

        public int getWins() {
            return wins;
        }

        public int getLosses() {
            return losses;
        }

        public int getKills() {
            return kills;
        }

        public int getDeaths() {
            return deaths;
        }

        public int getAssists() {
            return assists;
        }

        public double getKDRatio() {
            return deaths == 0 ? kills : (double) kills / deaths;
        }

        public double getWinRate() {
            return totalMatches == 0 ? 0.0 : (double) wins / totalMatches * 100.0;
        }
    }
}