# Seasonal themes (Halloween, Christmas, …)

A seasonal theme dresses up the network for a few weeks **without modifying any world, map
archive or persistent entity**. Everything it shows lives in players' clients or in memory:

| What | How | Why nothing has to be restored |
|---|---|---|
| Lobby decor | Fake blocks sent per chunk (`FakeBlockRenderer`), re-checked against the real block at send time | The server world never changes; switching off re-sends the real blocks |
| Lamps in match worlds | Same-shape swaps computed lazily per chunk (`PaletteLayer`) | Match worlds are disposable copies anyway; nothing is written |
| Night / sunset | `Player#setPlayerTime` per player | World time stays frozen at noon |
| Particles, snowfall | `Player#spawnParticle` per player | Nothing exists outside that client |
| Musician (witch, snowman), bats | Non-persistent entities marked with `LobbyEntityOwnership` | Never saved; startup cleanup removes leftovers |
| Music box | Note block sounds played per listener, category "Jukebox/Note Blocks" | No resource pack |
| Texts | Tab header/footer, MOTD line 2, lobby scoreboard title and line, arrival subtitle | Computed live |
| Collectible hunt | Real decor blocks shown as pumpkins (or presents) until found; finds in `seasonal_collectibles` | Rows are history, by design |
| Advent calendar | One gift a day behind the musician; openings in `seasonal_collectibles` | Rows and coins are history, by design |
| Limited cosmetics | `CosmeticDefinition.season`: on sale only while that theme is active | Owners keep them forever |
| Build Battle | `ThemeService.buildBattleThemes()`: one seasonal theme in every vote | — |

Safety rules built in:
- A fake block never supports a player. If they stand on one, the real block is sent back, so they can't be kicked for flying.
- A click on a fake block re-sends that chunk's fakes, because the server answers clicks with the real block.
- Fake blocks only replace blocks of the same shape, or fill air out of reach (cobwebs at least two blocks above the floor). Ground cover is non-colliding (leaf litter, one snow layer).
- Nothing is planned on a hunt spot or on the block above it, so the spot stays readable and clickable.
- Build Battle is excluded from lamp swaps: players build with those blocks.
- Never use blocks that slow players down on the client only (sweet berry bushes, powder snow, cobwebs within reach): the server disagrees and players rubber-band.
- Theme particles must have a native Bedrock mapping in Geyser (`ThemeParticles.BEDROCK_SAFE`); the theme file fails to load otherwise.

## Switching

- `config.yml` → `themes.mode: auto` (default) follows each theme's `window` (Europe/Paris).
- `/theme set off` switches every theme off at once, live, and persists the choice.
- `/theme set <id>` forces a theme outside its dates (e.g. for a preview on the test server).
- `/theme status` shows the active theme, its window, the number of client-side decorations and the song playing.
- `/theme reload` re-reads the theme files.
- `/theme music next` skips to the next song.

Players use `/musique` (aliases `/music`, `/musica`, `/musik`) to switch the music box off or on.

The music box plays one song, then stays silent for a random time (`music.gap-seconds: [90, 180]` for Halloween).

To add a song without a release:
1. Copy a Note Block Studio `.nbs` file (or a `.yml` in the notation of `SongParser`) to `plugins/CookieDough/themes/<id>/music/` on the server.
2. Run `/theme reload`.

Every file in that folder joins the rotation, and a file named like a bundled song replaces it. Custom NBS instruments are skipped, and notes outside the vanilla two octaves are folded back by octaves.

## Theme file reference

Everything below is optional unless stated. A key left out keeps the Halloween value, so
`themes/halloween/theme.yml` needs none of the newer keys.

- **window** (required): `start` and `end` in `zone` (Europe/Paris).
- **texts:** `scoreboard-title`, `motd-line` (ASCII apart from § codes), `announce-minutes`.
- **lobby:**
  - `player-time`, decor area (`radius`, `min-y`, `max-y`), `bats` (0..32), `ambient` sounds and interval;
  - `decor`: `swaps`, `toppers`, `ground`, `hanging`, `ceiling-corners`.
    - YAML 1.1 reads `on` as a boolean, so the rules use `over`/`under`.
    - Weights may include `keep`: that share of the matching blocks stays real (e.g. a few baubles among the leaves).
    - `keep-states: false` stops a swap from copying the real block's states (needed when the target has other states).
  - `anchor-particles`: particles at decor blocks whose data contains one of `near`, e.g. `{near: [jack_o_lantern], particle: SOUL, dy: 0.9, spread-xz: 0.15, spread-y: 0.1, speed: 0.01}`. Absent: Halloween's souls. `[]`: none.
  - `snowfall`: `{particle: SNOWFLAKE, count: 6, radius: 10, height: [3, 10]}`. Every half second, each player in the decorated area gets `count` flakes (one packet) in a box around them. No weather change. Absent: no snowfall.
- **music:**
  - position (`x`, `y`, `z`, `yaw`), `radius`, `max-gain`, `gap-seconds`, `playlist` (song ids; a missing song is logged and skipped);
  - `musician`: `witch` (default) or `snow_golem`;
  - `particle` above it while a song plays (default WITCH for the witch, NOTE otherwise);
  - `name-key`: the nameplate is `<messages>.music.<name-key>` in French (Latin-1 for Bedrock). Default `witch`;
  - `name-color`: a named colour (default `dark_purple`).
  - The musician is non-persistent, AI off, silent, invulnerable and not collidable. A snow golem has no pumpkin and can't melt, leave snow, shoot or be sheared.
- **hunt:** `id` (one per edition), `spots` (`real` = expected real type; `block` shown; `facing` only when the block has one), coins, `reward-cosmetic`.
  - `reward-source`: entitlement source of the reward cosmetic. Default `<id>:pumpkins` (Halloween 2026's value, kept for idempotent grants).
  - `celebration`: `{item: SPOT, particle: FIREWORK, sound: entity.player.levelup}`. `item: SPOT` bursts the block the spot was shown as. Default: Halloween's pumpkin, souls and witch laugh.
- **advent:** see below. Absent: no calendar.
- **games:** client time and same-shape lamp palette in match worlds. Absent: match worlds stay untouched.
- **build-battle-themes:** canonical English names.

## Advent calendar

```yaml
advent:
  id: christmas-2026-advent   # seasonal_collectibles.theme_id
  year: 2026
  month: 12
  first-day: 1
  last-day: 24
  coins: 10
  last-day-coins: 50
```

- A click (or a tap on Bedrock) on the musician tells the song playing, then opens the gift of the day (date in the theme's zone).
- The opening is the row (`player`, `id`, `dNN`) of `seasonal_collectibles`. Its primary key makes it idempotent: a second click says "already opened, come back tomorrow" (or "merry Christmas" on the last day).
- The coins use the ledger key `theme:<id>:dNN`, so the coin transaction source is `goal:theme:<id>:dNN`.
- A day not opened on its date is lost. Before the first day and after the last one, the musician says the calendar is not open yet or is over (useful with `/theme set christmas` in November).
- On joining the lobby, a player who has not opened today's gift gets one short reminder (`advent.reminder`, Bedrock variant `advent.reminder.bedrock`).
- `/theme status` shows the calendar's state and today's gift.
- Message keys: `<messages>.advent.opened`, `.opened.last`, `.already`, `.already.last`, `.reminder`, `.reminder.bedrock`, `.soon`, `.over`, `.unavailable`.
- Pure date logic and its tests: `AdventCalendar`, `AdventCalendarTest`.

## Christmas 2026

`themes/christmas/theme.yml`, from 1 December 2026 00:00 to 5 January 2027 00:00 (Paris):

- **Lobby:** winter evening (client time 12800), no bats, gentle per-player snowfall, soft chimes, bell and fireplace crackle.
- **Decor:**
  - one snow layer on lawns, the garden ring (lime and green terracotta) and tree tops (90 %);
  - short grass and ferns become snow;
  - green carpets mostly white;
  - small flowers become red and white tulips;
  - lanterns and lit red or green candles on fence posts;
  - about one leaf in 11 becomes a glowing bauble (shroomlight, pearlescent froglight, lit redstone lamp), with END_ROD sparkles.
- **Musician snowman** where the Halloween witch stood (1.5, 13, -14.5), 9 public-domain carols written by the music track in `themes/christmas/music/`.
- **Advent calendar** `christmas-2026-advent`, 1 to 24 December.
- **Hunt** `christmas-2026`: 13 presents (red and lime wool) `g01`..`g13`, 15 coins each, 100 coins and `candy_cane_trail` (source `christmas-2026:hunt`) for all 13. The spots come from `analysis/christmas-20261107/survey/`.
- **No `games` section:** match worlds keep their normal sky and blocks.

## Adding a theme

1. Add the id to `src/main/resources/themes/index.yml`.
2. Create `themes/<id>/theme.yml` by copying the closest existing theme (see the reference above).
3. Put the songs in `themes/<id>/music/*.yml` (format: see `SongParser`).
   - Each instrument covers two octaves. A note out of range fails the tests (`SongParserTest`).
   - All voices must end together.
   - Only use public-domain compositions, or music you hold the rights to (public performance on a server that sells items).
4. Add the theme texts to all 8 bundles under `theme.<id>.*` (copy the keys of an existing theme, and the `advent.*` keys if it has a calendar). The `/musique` token must stay identical in every language.
5. For limited cosmetics: give them `season: <id>` in `CosmeticCatalog`, then:
   - add a website migration that widens the cosmetic CHECK constraints;
   - add the catalog/copy entries on the website (with the season window).
6. On the website, add the theme's row to `utils/seasonal-theme.ts`.
7. The Build Battle names of the seasonal themes go in the Build Battle bundles (`bb.theme.name.*`). The website gallery names are regenerated from them.

Hunt spots must be real full blocks with an exposed face. Pick them from the production lobby: `analysis/halloween-20261012/` and `analysis/christmas-20261107/survey/` keep the read-only survey scripts (map, walkable area, visibility, candidate spots). A spot whose block no longer matches is skipped and logged (`Theme hunt …: X/13 spots ready`).
