package com.cookiebuild.cookiedough.service;

import java.util.List;
import java.util.UUID;

import com.cookiebuild.cookiedough.model.MinigameStats;
import com.cookiebuild.cookiedough.model.MinigameStatsId;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.TypedQuery;

public class MinigameStatsService {

    private final EntityManager entityManager;

    public MinigameStatsService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public static final String MICROBATTLES = "microbattles";
    public static final String PITCHOUT = "pitchout";
    // Ajouter d'autres mini-jeux ici

    /**
     * Récupère ou crée les statistiques d'un joueur pour un mini-jeu
     */
    public MinigameStats getOrCreateStats(UUID playerId, String minigame) {
        MinigameStatsId id = new MinigameStatsId(playerId, minigame);
        MinigameStats stats = entityManager.find(MinigameStats.class, id);

        if (stats == null) {
            // Créer de nouvelles statistiques avec des valeurs par défaut
            stats = new MinigameStats(playerId, minigame);
            saveStats(stats);
        }

        return stats;
    }

    /**
     * Sauvegarde les statistiques d'un joueur
     */
    public void saveStats(MinigameStats stats) {
        EntityTransaction transaction = entityManager.getTransaction();
        try {
            if (!transaction.isActive()) {
                transaction.begin();
            }
            entityManager.merge(stats); // merge pour gérer les nouvelles entités et les mises à jour
            transaction.commit();
        } catch (Exception e) {
            if (transaction.isActive()) {
                transaction.rollback();
            }
            e.printStackTrace();
        }
    }

    /**
     * Ajoute de l'expérience et des pièces pour une victoire
     */
    public void addWin(UUID playerId, String minigame) {
        MinigameStats stats = getOrCreateStats(playerId, minigame);
        stats.addWin();
        stats.addExperience(50); // 50 XP pour une victoire
        stats.addCoins(25); // 25 pièces pour une victoire
        saveStats(stats);
    }

    /**
     * Ajoute de l'expérience pour une défaite
     */
    public void addLoss(UUID playerId, String minigame) {
        MinigameStats stats = getOrCreateStats(playerId, minigame);
        stats.addLoss();
        stats.addExperience(10); // 10 XP pour une défaite (participation)
        stats.addCoins(5); // 5 pièces pour la participation
        saveStats(stats);
    }

    /**
     * Ajoute de l'expérience et des pièces pour un kill
     */
    public void addKill(UUID playerId, String minigame) {
        MinigameStats stats = getOrCreateStats(playerId, minigame);
        stats.addKill();
        stats.addExperience(5); // 5 XP par kill
        stats.addCoins(2); // 2 pièces par kill
        saveStats(stats);
    }

    /**
     * Ajoute une mort aux statistiques
     */
    public void addDeath(UUID playerId, String minigame) {
        MinigameStats stats = getOrCreateStats(playerId, minigame);
        stats.addDeath();
        saveStats(stats);
    }

    /**
     * Vérifie si un joueur peut se permettre un achat
     */
    public boolean canAfford(UUID playerId, String minigame, int cost) {
        MinigameStats stats = getOrCreateStats(playerId, minigame);
        return stats.getCoins() >= cost;
    }

    /**
     * Effectue un achat (retire les pièces)
     */
    public boolean purchase(UUID playerId, String minigame, int cost) {
        MinigameStats stats = getOrCreateStats(playerId, minigame);
        if (stats.removeCoins(cost)) {
            saveStats(stats);
            return true;
        }
        return false;
    }

    /**
     * Débloque un kit pour un joueur
     */
    public void unlockKit(UUID playerId, String minigame, String kitName) {
        MinigameStats stats = getOrCreateStats(playerId, minigame);
        stats.unlockKit(kitName);
        saveStats(stats);
    }

    /**
     * Vérifie si un joueur a débloqué un kit
     */
    public boolean hasUnlockedKit(UUID playerId, String minigame, String kitName) {
        MinigameStats stats = getOrCreateStats(playerId, minigame);
        return stats.hasUnlockedKit(kitName);
    }

    /**
     * Récupère le niveau d'un joueur pour un mini-jeu
     */
    public int getLevel(UUID playerId, String minigame) {
        MinigameStats stats = getOrCreateStats(playerId, minigame);
        return stats.getLevel();
    }

    /**
     * Récupère les pièces d'un joueur pour un mini-jeu
     */
    public int getCoins(UUID playerId, String minigame) {
        MinigameStats stats = getOrCreateStats(playerId, minigame);
        return stats.getCoins();
    }

    /**
     * Récupère le meilleur joueur de la semaine pour un mini-jeu spécifique
     * basé sur le nombre de victoires dans les 7 derniers jours
     */
    public UUID getTopPlayerOfWeek(String minigame) {
        try {
            // Requête pour obtenir le joueur avec le plus de victoires cette semaine
            // Pour l'instant, nous allons utiliser le joueur avec le plus de victoires au
            // total
            TypedQuery<UUID> query = entityManager.createQuery(
                    "SELECT ms.playerId FROM MinigameStats ms " +
                            "WHERE ms.minigame = :minigame " +
                            "ORDER BY ms.wins DESC",
                    UUID.class);
            query.setParameter("minigame", minigame);
            query.setMaxResults(1);

            List<UUID> results = query.getResultList();
            return results.isEmpty() ? null : results.get(0);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Récupère le nom du joueur à partir de son UUID
     * Cette méthode nécessite d'accéder aux données des joueurs
     */
    public String getPlayerName(UUID playerId) {
        try {
            // Pour l'instant, nous retournons juste l'UUID en string
            // Dans une implémentation complète, nous devrions chercher dans PlayerData
            TypedQuery<String> query = entityManager.createQuery(
                    "SELECT pd.playerName FROM PlayerData pd WHERE pd.playerId = :playerId",
                    String.class);
            query.setParameter("playerId", playerId);
            query.setMaxResults(1);

            List<String> results = query.getResultList();
            return results.isEmpty() ? playerId.toString().substring(0, 8) : results.get(0);
        } catch (Exception e) {
            e.printStackTrace();
            return playerId.toString().substring(0, 8);
        }
    }

    public EntityManager getEntityManager() {
        return entityManager;
    }
}