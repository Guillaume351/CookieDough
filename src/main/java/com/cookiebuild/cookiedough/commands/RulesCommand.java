package com.cookiebuild.cookiedough.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.cookiebuild.cookiedough.utils.LocaleManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;

/** Discoverable link to the canonical public server rules. */
public final class RulesCommand implements CommandExecutor {
    public static final String RULES_URL = "https://www.cookie-build.com/rules";

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(RULES_URL);
            return true;
        }
        player.sendMessage(Component.text(LocaleManager.getMessage(
                        "rules.message", player.locale()) + " ", NamedTextColor.GOLD)
                .append(Component.text(RULES_URL, NamedTextColor.AQUA)
                        .clickEvent(ClickEvent.openUrl(RULES_URL))
                        .hoverEvent(HoverEvent.showText(Component.text(LocaleManager.getMessage(
                                "rules.hover", player.locale()))))));
        return true;
    }
}
