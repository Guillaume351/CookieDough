package com.cookiebuild.cookiedough.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import com.cookiebuild.cookiedough.theme.advent.AdventCalendar;
import com.cookiebuild.cookiedough.theme.hunt.HuntPlan;
import com.cookiebuild.cookiedough.theme.music.Musician;

class ThemeDefinitionTest {
    private static ThemeDefinition halloween() throws Exception {
        return ThemeDefinition.parse(SongParserTest.bundled("themes/halloween/theme.yml"));
    }

    private static ThemeDefinition christmas() throws Exception {
        return ThemeDefinition.parse(SongParserTest.bundled("themes/christmas/theme.yml"));
    }

    /** The contract's song ids (the music track writes themes/christmas/music/<id>.yml). */
    private static final List<String> CHRISTMAS_SONGS = List.of("jingle-bells", "douce-nuit", "mon-beau-sapin",
            "deck-the-halls", "we-wish-you", "il-est-ne", "fee-dragee", "carol-of-the-bells", "les-anges");

    @Test
    void theIndexListsHalloweenAndChristmas() throws Exception {
        YamlConfiguration index = SongParserTest.bundled("themes/index.yml");
        assertEquals(List.of("halloween", "christmas"), index.getStringList("themes"));
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
    void christmasRunsFromTheFirstOfDecemberToTheEndOfTheHolidaysInParis() throws Exception {
        ThemeDefinition theme = christmas();
        assertEquals("christmas", theme.id());
        assertEquals(ThemeService.CHRISTMAS, theme.id());
        assertEquals("theme.christmas", theme.messagePrefix());
        assertEquals(Instant.parse("2026-11-30T23:00:00Z"), theme.start().toInstant());
        assertEquals(Instant.parse("2027-01-04T23:00:00Z"), theme.end().toInstant());

        List<ThemeDefinition> themes = List.of(halloween(), theme);
        assertTrue(ThemeSchedule.resolve("auto", themes, Instant.parse("2026-11-07T12:00:00Z")).isEmpty(),
                "nothing between the two seasons: production gets the code today");
        assertTrue(ThemeSchedule.resolve("auto", themes, Instant.parse("2026-11-30T22:59:59Z")).isEmpty());
        assertEquals("christmas", ThemeSchedule.resolve("auto", themes, Instant.parse("2026-11-30T23:00:00Z"))
                .orElseThrow().id());
        assertEquals("christmas", ThemeSchedule.resolve("auto", themes, Instant.parse("2026-12-24T20:00:00Z"))
                .orElseThrow().id());
        assertEquals("christmas", ThemeSchedule.resolve("auto", themes, Instant.parse("2027-01-04T22:59:59Z"))
                .orElseThrow().id());
        assertTrue(ThemeSchedule.resolve("auto", themes, Instant.parse("2027-01-04T23:00:00Z")).isEmpty());
        assertEquals("christmas", ThemeSchedule.resolve("christmas", themes, Instant.parse("2026-11-07T12:00:00Z"))
                .orElseThrow().id(), "/theme set christmas previews it");
        assertTrue(ThemeSchedule.isValidMode("christmas", themes));
    }

    @Test
    void christmasLobbyIsSnowyWithoutBatsOrWeather() throws Exception {
        ThemeDefinition.Lobby lobby = christmas().lobby();
        assertEquals("lobby", lobby.world());
        assertEquals(0, lobby.bats());
        assertTrue(lobby.playerTime() >= 12000 && lobby.playerTime() < 13000, "a readable winter evening");
        ThemeDefinition.Snowfall snow = lobby.snowfall();
        assertEquals(org.bukkit.Particle.SNOWFLAKE, snow.particle());
        assertTrue(snow.count() <= 10, "cheap for 60 players: " + snow.count());
        assertTrue(snow.minHeight() >= 2, "flakes start above heads");
        for (ThemeDefinition.AnchorParticle rule : lobby.anchorParticles()) {
            assertTrue(ThemeParticles.BEDROCK_SAFE.contains(rule.particle()), rule.toString());
        }
        for (String sound : lobby.ambientSounds()) {
            assertFalse(sound.matches(".*(witch|phantom|bat|cave|ghast|zombie|skeleton|wither).*"), sound);
        }
    }

    @Test
    void christmasSnowmanPlaysTheContractPlaylist() throws Exception {
        ThemeDefinition theme = christmas();
        assertEquals(CHRISTMAS_SONGS, theme.music().playlist());
        Musician snowman = theme.music().musician();
        assertEquals(Musician.SNOW_GOLEM, snowman.type());
        assertEquals("snowman", snowman.nameKey());
        assertTrue(ThemeParticles.BEDROCK_SAFE.contains(snowman.particle()));
        assertEquals(1.5, theme.music().x());
        assertEquals(13.0, theme.music().y());
        assertEquals(-14.5, theme.music().z());
        assertEquals(44, theme.music().radius());
        assertEquals(0.45f, theme.music().gainAt(0), 1e-6);
        assertEquals(90 * 20, theme.music().minGapTicks(), "never music non-stop");
        assertEquals(180 * 20, theme.music().maxGapTicks());
        // Songs are written by the music track: every bundled one must parse; a missing one is skipped at runtime.
        for (String id : CHRISTMAS_SONGS) {
            if (ThemeDefinitionTest.class.getClassLoader().getResource("themes/christmas/music/" + id + ".yml") == null) {
                continue;
            }
            try (java.io.Reader reader = SongParserTest.reader("themes/christmas/music/" + id + ".yml")) {
                assertFalse(com.cookiebuild.cookiedough.theme.music.SongParser.parse(id, reader).title().isBlank(), id);
            }
        }
    }

    @Test
    void christmasHidesThirteenNewPresents() throws Exception {
        HuntPlan hunt = christmas().hunt();
        assertEquals("christmas-2026", hunt.id());
        assertEquals(13, hunt.spots().size());
        assertEquals(15, hunt.coinsPerFind());
        assertEquals(100, hunt.completionCoins());
        assertEquals("candy_cane_trail", hunt.rewardCosmetic());
        assertEquals("christmas-2026:hunt", hunt.rewardSource());
        assertEquals(HuntPlan.Celebration.SPOT_ITEM, hunt.celebration().item());
        assertTrue(ThemeParticles.BEDROCK_SAFE.contains(hunt.celebration().particle()));
        Set<String> pumpkins = new HashSet<>();
        for (HuntPlan.Spot spot : halloween().hunt().spots()) pumpkins.add(spot.x() + "," + spot.y() + "," + spot.z());
        Set<String> positions = new HashSet<>();
        for (int i = 0; i < 13; i++) {
            HuntPlan.Spot spot = hunt.spots().get(i);
            assertEquals("g%02d".formatted(i + 1), spot.id());
            String position = spot.x() + "," + spot.y() + "," + spot.z();
            assertTrue(positions.add(position), spot.id());
            assertFalse(pumpkins.contains(position), spot.id() + " is a Halloween spot");
            assertTrue(spot.block().matches("minecraft:(red|lime)_wool"), spot.block() + ": no facing, no block entity");
            assertTrue(Math.hypot(spot.x() - 1, spot.z()) < 45, spot.id() + " is inside the lobby bowl");
            assertFalse(spot.expected().contains("WOOL") && spot.expected().matches("(RED|LIME)_WOOL"));
        }
    }

    @Test
    void christmasAdventCalendarFollowsTheContract() throws Exception {
        AdventCalendar advent = christmas().advent();
        assertEquals("christmas-2026-advent", advent.id());
        assertEquals(java.time.ZoneId.of("Europe/Paris"), advent.zone());
        assertEquals(1, advent.firstDay());
        assertEquals(24, advent.lastDay());
        assertEquals(10, advent.coinsFor(1));
        assertEquals(50, advent.coinsFor(24));
        assertEquals("christmas-2026-advent:d24", advent.rewardKey(24));
        assertTrue(advent.dayAt(christmas().start().toInstant()).isPresent(), "opens with the theme");
        assertNull(halloween().advent(), "Halloween has no advent calendar");
    }

    @Test
    void christmasTextsGamesAndBuildBattle() throws Exception {
        ThemeDefinition theme = christmas();
        assertNull(theme.games(), "match worlds stay as they are");
        assertTrue(theme.texts().motdLine().replaceAll("§[0-9a-fk-or]", "").chars().allMatch(c -> c < 0x80),
                "ASCII server list line apart from colour codes");
        assertEquals(15, theme.texts().announceMinutes());
        assertEquals(List.of("Snowman", "Christmas Tree", "Santa Claus", "Reindeer", "Gingerbread House", "Gift",
                "Sleigh", "Igloo", "Penguin", "Fireplace"), theme.buildBattleThemes());
    }

    @Test
    void halloweenKeepsItsDefaultsWithItsUnchangedFile() throws Exception {
        ThemeDefinition theme = halloween();
        assertEquals(Musician.HALLOWEEN, theme.music().musician());
        assertEquals(ThemeDefinition.HALLOWEEN_ANCHOR_PARTICLES, theme.lobby().anchorParticles());
        assertNull(theme.lobby().snowfall());
        assertEquals("halloween-2026:pumpkins", theme.hunt().rewardSource(),
                "already granted Will-o'-the-Wisps rewards stay idempotent");
        assertEquals(HuntPlan.Celebration.HALLOWEEN, theme.hunt().celebration());
        assertTrue(theme.hunt().spots().stream().allMatch(spot -> spot.block().contains("[facing=")));
    }

    @Test
    void everyThemeKeyTheCodeUsesExistsInTheBundles() throws Exception {
        java.util.ResourceBundle english = java.util.ResourceBundle.getBundle("messages", java.util.Locale.ENGLISH,
                java.util.ResourceBundle.Control.getNoFallbackControl(java.util.ResourceBundle.Control.FORMAT_PROPERTIES));
        java.util.ResourceBundle french = java.util.ResourceBundle.getBundle("messages", java.util.Locale.FRENCH,
                java.util.ResourceBundle.Control.getNoFallbackControl(java.util.ResourceBundle.Control.FORMAT_PROPERTIES));
        List<String> common = List.of("join.subtitle", "tab.header", "tab.footer", "announce", "announce.bedrock",
                "scoreboard.hunt", "hunt.found", "hunt.complete", "hunt.complete.title", "hunt.complete.subtitle",
                "music.now", "music.next");
        List<String> advent = List.of("advent.opened", "advent.opened.last", "advent.already", "advent.already.last",
                "advent.reminder", "advent.reminder.bedrock", "advent.soon", "advent.over", "advent.unavailable");
        for (ThemeDefinition theme : List.of(halloween(), christmas())) {
            List<String> keys = new java.util.ArrayList<>(common);
            keys.add("music." + theme.music().musician().nameKey());
            if (theme.advent() != null) keys.addAll(advent);
            for (String key : keys) {
                String full = theme.messagePrefix() + "." + key;
                assertTrue(english.containsKey(full), full);
            }
            String nameplate = french.getString(theme.messagePrefix() + ".music." + theme.music().musician().nameKey());
            assertTrue(nameplate.chars().allMatch(c -> c <= 0xFF), "Latin-1 nameplate for Bedrock: " + nameplate);
        }
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
