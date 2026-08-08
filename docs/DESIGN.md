# Design: Checkbox

Checkbox is a **client-side** Minecraft mod that adds a todo list with automatic progress
tracking, rendered as a customisable HUD overlay.

This document explains *how* the mod is built and *why* it is built that way. The normative,
testable behaviour lives in [SPEC.md](SPEC.md).

Target for this document: **Minecraft 26.2**, `main` branch, Fabric + NeoForge. Older
Minecraft versions are a later port (see [§10](#10-branches--porting)).

---

## 1. Goals and non-goals

### Goals

* Track three kinds of todo entry:
  1. **Text** — manually checked off ("Build a house").
  2. **Counter** — auto-tracked items collected or mobs killed ("Collect 8 oak logs",
     "Kill 10 zombies").
  3. **Timer** — a countdown that fires a notification when it reaches zero.
* Show the list on the HUD, with customisable position, size and appearance, and a
  visibility toggle bound to a key and a button.
* Work on **vanilla servers**. No server-side component, no custom packets, no registry
  additions, no dependency on server plugins.
* Persist lists per world/server, surviving restarts.
* Share one implementation across Fabric and NeoForge, following the structure already
  proven in [RedFX](../../RedFX/README.md).

### Non-goals (for v1.0)

* Server-authoritative or shared/party todo lists.
* Quest-chain logic (dependencies, rewards, unlocks).
* Tracking anything the client cannot observe (blocks mined by other players, advancement
  criteria internals, server-side scoreboard objectives).
* Data-pack or JSON-authored quest definitions.

### Guiding constraint

Everything the tracker knows, it learns from **packets the vanilla server already sends to
the client** or from **client-side game state**. This is what keeps the mod usable on
servers, and it is also the source of the mod's only real inaccuracies — documented
honestly in [§5.4](#54-known-inaccuracies).

---

## 2. Platform baseline (verified against 26.2)

Minecraft 26.2 completed the GUI rewrite. The facts below were read out of
`~/.gradle/caches/fabric-loom/26.2/minecraft-merged.jar` (Mojang mappings) and the loader
jars, not from memory — they drive most of the architecture:

| Fact | Consequence for Checkbox |
| :--- | :--- |
| `GuiGraphics` no longer exists; drawing goes through `net.minecraft.client.gui.GuiGraphicsExtractor` (`text`, `fill`, `blit`, `item`, `pose()` → `Matrix3x2fStack`) | All HUD/screen drawing uses the extractor; there is no `PoseStack`, only a 2D `Matrix3x2fStack` |
| HUD rendering is `Hud.extractRenderState(GuiGraphicsExtractor, DeltaTracker)`; `Gui` only owns screens/overlays now | The HUD hook attaches to the *extract* phase, not a render phase |
| Fabric API 0.154.0+26.2 ships `HudElementRegistry` + `HudElement.extractRenderState(GuiGraphicsExtractor, DeltaTracker)` | No mixin needed on Fabric |
| NeoForge 26.2.0.45-beta ships `RegisterGuiLayersEvent` + `GuiLayer.render(GuiGraphicsExtractor, DeltaTracker)` | No mixin needed on NeoForge |
| **Both loader HUD callbacks take the identical parameter list** | The renderer itself lives in `:common`; each loader contributes a 3-line adapter |
| `KeyMapping` now takes a `KeyMapping.Category` record, not a translation-key string; `KeyMapping.Category.register(Identifier)` creates one | Checkbox registers its own category `checkbox:main` |
| Fabric's helper is `KeyMappingHelper.registerKeyMapping` (renamed from `KeyBindingHelper`) | Per-loader keybind registration |
| `ClientboundDamageEventPacket` is a record exposing `entityId()`, `sourceCauseId()`, `sourceDirectId()`, `sourceType()` | Kill attribution is *packet-accurate*, including projectiles (see [§5.2](#52-mob-kill-tracking)) |
| `ClientboundTakeItemEntityPacket` exposes `getItemId()`, `getPlayerId()`, `getAmount()`; the handler **shrinks and then discards** the `ItemEntity` | A `TAIL` injection is too late to read the picked-up item; the injection point must be before `ItemStack.shrink` |
| `net.minecraft.resources.Identifier` (not `ResourceLocation`) | `Checkbox.id(String)` helper, same as RedFX |
| `StatsCounter.getValue(StatType<T>, T)`, `Stats.ITEM_PICKED_UP`, `Stats.ENTITY_KILLED` exist client-side | Optional server-stat reconciliation is possible (v1.1) |

---

## 3. Module and package layout

Mirrors RedFX so the two projects stay muscle-memory compatible.

```
Checkbox/
├── build.gradle              # plugin decls, shared repos, Modrinth token lookup
├── settings.gradle           # pluginManagement + include 'common','fabric','neoforge'
├── gradle.properties         # single source of truth for versions
├── common/                   # ALL logic. Loom-compiled against Mojang-named 26.2.
├── fabric/                   # ModInitializer + registration glue only
├── neoforge/                 # @Mod + registration glue only
├── docs/{DESIGN,SPEC}.md
└── release/                  # build output, published to Modrinth
```

`:common` package tree (`com.drinfonty.checkbox`):

```
checkbox/
├── Checkbox.java                  # MOD_ID, LOGGER, DEBUG flag, id(String)
├── CheckboxClient.java            # client lifecycle facade the loaders call into
├── model/
│   ├── TodoEntry.java             # sealed abstract base: id, text, order, scope, dirty
│   ├── TextEntry.java
│   ├── CounterEntry.java          # items + kills (one type, two match kinds)
│   ├── TimerEntry.java
│   ├── TodoList.java              # ordered list + mutation + dirty flag
│   ├── EntryScope.java            # WORLD | GLOBAL
│   └── EntryMatch.java            # ITEM | ITEM_TAG | ENTITY | ENTITY_TAG + registry id
├── store/
│   ├── TodoStore.java             # the two live lists, debounced atomic save
│   ├── TodoJson.java              # tolerant read/write of the list file format
│   ├── StoreScope.java            # WORLD (sp/<save> | mp/<host_port>) | GLOBAL
│   └── ScopeResolver.java         # the one store class that touches Minecraft
├── config/
│   └── CheckboxConfig.java        # HUD + behaviour settings, config/checkbox/config.json
├── track/
│   ├── TrackerManager.java        # tick entry point, routes events to entries
│   ├── ItemCensus.java            # inventory snapshot/diff for ACQUIRED & INVENTORY
│   ├── KillAttribution.java       # entityId -> last damage by local player + timestamp
│   └── TimerService.java          # countdown ticking, pause handling, expiry
├── hud/
│   ├── TodoHudRenderer.java       # the ONLY class that draws the HUD
│   ├── HudAnchor.java             # 9 anchors -> (x,y) resolution
│   └── HudLayoutCache.java        # pre-formatted rows, invalidated on change
├── client/gui/
│   ├── CheckboxScreen.java        # list manager
│   ├── EntryEditScreen.java       # add/edit one entry
│   ├── HudSettingsScreen.java     # appearance settings
│   └── HudPositionScreen.java     # drag-to-place editor
└── client/mixin/
    ├── ClientPacketListenerMixin.java
    └── LivingEntityMixin.java
```

### Why "everything in `:common`"

RedFX proved the pattern: `:common` is compiled by Loom against the Mojang-named Minecraft
jar, and NeoForge also runs Mojang mappings on 26.x, so the *same* compiled classes are
valid on both loaders. `:common` may reference Minecraft and Mixin, but **never** a loader
API. Only five things genuinely differ per loader, and each gets a thin adapter:

| Concern | Fabric | NeoForge |
| :--- | :--- | :--- |
| Mod entry point | `ClientModInitializer` | `@Mod` constructor, `Dist.CLIENT` guard |
| HUD attach | `HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT, …)` | `RegisterGuiLayersEvent.registerAbove(VanillaGuiLayers.CHAT, …)` |
| Keybinds | `KeyMappingHelper.registerKeyMapping` | `RegisterKeyMappingsEvent.register` |
| Client tick | `ClientTickEvents.END_CLIENT_TICK` | `ClientTickEvent.Post` |
| Config screen button | ModMenu `ModMenuApi` | `IConfigScreenFactory` extension point |
| Client commands (v1.1) | `ClientCommandRegistrationCallback` | `RegisterClientCommandsEvent` |

Every adapter forwards into `CheckboxClient` — `onClientTick()`, `onOpenManagerKey()`,
`onToggleHudKey()`, `renderHud(extractor, deltaTracker)`. No service-loader indirection: two
loaders do not justify the ceremony, and RedFX's direct approach has held up.

---

## 4. Data model

`TodoEntry` is a sealed abstract class with three subclasses. Shared state: `id` (UUID),
`text`, `createdAt`, `order`, `scope`, `completedAt`, and a per-entry dirty flag. `isDone()`
is abstract: text entries store it, counters derive it from `progress >= target`, timers from
`state == EXPIRED`.

The dirty flag lives on the entry rather than only on the list because trackers hold an entry
directly and have no reason to know which list owns it; a per-entry flag means a progress
update cannot be lost by forgetting to notify the list. `TodoList.isDirty()` is the union of
its own structural changes and its entries'.

* **TextEntry** — nothing but the checkbox.
* **CounterEntry** — `match` (`EntryMatch`), `target`, `progress`, `countMode`
  (`ACQUIRED` | `INVENTORY` | `PICKED_UP`, item entries only), `baseline` (an internal
  census snapshot; see below). Kill entries always behave like `ACQUIRED`.
* **TimerEntry** — `durationMillis`, `state` (`IDLE` | `RUNNING` | `PAUSED` | `EXPIRED`),
  `endsAtEpochMillis` (when running), `remainingMillis` (when paused/idle).

**Item and kill entries are one class, not two.** The only difference is whether `match.kind`
resolves against the item registry or the entity-type registry, and which event feeds it.
Keeping them unified means one progress-bar renderer, one completion path, one edit screen
layout, and one serialiser.

**`EntryMatch` holds the registry id as a `String`, not an `Identifier`.** That keeps the
whole model and store layer free of Minecraft classes, so it is unit testable in a plain JVM
with no game bootstrap — which is most of why the layer has real test coverage at all.
Resolution against the item and entity registries happens in `track/`, where a client and its
registries actually exist. Ids are normalised on the way in (lower-cased, `#` stripped, bare
paths defaulted to `minecraft:`) so `Oak_Log` and `minecraft:oak_log` are one entry.

Unresolvable ids (`minecraft:oak_log` on a client whose registry lacks it, or a modded id
after the mod is removed) are kept verbatim in JSON and rendered as a greyed-out row with a
warning tooltip rather than being dropped. Silently deleting a player's todo entry because a
registry lookup failed is the worst possible failure mode for this mod.

### Persistence

| File | Contents |
| :--- | :--- |
| `config/checkbox/config.json` | HUD + behaviour settings (global) |
| `config/checkbox/lists/global.json` | entries with `scope = GLOBAL` |
| `config/checkbox/lists/sp/<save-folder>.json` | singleplayer world list |
| `config/checkbox/lists/mp/<host>_<port>.json` | per-server list |

Scope is resolved on world join from `Minecraft.getSingleplayerServer()` (singleplayer) or
`Minecraft.getCurrentServer()` (multiplayer), sanitised to `[a-z0-9._-]`. Realms and
unresolvable connections fall back to `global`.

Writes are **debounced and atomic**: mutations set a dirty flag, a flush runs at most once
every 5 s and unconditionally on world unload and game close, and each flush writes
`<file>.tmp` then moves it into place. Gson with pretty-printing, `schemaVersion` field for
migrations, and a `load()` that repairs missing/invalid fields instead of throwing — the
same defensive posture as `RedfxConfig`.

---

## 5. Tracking design

This is the part with real engineering risk, because a client-only mod has to *infer* what a
server knows.

### 5.1 Item tracking

Three count modes; **`ACQUIRED` is the default** because it is what "collect 8 oak logs"
means to a player — progress that only goes up, whether the logs came from a tree, a
crafting grid, a chest, or a villager.

**`ACQUIRED` / `INVENTORY` — inventory census.** Every 5 client ticks (4 Hz), and only if at
least one item entry is active, `ItemCensus` walks the local player's inventory
(`Inventory.getNonEquipmentItems()` plus equipment slots) and totals the counts of the items
that some entry cares about. It compares against the previous snapshot:

* `INVENTORY` entries display the current total directly.
* `ACQUIRED` entries add every **positive** delta to `progress` and ignore negative ones.

The census set is a `Reference2IntMap<Item>` built from active entries, so the walk is ~46
slots with an identity lookup per non-empty stack — far below the noise floor of a tick.

The critical correctness detail is the **baseline**: the first census after a world join, a
respawn, or the creation of a new entry seeds the snapshot **without crediting anything**.
Without this, logging into a world with 3 stacks of oak logs would instantly complete
"collect 8 oak logs".

**`PICKED_UP` — ground pickups only.** Fed by a mixin on
`ClientPacketListener#handleTakeItemEntity`. The injection point matters: the vanilla body
calls `PacketUtils.ensureRunningOnSameThread` at the top (so a `HEAD` injection would run on
the **netty thread**), then shrinks the `ItemEntity`'s stack by `packet.getAmount()` and
discards the entity (so a `TAIL` injection can no longer read what was picked up).
Checkbox therefore injects at `INVOKE` on `ItemStack.shrink(I)V` and captures the stack with
MixinExtras' `@Local` — on the client thread, with the stack still intact. MixinExtras is
bundled by both Loom and NeoForge ModDev on 26.2.

### 5.2 Mob kill tracking

Two signals, combined:

1. **Attribution** — a mixin on `ClientPacketListener#handleDamageEvent` (`TAIL`, i.e. past
   the thread guard). `ClientboundDamageEventPacket` carries `entityId`, `sourceCauseId` and
   `sourceDirectId`; when either source id equals the local player's entity id,
   `KillAttribution` records `entityId → (gameTime, cause)`. Because `sourceCauseId` is the
   *causing* entity, an arrow or a splash potion attributes to the player who fired it, not
   to the projectile. This is the same data the vanilla death-message system uses.
2. **Death** — a mixin on `LivingEntity#tick` (`HEAD`) firing when `deathTime == 1`, the
   first tick of the death animation. This is the same edge-detection pattern RedFX already
   uses successfully for `hurtTime` on 26.2.

A kill is credited when a tracked entity type reaches `deathTime == 1` **and** an attribution
record for it exists within the last 200 ticks (10 s, configurable). Records are pruned on a
timer and on level unload.

Why not simpler alternatives:

* *Chat death messages* — vanilla emits none for mobs.
* *`LivingEntity#die`* — the client only reaches it via entity events, and not on every path.
* *"Nearest mob that died"* — miscounts badly in group PvE and with iron golems/wolves.

### 5.3 Optional stat reconciliation (v1.1, default off)

The client's own `StatsCounter` is populated by `ClientboundAwardStatsPacket`, which a vanilla
server sends on request (the same request the Statistics screen makes). With reconciliation
enabled, Checkbox requests stats every 60 s and computes
`serverProgress = currentStat − baselineStatAtEntryCreation` from `Stats.ENTITY_KILLED` /
`Stats.ITEM_PICKED_UP`, then takes `max(localProgress, serverProgress)`.

It can only ever *raise* progress. This backfills the one hole live detection cannot cover —
a mob that dies outside the client's view — without letting a server that has stats disabled
(returning zeroes) wipe out real progress. Off by default because it adds periodic server
traffic, and because `ITEM_PICKED_UP` semantics do not match the `ACQUIRED` default.

### 5.4 Known inaccuracies

Stated up front so they can be documented for users rather than discovered as bugs:

| Situation | Behaviour | Mitigation |
| :--- | :--- | :--- |
| Mob dies out of view / unloaded chunk (e.g. a fall-damage finish after a bow hit) | Not counted | Stat reconciliation (v1.1) |
| Player drops items and re-collects them (`ACQUIRED`) | Counted twice | Switch that entry to `INVENTORY` |
| Player dies and re-collects their drops (`ACQUIRED`) | Counted again | Documented; `INVENTORY` mode avoids it |
| Items withdrawn from a chest (`ACQUIRED`) | Counted | Intentional — matches "acquired" semantics |
| Kill assist where another player lands the final blow | Counted for us if we damaged it within the window | Accepted; matches how players think about it |
| Server strips damage events (rare anti-cheat plugins) | Kills undercount | Stat reconciliation (v1.1) |

---

## 6. HUD rendering

`TodoHudRenderer.render(GuiGraphicsExtractor, DeltaTracker)` is the single drawing surface.
Both loaders attach it directly — Fabric via a `HudElement`, NeoForge via a `GuiLayer` —
because 26.2 gave the two callbacks the same signature.

Layout pipeline per frame:

1. **Gate.** Skip if no level, no player, `options.hideGui` (F1), HUD toggled off, the debug
   overlay is open (configurable), or the position editor is already drawing its own preview.
2. **Layout from cache.** `HudLayoutCache` holds pre-formatted `Component` rows and their
   measured widths, rebuilt only when the list, a progress value, a timer second, or a
   config value changes. The render path itself allocates nothing per frame — timer rows are
   the only ones that re-format, and only when the displayed second changes.
3. **Anchor.** `HudAnchor` maps one of nine anchors plus a pixel offset to a top-left corner
   in scaled GUI space, using `extractor.guiWidth()/guiHeight()`.
4. **Scale.** `extractor.pose().pushMatrix()`, `translate(x, y)`, `scale(s, s)`, draw at the
   origin, `popMatrix()`. Scaling around the anchor (not the screen origin) is what keeps the
   widget visually pinned when the user changes size.
5. **Draw.** Optional panel background (`fill` with configurable ARGB), optional title row,
   then one row per visible entry: status glyph, text, and for counters a
   `progress/target` suffix with an optional inline bar. Overflow collapses into `+N more`.

Attachment point is *after* the chat layer, so the list sits above the HUD furniture but does
not fight the vanilla overlays for space. Everything about placement is user-controlled
anyway.

**Portability note:** `GuiGraphicsExtractor` does not exist before 26.x. Confining every draw
call to `TodoHudRenderer` (and the screens) means the eventual 1.21 port replaces one HUD
file rather than being scattered across the codebase.

---

## 7. Timers

`TimerService` ticks with the client. A running timer stores an absolute
`endsAtEpochMillis`, so display never drifts; a paused timer stores `remainingMillis`.
Transitions:

* **Game pause** (singleplayer `Minecraft.isPaused()`) → auto-pause, re-arm on resume. A
  countdown that runs while the game is frozen would be indefensible.
* **World unload / game close** → `pauseTimersOnQuit` (default `true`) converts running
  timers to `PAUSED` with the remaining time preserved. Set it to `false` for wall-clock
  timers that keep running while you are logged out.
* **Expiry** → play `UI_TOAST_CHALLENGE_COMPLETE` (configurable, muteable), push a
  `SystemToast`, flash the HUD row, and move the entry to `EXPIRED`. It stays visible and
  blinking until dismissed, because a countdown you miss is a countdown that failed.

Timer rows re-render at 1 Hz, not per frame.

---

## 8. Configuration and screens

`CheckboxConfig` follows `RedfxConfig`: a plain Gson-serialised object, a static `get()`,
field-by-field repair on load, and `resetToDefaults()`. Full key list in
[SPEC §6](SPEC.md#6-configuration).

Four screens, all built from vanilla widgets available in 26.2 (`ObjectSelectionList`,
`EditBox`, `Checkbox`, `AbstractSliderButton`, `LinearLayout`/`GridLayout`,
`HeaderAndFooterLayout`):

* **`CheckboxScreen`** — the manager. Scrollable entry list with per-row toggle, edit,
  reorder and delete; footer buttons for the four add-types, HUD settings, and a
  show/hide-HUD toggle (satisfying the "toggleable via a button" requirement without needing
  the keybind).
* **`EntryEditScreen`** — type-specific fields. Item and kill entries get an id field with
  registry-backed suggestions, a "use held item" / "use looked-at entity" shortcut, and a
  target count. Timers get `hh:mm:ss` inputs.
* **`HudSettingsScreen`** — anchor, offsets, scale, width mode, max rows, background style
  and opacity, text shadow, and completed-entry behaviour.
* **`HudPositionScreen`** — drag the live widget, snap to screen edges/centres, arrow keys
  nudge by 1 px (shift = 10), `Esc` cancels, `Enter` accepts. This is worth building rather
  than shipping raw X/Y spinners: nobody wants to guess pixel coordinates.

Reachable from ModMenu (Fabric), the NeoForge mods-list config button, and the `K` keybind.

---

## 9. Build, verification, publishing

Copied wholesale from RedFX, because it encodes hard-won knowledge about this toolchain:

* `gradle.properties` is the single source of version truth (`minecraft_version=26.2`,
  `loader_version=0.19.3`, `loom_version=1.17-SNAPSHOT`, `neoforge_version=26.2.0.45-beta`,
  `mc_version_suffix=mc26.2.x`, `maven_group=com.drinfonty.checkbox`).
* Java 25 toolchain and `options.release = 25` in all three modules.
* `:common` adds `compileOnly "net.fabricmc:fabric-loader"` purely to silence
  `EnvType.CLIENT` annotation warnings.
* Jars land in `release/` as `checkbox-<version>-mc26.2.x-<loader>.jar`; `:fabric` also
  copies to the local `mods/` folder.
* **`-PtestJar` is mandatory pre-publish verification on both loaders.** On 26.2 Fabric there
  is no `remapJar` step (Fabric publishes no intermediary mappings for 26.x), so the `jar`
  output *is* the artifact and a dev client is a faithful rehearsal. On NeoForge, `-PtestJar`
  must omit the `mods { … sourceSet … }` block and stage the jar in an isolated
  `run-testjar/` — otherwise FML's in-dev folder locator wins and silently tests the loose
  classes. That mistake shipped two broken RedFX releases; do not re-derive it.
* Modrinth publishing via Minotaur, token from `MODRINTH_TOKEN` or `local.properties`,
  rehearsable with `-PmodrinthDebug`.
* `-Dcheckbox.debug=true` gates verbose tracker diagnostics, set only by the non-`testJar`
  dev runs.

### Automated tests

`:common` gets a JUnit 5 `test` source set covering the logic that has no Minecraft
dependency: census diffing, `ACQUIRED` vs `INVENTORY` semantics, baseline seeding, timer
state transitions across pause/quit/expiry, anchor math, and store round-tripping including
schema repair of malformed JSON. These are exactly the parts where a regression is invisible
in a manual playtest — a mistake in baseline seeding looks like "the mod works" until someone
logs in with a full inventory.

---

## 10. Branches & porting

`main` targets 26.2. Legacy branches come later and follow RedFX's cherry-pick workflow
(implement on `main`, then `git cherry-pick` onto the legacy branch, re-deriving anything
that touches build config or mappings).

| Branch | Built against | Status |
| :--- | :--- | :--- |
| **`main`** | **26.2** | active |
| `legacy-26.1` | 26.1.2 | planned |
| `legacy-1.21` | 1.21.11 | planned |

Two things will **not** cherry-pick cleanly and are flagged now:

1. **HUD API.** 1.21 has `GuiGraphics`, `HudLayerRegistrationCallback` (Fabric) and
   `RegisterGuiOverlaysEvent`/`GuiLayer` variants (NeoForge) with different signatures.
   Isolating drawing in `TodoHudRenderer` keeps this to one file.
2. **Fabric mappings.** `legacy-1.21` uses `fabric-loom-remap` and publishes `remapJar`'s
   `intermediary` output, so `-PtestJar` is *not* a valid verification there — it needs
   `:fabric:runProdClient`, exactly as RedFX documents.

Also version-sensitive: `Identifier` was `ResourceLocation` before 26.x, and
`KeyMapping.Category` was a plain translation-key string.

---

## 11. Roadmap

| Version | Contents |
| :--- | :--- |
| **1.0.0** | Model + persistence, all four entry types, item/kill tracking, timers, HUD with full customisation, manager/edit/HUD-settings/position screens, two keybinds, ModMenu + NeoForge config entry |
| **1.1.0** | `/checkbox` client commands, item/entity **tag** matching (`#minecraft:logs`), quick-add from held item and looked-at entity, optional stat reconciliation, sorting/filtering, per-entry colours |
| **1.2.0** | `legacy-26.1` and `legacy-1.21` branches, list import/export, share entry to chat |

---

## 12. Open questions

* Should completed entries auto-archive after a configurable delay, or stay struck-through
  until manually cleared? Currently specced as configurable, defaulting to a 10 s fade.
* Is a per-entry "notify on completion" sound worth the config surface, or is one global
  toggle enough? Currently one global toggle.
* Realms world identity has no stable client-visible key; currently falls back to the global
  list. Worth revisiting if anyone asks.
