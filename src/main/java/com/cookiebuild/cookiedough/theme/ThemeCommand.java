package com.cookiebuild.cookiedough.theme;

import java.util.List;
import java.util.stream.Stream;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

/** {@code /theme status|set <auto|off|id>|reload|music next} for operators. */
public final class ThemeCommand implements TabExecutor {
    private final ThemeController controller;

    public ThemeCommand(ThemeController controller) {
        this.controller = controller;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String action = args.length == 0 ? "status" : args[0].toLowerCase(java.util.Locale.ROOT);
        switch (action) {
            case "set" -> {
                if (args.length < 2 || !controller.setMode(args[1])) {
                    sender.sendMessage("§cUsage : /theme set <auto|off|" + String.join("|", ids()) + ">");
                    return true;
                }
                sender.sendMessage("§aMode de thème : " + controller.mode());
                status(sender);
            }
            case "reload" -> {
                controller.reload();
                sender.sendMessage("§aThèmes rechargés.");
                status(sender);
            }
            case "music" -> {
                if (args.length >= 2 && args[1].equalsIgnoreCase("next")) {
                    controller.music().next();
                    sender.sendMessage("§aMorceau suivant.");
                } else {
                    sender.sendMessage("§cUsage : /theme music next");
                }
            }
            default -> status(sender);
        }
        return true;
    }

    private void status(CommandSender sender) {
        sender.sendMessage("§6Thème : §f" + controller.current().map(ThemeDefinition::id).orElse("aucun")
                + " §7(mode " + controller.mode() + ")");
        controller.current().ifPresent(theme -> sender.sendMessage("§7Fenêtre : " + theme.start().toLocalDateTime()
                + " → " + theme.end().toLocalDateTime() + " (" + theme.start().getZone() + ")"));
        sender.sendMessage("§7Décors client du lobby : " + controller.decorSize());
        controller.music().nowPlaying().ifPresent(song ->
                sender.sendMessage("§7Musique : " + song.title() + " (" + song.composer() + ")"));
        sender.sendMessage("§7Thèmes connus : " + controller.themes().stream()
                .map(theme -> theme.id() + " " + theme.start().toLocalDate() + "→" + theme.end().toLocalDate())
                .toList());
    }

    private List<String> ids() {
        return controller.themes().stream().map(ThemeDefinition::id).toList();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return Stream.of("status", "set", "reload", "music")
                .filter(value -> value.startsWith(args[0].toLowerCase(java.util.Locale.ROOT))).toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            return Stream.concat(Stream.of("auto", "off"), ids().stream())
                    .filter(value -> value.startsWith(args[1].toLowerCase(java.util.Locale.ROOT))).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("music")) return List.of("next");
        return List.of();
    }
}
