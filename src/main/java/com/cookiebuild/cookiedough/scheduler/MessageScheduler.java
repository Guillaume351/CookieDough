package com.cookiebuild.cookiedough.scheduler;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.utils.LocaleManager;

/**
 * Planificateur de messages automatiques pour informer les joueurs
 * des fonctionnalités du serveur et des réseaux sociaux
 */
public class MessageScheduler {

    private final CookieDough plugin;
    private final LocaleManager localeManager;
    private BukkitRunnable schedulerTask;
    private final AtomicInteger messageIndex = new AtomicInteger(0);

    // Liste des clés de messages à faire tourner
    private final List<String> messageKeys = Arrays.asList(
            "scheduled.website_stats",
            "scheduled.java_compatibility",
            "scheduled.discord_suggestions",
            "scheduled.twitter_follow",
            "scheduled.instagram_follow");

    // Intervalle entre les messages (en ticks - 20 ticks = 1 seconde)
    private static final long MESSAGE_INTERVAL = 20 * 60 * 5; // 5 minutes

    // Nombre minimum de joueurs pour envoyer des messages
    private static final int MIN_PLAYERS_FOR_MESSAGES = 1;

    public MessageScheduler(CookieDough plugin, LocaleManager localeManager) {
        this.plugin = plugin;
        this.localeManager = localeManager;
    }

    /**
     * Démarre le planificateur de messages
     */
    public void start() {
        if (schedulerTask != null && !schedulerTask.isCancelled()) {
            schedulerTask.cancel();
        }

        schedulerTask = new BukkitRunnable() {
            @Override
            public void run() {
                sendScheduledMessage();
            }
        };

        // Démarrer avec un délai initial de 2 minutes, puis répéter toutes les 5
        // minutes
        schedulerTask.runTaskTimer(plugin, 20 * 60 * 2, MESSAGE_INTERVAL);

        plugin.getLogger().info("MessageScheduler démarré - Messages toutes les 5 minutes");
    }

    /**
     * Arrête le planificateur de messages
     */
    public void stop() {
        if (schedulerTask != null && !schedulerTask.isCancelled()) {
            schedulerTask.cancel();
            schedulerTask = null;
        }
        plugin.getLogger().info("MessageScheduler arrêté");
    }

    /**
     * Envoie un message planifié à tous les joueurs connectés
     */
    private void sendScheduledMessage() {
        // Vérifier qu'il y a assez de joueurs connectés
        if (Bukkit.getOnlinePlayers().size() < MIN_PLAYERS_FOR_MESSAGES) {
            return;
        }

        // Obtenir la clé du message suivant
        String messageKey = getNextMessageKey();

        // Envoyer le message à tous les joueurs connectés
        for (Player player : Bukkit.getOnlinePlayers()) {
            String message = localeManager.getMessage(messageKey, player.locale());
            player.sendMessage("§6[Cookie Build] §f" + message);
        }

        plugin.getLogger().info("Message planifié envoyé: " + messageKey + " à " +
                Bukkit.getOnlinePlayers().size() + " joueurs");
    }

    /**
     * Obtient la clé du prochain message dans la rotation
     */
    private String getNextMessageKey() {
        int currentIndex = messageIndex.getAndIncrement();

        // Revenir au début si on a atteint la fin de la liste
        if (currentIndex >= messageKeys.size()) {
            messageIndex.set(1); // Réinitialiser à 1 car on va retourner l'index 0
            return messageKeys.get(0);
        }

        return messageKeys.get(currentIndex);
    }

    /**
     * Envoie immédiatement un message de test (pour debug)
     */
    public void sendTestMessage() {
        if (Bukkit.getOnlinePlayers().isEmpty()) {
            plugin.getLogger().info("Aucun joueur connecté pour le test de message");
            return;
        }

        String messageKey = getNextMessageKey();
        for (Player player : Bukkit.getOnlinePlayers()) {
            String message = localeManager.getMessage(messageKey, player.locale());
            player.sendMessage("§6[Cookie Build - Test] §f" + message);
        }

        plugin.getLogger().info("Message de test envoyé: " + messageKey);
    }

    /**
     * Obtient l'état actuel du planificateur
     */
    public boolean isRunning() {
        return schedulerTask != null && !schedulerTask.isCancelled();
    }

    /**
     * Obtient le nombre de messages dans la rotation
     */
    public int getMessageCount() {
        return messageKeys.size();
    }

    /**
     * Obtient l'index du message actuel
     */
    public int getCurrentMessageIndex() {
        return messageIndex.get() % messageKeys.size();
    }
}