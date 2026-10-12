# Seasonal themes (Halloween, Christmas, …)

A seasonal theme dresses up the network for a few weeks **without modifying any world, map
archive or persistent entity**. Everything it shows lives in players' clients or in memory:

| What | How | Why nothing has to be restored |
|---|---|---|
| Lobby decor | Fake blocks sent per chunk (`FakeBlockRenderer`), re-checked against the real block at send time | The server world never changes; switching off re-sends the real blocks |
| Lamps in match worlds | Same-shape swaps computed lazily per chunk (`PaletteLayer`) | Match worlds are disposable copies anyway; nothing is written |
| Night / sunset | `Player#setPlayerTime` per player | World time stays frozen at noon |
| Musician, bats | Non-persistent entities marked with `LobbyEntityOwnership` | Never saved; startup cleanup removes leftovers |
| Music box | Note block sounds played per listener, category "Jukebox/Note Blocks" | No resource pack |
| Texts | Tab header/footer, MOTD line 2, lobby scoreboard title and line, arrival subtitle | Computed live |
| Collectible hunt | Real decor blocks shown as pumpkins (or presents) until found; finds in `seasonal_collectibles` | Rows are history, by design |
| Limited cosmetics | `CosmeticDefinition.season`: on sale only while that theme is active | Owners keep them forever |
| Build Battle | `ThemeService.buildBattleThemes()`: one seasonal theme in every vote | — |

Safety rules built in:
- A fake block never supports a player. If they stand on one, the real block is sent back, so they can't be kicked for flying.
- A click on a fake block re-sends that chunk's fakes, because the server answers clicks with the real block.
- Fake blocks only replace blocks of the same shape, or fill air out of reach (cobwebs at least two blocks above the floor).
- Build Battle is excluded from lamp swaps: players build with those blocks.

## Switching

- `config.yml` → `themes.mode: auto` (default) follows each theme's `window` (Europe/Paris).
- `/theme set off` switches every theme off at once, live, and persists the choice.
- `/theme set <id>` forces a theme outside its dates (e.g. for a preview on the test server).
- `/theme status` shows the active theme, its window, the number of client-side decorations and the song playing.
- `/theme reload` re-reads the theme files.
- `/theme music next` skips to the next song.

Players use `/musique` (aliases `/music`, `/musica`, `/musik`) to switch the music box off or on.

## Adding a theme (e.g. Christmas)

1. Add the id to `src/main/resources/themes/index.yml`.
2. Create `themes/christmas/theme.yml` by copying `themes/halloween/theme.yml`:
   - **window:** start and end (Europe/Paris).
   - **texts:** scoreboard title and ASCII MOTD line.
   - **lobby:** client time, decor rules, bats (use `0` for Christmas) and ambient sounds.
     - Decor rules work as swaps (leaves → snowy look…), toppers on posts (lanterns, presents), ground cover (`snow[layers=1]`), hanging blocks and ceiling corners.
     - YAML 1.1 reads `on` as a boolean, so the rules use `over`/`under`.
   - **music:** where the musician stands and the playlist.
   - **hunt:** a new edition id (`christmas-2026`), the spots (`real` = real block type, checked at start) and the rewards.
   - **games:** client time and the lamp palette.
   - **build-battle-themes:** canonical English names.
3. Put the songs in `themes/christmas/music/*.yml` (format: see `SongParser`).
   - Each instrument covers two octaves. A note out of range fails the tests (`SongParserTest`).
   - All voices must end together.
   - Only use public-domain compositions, or music you hold the rights to (public performance on a server that sells items).
4. Add the theme texts to all 8 bundles under `theme.christmas.*` (copy the `theme.halloween.*` keys). The `/musique` token must stay identical in every language.
5. For limited cosmetics: give them `season: christmas` in `CosmeticCatalog`, then:
   - add a website migration that widens the cosmetic CHECK constraints;
   - add the catalog/copy entries on the website (with the season window).
6. On the website, add the Christmas row to `utils/seasonal-theme.ts`.
7. The Build Battle names of the seasonal themes go in the Build Battle bundles (`bb.theme.name.*`). The website gallery names are regenerated from them.

Hunt spots must be real full blocks with an exposed face. Pick them from the production lobby: `analysis/halloween-20261012/` keeps the read-only survey scripts used for Halloween (map, walkable area, visibility). A spot whose block no longer matches is skipped and logged (`Theme hunt …: X/13 spots ready`).
