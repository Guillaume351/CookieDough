package com.cookiebuild.cookiedough.commands;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.model.PlayerData;
import com.cookiebuild.cookiedough.model.PlayerSession;
import com.cookiebuild.cookiedough.service.PlayerStatsService;

/**
 * Commande de diagnostic pour analyser les sessions d'un joueur
 * Utile pour déboguer les problèmes de temps de jeu
 */
public class SessionDiagnosticCommand implements CommandExecutor {

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Vérifier que c'est un joueur
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cCette commande ne peut être utilisée que par un joueur.");
            return true;
        }

        Player player = (Player) sender;

        // Vérifier les permissions d'administrateur
        if (!player.hasPermission("cookiedough.admin") && !player.isOp()) {
            player.sendMessage("§cVous n'avez pas la permission d'utiliser cette commande.");
            return true;
        }

        if (args.length != 1) {
            player.sendMessage("§cUsage: /sessiondiag <nom_joueur>");
            return true;
        }

        String targetPlayerName = args[0];

        // Rechercher le joueur par nom
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetPlayerName);
        if (targetPlayer == null || !targetPlayer.hasPlayedBefore()) {
            player.sendMessage("§cJoueur '" + targetPlayerName + "' introuvable.");
            return true;
        }

        UUID targetUUID = targetPlayer.getUniqueId();

        // Exécuter l'analyse en asynchrone
        Bukkit.getScheduler().runTaskAsynchronously(CookieDough.getInstance(), () -> {
            analyzePlayerSessions(player, targetPlayer, targetUUID);
        });

        return true;
    }

    private void analyzePlayerSessions(Player sender, OfflinePlayer targetPlayer, UUID targetUUID) {
        PlayerStatsService playerStatsService = CookieDough.getPlayerStatsService();
        PlayerData playerData = playerStatsService.getPlayerData(targetUUID);

        if (playerData == null) {
            sender.sendMessage("§cAucune donnée trouvée pour le joueur " + targetPlayer.getName());
            return;
        }

        Set<PlayerSession> sessionSet = playerData.getPlayerSessions();
        List<PlayerSession> sessions = new ArrayList<>(sessionSet);

        sender.sendMessage("§6=== Diagnostic des sessions pour " + targetPlayer.getName() + " ===");
        sender.sendMessage("§eUUID: §f" + targetUUID);
        sender.sendMessage("§eNombre total de sessions: §f" + sessions.size());

        if (sessions.isEmpty()) {
            sender.sendMessage("§cAucune session trouvée ! Cela explique pourquoi le temps de jeu est 0.");
            return;
        }

        long totalDuration = 0;
        int validSessions = 0;
        int crashedSessions = 0;
        int nullDurationSessions = 0;
        int ongoingSessions = 0;

        sender.sendMessage("§e--- Détail des sessions ---");

        for (int i = 0; i < Math.min(sessions.size(), 10); i++) { // Limiter à 10 sessions pour éviter le spam
            PlayerSession session = sessions.get(i);

            String startTime = session.getStartTime() != null ? dateFormat.format(session.getStartTime()) : "NULL";
            String endTime = session.getEndTime() != null ? dateFormat.format(session.getEndTime()) : "NULL";
            Long duration = session.getDuration();
            boolean crashed = session.isServerCrash();

            if (duration != null) {
                totalDuration += duration;
                validSessions++;
            } else {
                nullDurationSessions++;
            }

            if (crashed) {
                crashedSessions++;
            }

            if (session.getEndTime() == null) {
                ongoingSessions++;
            }

            long durationMinutes = duration != null ? duration / 1000 / 60 : 0;

            sender.sendMessage("§7Session " + (i + 1) + ": §f" + session.getId());
            sender.sendMessage("  §7Début: §f" + startTime);
            sender.sendMessage("  §7Fin: §f" + endTime);
            sender.sendMessage("  §7Durée: §f" + duration + "ms (" + durationMinutes + " min)");
            sender.sendMessage("  §7Crash: §f" + (crashed ? "§cOui" : "§aNon"));
        }

        if (sessions.size() > 10) {
            sender.sendMessage("§7... et " + (sessions.size() - 10) + " autres sessions");
        }

        sender.sendMessage("§e--- Résumé ---");
        sender.sendMessage("§eSessions valides: §f" + validSessions);
        sender.sendMessage("§eSessions crashées: §f" + crashedSessions);
        sender.sendMessage("§eSessions avec durée NULL: §f" + nullDurationSessions);
        sender.sendMessage("§eSessions en cours: §f" + ongoingSessions);
        sender.sendMessage(
                "§eTemps total calculé: §f" + totalDuration + "ms (" + (totalDuration / 1000 / 60) + " minutes)");

        if (nullDurationSessions > 0) {
            sender.sendMessage("§c⚠ PROBLÈME: " + nullDurationSessions + " sessions ont une durée NULL !");
        }

        if (validSessions > 0 && totalDuration == 0) {
            sender.sendMessage("§c⚠ PROBLÈME: Sessions trouvées mais durée totale = 0 !");
        }
    }
}