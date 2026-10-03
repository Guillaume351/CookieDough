package com.cookiebuild.cookiedough.retention;

import java.time.Instant;
import java.util.Optional;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/** Dynamic MOTD line 2 on Soirée Cookie days (Paper fires PaperServerListPingEvent through this type). */
public final class SoireeMotdListener implements Listener {
    private final CommunityEventManager events;

    public SoireeMotdListener(CommunityEventManager events) {
        this.events = events;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPing(ServerListPingEvent event) {
        WeeklyEventSchedule schedule = events.recurringSchedule();
        Instant now = Instant.now();
        Optional<String> line = SoireeMotdPolicy.secondLine(schedule, now);
        if (line.isEmpty()) return;
        LegacyComponentSerializer legacy = LegacyComponentSerializer.legacySection();
        String firstLine = SoireeMotdPolicy.firstLine(legacy.serialize(event.motd()));
        boolean live = SoireeMotdPolicy.phase(schedule, now) == SoireeMotdPolicy.Phase.LIVE;
        event.motd(legacy.deserialize(firstLine).append(Component.newline())
                .append(Component.text(line.get(), live ? NamedTextColor.GREEN : NamedTextColor.GOLD)));
    }
}
