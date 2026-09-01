package com.cookiebuild.cookiedough.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.scheduler.MessageScheduler;

/**
 * Commande pour tester le système de messages automatiques
 * Réservée aux administrateurs
 */
public class MessageTestCommand implements CommandExecutor {

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

        MessageScheduler scheduler = CookieDough.getInstance().getMessageScheduler();

        if (scheduler == null) {
            player.sendMessage("§cErreur: MessageScheduler n'est pas initialisé.");
            return true;
        }

        if (args.length == 0) {
            // Afficher l'aide
            player.sendMessage("§6=== MessageScheduler Test ===");
            player.sendMessage("§e/messagetest send §7- Envoie un message de test");
            player.sendMessage("§e/messagetest status §7- Affiche le statut du planificateur");
            player.sendMessage("§e/messagetest info §7- Affiche les informations détaillées");
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "send":
                scheduler.sendTestMessage();
                player.sendMessage("§aMessage de test envoyé à tous les joueurs connectés !");
                break;

            case "status":
                boolean isRunning = scheduler.isRunning();
                player.sendMessage("§6=== Statut MessageScheduler ===");
                player.sendMessage("§eStatut: " + (isRunning ? "§aActif" : "§cInactif"));
                player.sendMessage("§eNombre de messages: §f" + scheduler.getMessageCount());
                player.sendMessage("§eIndex actuel: §f" + scheduler.getCurrentMessageIndex());
                break;

            case "info":
                player.sendMessage("§6=== Informations MessageScheduler ===");
                player.sendMessage("§eIntervalle: §f5 minutes");
                player.sendMessage("§eJoueurs minimum: §f2");
                player.sendMessage("§eMessages disponibles:");
                player.sendMessage("§7- Site web avec statistiques");
                player.sendMessage("§7- Compatibilité Java Minecraft");
                player.sendMessage("§7- Suggestions Discord");
                player.sendMessage("§7- Twitter @CookieBuild");
                player.sendMessage("§7- Instagram @Cookie_Build");
                break;

            default:
                player.sendMessage("§cSous-commande inconnue. Utilisez /messagetest pour voir l'aide.");
                break;
        }

        return true;
    }
}