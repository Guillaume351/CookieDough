package com.cookiebuild.cookiedough.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import com.cookiebuild.cookiedough.theme.hunt.HuntPlan;

class ThemeDefinitionTest {
    private static ThemeDefinition halloween() throws Exception {
        return ThemeDefinition.parse(SongParserTest.bundled("themes/halloween/theme.yml"));
    }

    @Test
    void theIndexListsHalloween() throws Exception {
        YamlConfiguration index = SongParserTest.bundled("themes/index.yml");
        assertEquals(List.of("halloween"), index.getStringList("themes"));
    }

    @Test
    void halloweenRunsFromTodayToTheEndOfAllSaintsWeekendInParis() throws Exception {
        ThemeDefinition theme = halloween();
        assertEquals("halloween", theme.id());
        assertEquals(ThemeService.HALLOWEEN, theme.id());
        assertEquals(ZonedDateTime.parse("2026-10-12T00:00+02:00[Europe/Paris]").toInstant(), theme.start().toInstant());
        assertEquals(ZonedDateTime.parse("2026-11-03T00:00+01:00[Europe/Paris]").toInstant(), theme.end().toInstant());
        assertEquals("theme.halloween", theme.messagePrefix());
    }

    @Test
    void scheduleFollowsDatesUnlessForcedOrOff() throws Exception {
        List<ThemeDefinition> themes = List.of(halloween());
        Instant inside = Instant.parse("2026-10-31T20:00:00Z");
        Instant before = Instant.parse("2026-10-11T21:59:59Z");
        Instant lastSecond = Instant.parse("2026-11-02T22:59:59Z");
        Instant after = Instant.parse("2026-11-02T23:00:00Z");
        assertEquals("halloween", ThemeSchedule.resolve("auto", themes, inside).orElseThrow().id());
        assertTrue(ThemeSchedule.resolve("auto", themes, before).isEmpty());
        assertEquals("halloween", ThemeSchedule.resolve("auto", themes, Instant.parse("2026-10-11T22:00:00Z"))
                .orElseThrow().id());
        assertTrue(ThemeSchedule.resolve("auto", themes, lastSecond).isPresent());
        assertTrue(ThemeSchedule.resolve("auto", themes, after).isEmpty());
        assertTrue(ThemeSchedule.resolve("off", themes, inside).isEmpty());
        assertTrue(ThemeSchedule.resolve(" OFF ", themes, inside).isEmpty());
        assertEquals("halloween", ThemeSchedule.resolve("halloween", themes, after).orElseThrow().id());
        assertTrue(ThemeSchedule.resolve(null, themes, inside).isPresent());
        assertTrue(ThemeSchedule.isValidMode("auto", themes) && ThemeSchedule.isValidMode("halloween", themes));
        assertFalse(ThemeSchedule.isValidMode("christmas", themes));
    }

    @Test
    void lobbyMusicHuntAndGamesAreConfigured() throws Exception {
        ThemeDefinition theme = halloween();
        assertNotNull(theme.lobby());
        assertEquals("lobby", theme.lobby().world());
        assertTrue(theme.lobby().playerTime() >= 12000 && theme.lobby().playerTime() <= 18000);
        assertTrue(theme.lobby().bats() > 0);

        assertNotNull(theme.music());
        assertTrue(theme.music().gainAt(0) <= 0.5f, "soft next to the box");
        assertTrue(theme.music().gainAt(30) > 0f, "still audible across the lobby");
        assertEquals(0f, theme.music().gainAt(theme.music().radius()));
        assertEquals(90 * 20, theme.music().minGapTicks(), "a real pause between two songs");
        assertEquals(180 * 20, theme.music().maxGapTicks());
        int gap = theme.music().gapTicks(new java.util.Random(7));
        assertTrue(gap >= 90 * 20 && gap <= 180 * 20);

        HuntPlan hunt = theme.hunt();
        assertEquals("halloween-2026", hunt.id());
        assertEquals(13, hunt.spots().size());
        Set<String> positions = new HashSet<>();
        for (HuntPlan.Spot spot : hunt.spots()) {
            assertTrue(positions.add(spot.x() + "," + spot.y() + "," + spot.z()), spot.id());
            assertTrue(spot.block().matches("minecraft:(jack_o_lantern|carved_pumpkin)\\[facing=(north|south|east|west)]"),
                    spot.block());
            assertTrue(Math.hypot(spot.x() - 1, spot.z()) < 45, spot.id() + " is inside the lobby bowl");
        }
        assertEquals("will_o_wisp_trail", hunt.rewardCosmetic());
        assertTrue(hunt.coinsPerFind() > 0 && hunt.completionCoins() > 0);

        assertTrue(theme.games().applies("bedwars") && theme.games().applies("buildbattles"));
        assertFalse(theme.games().applies("skyblock"));
        assertFalse(theme.games().appliesPalette("buildbattles"), "builders must see the blocks they place");
        assertEquals(Set.of("GLOWSTONE", "SEA_LANTERN", "SHROOMLIGHT"), theme.games().palette().keySet());
        assertTrue(theme.texts().motdLine().replaceAll("§[0-9a-fk-or]", "").chars().allMatch(c -> c < 0x80),
                "ASCII server list line apart from colour codes");
        assertEquals(10, theme.buildBattleThemes().size());
        assertTrue(theme.buildBattleThemes().contains("Haunted House"));
    }

    @Test
    void huntOutcomesCountEachSpotOnceAndCompleteOnTheLastOne() {
        assertEquals(new HuntPlan.Outcome(true, 1, 13, false), HuntPlan.outcome(Set.of(), "p01", 13));
        assertEquals(new HuntPlan.Outcome(false, 1, 13, false), HuntPlan.outcome(Set.of("p01"), "p01", 13));
        Set<String> twelve = new HashSet<>();
        for (int i = 1; i <= 12; i++) twelve.add("p%02d".formatted(i));
        assertEquals(new HuntPlan.Outcome(true, 13, 13, true), HuntPlan.outcome(twelve, "p13", 13));
        assertFalse(HuntPlan.outcome(twelve, "p12", 13).completed());
    }

    @Test
    void bundledConfigFollowsTheThemeDatesByDefault() throws Exception {
        YamlConfiguration config = SongParserTest.bundled("config.yml");
        assertEquals("auto", config.getString(ThemeController.MODE_PATH));
    }
}
