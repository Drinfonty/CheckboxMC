# Specification: Checkbox

Checkbox is a client-only Minecraft mod that provides a todo list with automatic progress
tracking, displayed on a customisable HUD overlay.

This document is normative: it defines observable behaviour, data formats, defaults and
acceptance criteria. Rationale and architecture live in [DESIGN.md](DESIGN.md).

Keywords: **MUST**, **SHOULD**, **MAY**.

---

## 1. Environment & support

* **Modern branch (`main`)**
  * **Minecraft**: 26.2 (Java 25)
  * **Fabric**: Loader `>=0.19.3`, Loom `1.17-SNAPSHOT`, Fabric API `0.154.0+26.2`
  * **NeoForge**: `26.2.0.45-beta`, ModDev `2.0.143`
* **Planned branches**: `legacy-26.1` (26.1.2), `legacy-1.21` (1.21–1.21.11) — not in scope
  for v1.0.
* **Side**: Client-only. The mod MUST run on vanilla and modded servers without a server-side
  component. It MUST NOT register blocks, items, entities, or custom network channels, and
  MUST NOT send any packet a vanilla client would not send.
* **Mod identity**: mod id `checkbox`, group `com.drinfonty.checkbox`, display name
  `Checkbox`.
* **Dependencies**: Fabric API (Fabric only). ModMenu is optional (`compileOnly`).

---

## 2. Entry types

Every entry has: a unique `id`, display `text`, a `done` state, a creation timestamp, a
position in the list, and a storage `scope`.

### 2.1 Text entry

* Free-text, manually checked off. Example: *"Build a house"*.
* Toggling `done` MUST be possible from the manager screen and MUST be reversible.
* Text MUST accept 1–128 characters. Empty text MUST be rejected at creation time.

### 2.2 Counter entry — items

* Example: *"Collect 8 oak logs"* → `match = ITEM minecraft:oak_log`, `target = 8`.
* `target` MUST be an integer in `1..9999`.
* Counters have **no editable description**. The label is generated for display as
  `Collect <target> <item name>` (`Kill <target> <entity name>` for §2.3) from the translated
  name and the *current* target, so editing the target updates the description and the label
  follows the player's language. The generated text is also stored, and is shown only when the
  id no longer resolves on that client — preserving the last name the entry was known by
  rather than falling back to a raw registry id.
* `progress` is clamped to `0..target`.
* **Count mode** (per entry):

| Mode | Semantics | Progress can decrease |
| :--- | :--- | :--- |
| **`ACQUIRED`** (default) | Every net increase of the item in the player's inventory is added | No |
| `INVENTORY` | Displays how many the player currently holds | Yes |
| `PICKED_UP` | Only items picked up off the ground | No |

* `ACQUIRED` and `INVENTORY` MUST be evaluated by an inventory census running every 5 client
  ticks, and only while at least one item entry is active.
* The census MUST cover the main inventory, hotbar, offhand and armour slots.
* The census MUST establish a **baseline without crediting progress** on: world join, player
  respawn, and creation of a new item entry. Logging in holding 64 oak logs MUST NOT advance
  "collect 8 oak logs".
* `PICKED_UP` MUST be driven by `ClientboundTakeItemEntityPacket`, reading the `ItemEntity`'s
  stack before the vanilla handler shrinks it, and crediting `packet.getAmount()`.
* An entry whose item id does not resolve in the client registry MUST be preserved, rendered
  greyed out, and MUST NOT crash or be deleted.

### 2.3 Counter entry — mob kills

* Example: *"Kill 10 zombies"* → `match = ENTITY minecraft:zombie`, `target = 10`.
* A kill MUST be credited when **both** hold:
  1. A death arrives for that entity — `ClientboundEntityEventPacket` with `EntityEvent.DEATH`.
  2. The local player is recorded as the damage cause for that entity within the
     **attribution window** (default 200 ticks / 10 s), from
     `ClientboundDamageEventPacket.sourceCauseId()` or `sourceDirectId()`.
* Projectile and thrown-potion kills MUST attribute to the player, not the projectile
  (`sourceCauseId` carries the shooter).
* Detection MUST NOT depend on client-side animation or rendering state. Those are the parts
  of the client other mods most often replace — a ragdoll mod removes the dying mob, and an
  animation-based check then never fires. The victim's type MUST therefore be recorded when
  the hit lands, so crediting the kill afterwards needs nothing but an entity id.
* Attribution records MUST be pruned when they exceed the window and on level unload.
* Kills by other players, by mobs, or from causes the local player did not contribute to
  MUST NOT be counted.
* Known gap: a mob that dies outside the client's view is not counted (see
  [§10](#10-known-limitations)).

### 2.4 Timer entry

* A countdown of `1 s .. 24 h`, entered as `hh:mm:ss`.
* States: `IDLE` → `RUNNING` ⇄ `PAUSED` → `EXPIRED`. Reset returns any state to `IDLE`.
* A running timer MUST NOT drift: remaining time is derived from an absolute end timestamp.
* The timer MUST auto-pause while the game is paused in singleplayer and resume on unpause.
* On world unload or game close, running timers MUST be converted to `PAUSED` with remaining
  time preserved, unless `pauseTimersOnQuit = false`, in which case they keep counting
  against wall-clock time.
* On expiry the mod MUST: play the completion sound (unless muted), show a toast, flash the
  HUD row, and hold the entry in `EXPIRED` (displaying `0:00`) until the player dismisses or
  resets it.
* Remaining time MUST be displayed as `h:mm:ss` above one hour, otherwise `m:ss`, and MUST
  update at 1 Hz.

### 2.5 Completion

* A counter entry MUST become `done` the moment `progress >= target`.
* On **automatic** completion the mod MUST play a sound (unless muted) and show a toast:
  * counters reaching their target → `block.note_block.pling`
  * timers running out → `entity.player.levelup`
* Ticking an entry off by hand in the manager MUST NOT announce — the click is its own
  feedback.
* An entry that is already satisfied the first time it is measured MUST NOT announce. Logging
  in holding enough for an `INVENTORY` entry is not an achievement.
* Completed entries are rendered struck-through and, per `completedBehaviour`, either fade
  off the HUD after `completedFadeSeconds` (default) or remain until cleared.
* Completed entries MUST remain in the manager screen until removed or `Clear completed` is
  used.
* An `INVENTORY` entry that falls back below `target` MUST return to not-done.

---

## 3. HUD

### 3.1 Visibility

The HUD MUST render only when all of these hold:

* a level and local player exist;
* `hudVisible` is `true`;
* the vanilla HUD is not hidden (F1);
* `hideWithDebugScreen` is `false` or the debug overlay is closed;
* `hideWhenScreenOpen` is `false` or no screen is open.

Visibility MUST be toggleable by:

* the **Toggle HUD** keybind (§5),
* a toggle button in the manager screen,
* the `hudVisible` config field.

The toggle MUST persist across restarts.

### 3.2 Placement and size

| Setting | Values | Default |
| :--- | :--- | :--- |
| `anchor` | `TOP_LEFT`, `TOP_CENTER`, `TOP_RIGHT`, `MIDDLE_LEFT`, `MIDDLE_CENTER`, `MIDDLE_RIGHT`, `BOTTOM_LEFT`, `BOTTOM_CENTER`, `BOTTOM_RIGHT` | `TOP_LEFT` |
| `offsetX`, `offsetY` | −4096..4096 scaled px from the anchor | `4`, `4` |
| `scale` | 0.5–2.0 | `1.0` |
| `widthMode` | `AUTO`, `FIXED` | `AUTO` |
| `fixedWidth` | 60–320 px | `140` |
| `maxVisibleEntries` | 1–20 | `8` |

* Scaling MUST be applied about the anchor point, so changing `scale` does not move the
  widget away from its corner.
* The widget MUST remain fully on screen for any GUI scale ≥ 1 at default offsets, and MUST
  be clamped to the screen bounds when a saved offset would push it entirely off-screen
  (e.g. after a resolution change).
* When more entries are visible than `maxVisibleEntries`, the overflow MUST be summarised as
  a final `+N more` row.

### 3.3 Appearance

| Setting | Values | Default |
| :--- | :--- | :--- |
| `backgroundStyle` | `NONE`, `SHADOW`, `PANEL` | `PANEL` |
| `backgroundOpacity` | 0–100 | `50` |
| `textShadow` | bool | `true` |
| `showTitle` | bool | `true` |
| `titleText` | ≤32 chars | `Checkbox` |
| `showProgressBar` | bool | `true` |
| `showCompleted` | bool | `true` |
| `completedBehaviour` | `FADE`, `KEEP` | `FADE` |
| `completedFadeSeconds` | 1–60 | `10` |

Row format:

```
[ ] Build a house
[ ] Collect 8 Oak Log       3/8 <icon>
    ▰▰▰▱▱▱▱▱
[x] Kill 10 Zombie        10/10 <icon>
[ ] Furnace batch            4:31
```

* Each row is led by a drawn checkbox: an empty square, or a green square with a tick once
  complete. It is drawn rather than blitted from Minecraft's `widget/checkbox` sprites, which
  are 20×20 with a 1px border and lose their bottom edge when sampled down to a 9px row.
* Completed entries turn **green** — label, value and checkbox together — rather than being
  greyed out or struck through. Finishing a tracked goal should read as an achievement, and
  strikethrough on a 9px font is illegible anyway.
* Counter rows MUST show `progress/target`; the bar is optional per `showProgressBar`.
* Tracked rows SHOULD show an icon at the right edge — the item itself, the mob's spawn egg
  for a kill counter, and a clock for a timer, so the tracked types line up. Items draw at
  16×16 and are scaled to 8px to fit the row. Tag matches and unresolvable ids show no icon;
  neither do plain text entries, which have no value column either.
* Timer rows MUST show remaining time and MUST blink while `EXPIRED`.

### 3.4 Ordering

Entries render in the user's manual order. Within that, incomplete entries MUST sort before
completed ones when `showCompleted` is enabled.

---

## 4. Screens

### 4.1 Manager (`CheckboxScreen`)

* Scrollable list of the active scope's entries. Each row exposes: toggle done (text
  entries), edit, move up, move down, delete.
* Footer actions: `Add Text`, `Add Counter (Item)`, `Add Counter (Kill)`, `Add Timer`,
  `HUD Settings`, `Show/Hide HUD`, `Clear Completed`, `Done`.
* Deleting an entry MUST require no confirmation for a single entry; `Clear Completed` MUST
  confirm.
* MUST be reachable from the **Open Checkbox** keybind, ModMenu (Fabric) and the NeoForge
  mods-list config button.

### 4.2 Entry editor (`EntryEditScreen`)

* Common: text field (1–128 chars), scope selector (`This world` / `Global`).
* Item entries: item id field with registry-backed suggestions, `Use held item` button,
  target count field, count-mode cycle button.
* Kill entries: entity id field with suggestions, `Use looked-at entity` button, target count.
* Timer entries: `hh`, `mm`, `ss` fields and a `Start immediately` checkbox.
* Invalid input MUST disable the confirm button and show an inline reason; it MUST NOT throw.

### 4.3 HUD settings (`HudSettingsScreen`)

Exposes every field in §3.2 and §3.3, plus `Move HUD…` and `Reset Defaults`.

### 4.4 HUD position editor (`HudPositionScreen`)

* Renders a live preview of the widget over a dimmed screen.
* Drag with the mouse to reposition; snap to screen edges and centre lines within 6 px.
* Arrow keys nudge 1 px, Shift+arrow 10 px.
* `Enter` accepts, `Esc` cancels and restores the previous position.
* The nearest anchor MUST be derived automatically from the drop position, with the residual
  stored as `offsetX`/`offsetY`, so the widget stays correctly pinned at other resolutions.

---

## 5. Key bindings

Registered under a Checkbox category (`checkbox:main`).

| Action | Translation key | Default |
| :--- | :--- | :--- |
| Open Checkbox | `key.checkbox.open` | `+` (the `=` key) |
| Toggle HUD | `key.checkbox.toggle_hud` | `J` |
| Quick-add held item (v1.1) | `key.checkbox.quick_add` | unbound |

Both defaults are unused by vanilla. Minecraft binds physical keys and cannot express a
modifier as part of a binding, so "Open Checkbox" is the `=` key and fires whether or not
shift is held. Key presses MUST be consumed via `consumeClick()` in the
client tick and MUST be ignored while a screen is open.

---

## 6. Configuration

File: `config/checkbox/config.json`, pretty-printed JSON, loaded once and saved on change.

```json
{
  "schemaVersion": 1,
  "hudVisible": true,
  "anchor": "TOP_LEFT",
  "offsetX": 4,
  "offsetY": 4,
  "scale": 1.0,
  "widthMode": "AUTO",
  "fixedWidth": 140,
  "maxVisibleEntries": 8,
  "backgroundStyle": "PANEL",
  "backgroundOpacity": 50,
  "textShadow": true,
  "showTitle": true,
  "titleText": "Checkbox",
  "showProgressBar": true,
  "showCompleted": true,
  "completedBehaviour": "FADE",
  "completedFadeSeconds": 10,
  "hideWhenScreenOpen": false,
  "hideWithDebugScreen": true,
  "playSounds": true,
  "showToasts": true,
  "defaultCountMode": "ACQUIRED",
  "killAttributionWindowTicks": 200,
  "pauseTimersOnQuit": true,
  "statReconciliation": false
}
```

* Loading MUST tolerate a missing file, an empty file, malformed JSON, unknown fields, and
  out-of-range values — each invalid field falls back to its default and the file is
  rewritten. A corrupt config MUST NOT prevent the game from starting.
* `resetToDefaults()` MUST restore every field above.

---

## 7. Storage format

| Path | Contents |
| :--- | :--- |
| `config/checkbox/lists/sp/<save-folder>.json` | singleplayer world list |
| `config/checkbox/lists/mp/<host>_<port>.json` | per-server list |
| `config/checkbox/lists/global.json` | entries scoped `GLOBAL` |

Scope keys MUST be sanitised to `[a-z0-9._-]`, with unresolvable connections (including
Realms) falling back to `global`.

```json
{
  "schemaVersion": 1,
  "entries": [
    {
      "id": "0f0a…", "type": "TEXT", "text": "Build a house",
      "order": 0, "scope": "WORLD",
      "done": false, "createdAt": 1765200000000, "completedAt": null
    },
    {
      "id": "1b2c…", "type": "COUNTER", "text": "Collect oak logs",
      "order": 1, "scope": "WORLD",
      "match": { "kind": "ITEM", "id": "minecraft:oak_log" },
      "target": 8, "progress": 3, "countMode": "ACQUIRED",
      "done": false, "createdAt": 1765200100000, "completedAt": null
    },
    {
      "id": "2c3d…", "type": "COUNTER", "text": "Kill zombies",
      "order": 2, "scope": "WORLD",
      "match": { "kind": "ENTITY", "id": "minecraft:zombie" },
      "target": 10, "progress": 10,
      "done": true, "createdAt": 1765200200000, "completedAt": 1765203000000
    },
    {
      "id": "3d4e…", "type": "TIMER", "text": "Furnace batch",
      "order": 3, "scope": "GLOBAL",
      "durationMillis": 300000, "state": "RUNNING",
      "endsAtEpochMillis": 1765203300000, "remainingMillis": null,
      "done": false, "createdAt": 1765203000000, "completedAt": null
    }
  ]
}
```

`match.kind` ∈ `ITEM`, `ENTITY` in v1.0; `ITEM_TAG`, `ENTITY_TAG` added in v1.1.

Persistence requirements:

* Saves MUST be debounced (at most one flush per 5 s) and MUST flush unconditionally on world
  unload and game shutdown.
* Saves MUST be atomic (write `<file>.tmp`, then move).
* An unreadable or malformed list file MUST be reported in the log and MUST NOT be
  overwritten until the player makes a change; the session starts with an empty list rather
  than destroying data.

---

## 8. Commands (v1.1)

Client-side commands, registered via `ClientCommandRegistrationCallback` (Fabric) and
`RegisterClientCommandsEvent` (NeoForge). They MUST never reach the server.

```
/checkbox add text <text…>
/checkbox add item <item> <count> [text…]
/checkbox add kill <entity> <count> [text…]
/checkbox add timer <duration> [text…]     # duration: 90s | 5m | 1h30m
/checkbox list
/checkbox done <index>
/checkbox remove <index>
/checkbox clear completed
/checkbox hud <show|hide|toggle>
```

---

## 9. Performance requirements

* HUD rendering MUST NOT allocate per frame: rows are formatted into a cache invalidated only
  by list, progress, config or displayed-second changes.
* The inventory census MUST run at most every 5 ticks and MUST be skipped entirely when no
  item entry is active.
* Tracking mixins MUST do O(1) work per packet and MUST NOT allocate on the netty thread.
* No file I/O on the render thread; saves are debounced off the hot path.
* Diagnostics MUST be gated behind `-Dcheckbox.debug=true` and compiled out via a
  `static final boolean` guard, following RedFX.

---

## 10. Known limitations

These are specified behaviour, not defects:

1. Mobs that die outside the client's view or in unloaded chunks are not counted (v1.1 stat
   reconciliation mitigates this).
2. In `ACQUIRED` mode, dropping and re-collecting items — including recovering your own death
   drops — counts again.
3. In `ACQUIRED` mode, withdrawing items from a container counts as acquiring them, by design.
4. If a server suppresses damage events, kill tracking undercounts.
5. Progress is per client installation. It does not sync between devices or players.

---

## 11. Acceptance criteria

A build satisfies v1.0 when all of the following pass on **both** loaders, verified with
`:fabric:runClient -PtestJar` and `:neoforge:runClient -PtestJar`:

**Tracking**

1. Create "collect 8 oak logs" with an empty inventory, chop 8 logs → entry completes at 8.
2. Log out and back in holding those logs → progress is unchanged (baseline seeding works).
3. Craft oak planks then re-craft logs is not required; withdrawing 4 oak logs from a chest
   advances an `ACQUIRED` entry by 4.
4. Switch the same entry to `INVENTORY`, drop the logs → progress drops accordingly.
5. Create "kill 10 zombies"; melee kills, bow kills and splash-potion kills all count; a
   zombie killed by another player or burned by daylight untouched by you does not.
6. Progress and completion states survive a client restart.

**Timers**

7. A 30 s timer reaches zero within ±1 s of wall clock, plays the sound, toasts, and blinks.
8. Opening the singleplayer pause menu freezes the countdown; closing it resumes.
9. Quitting to title with `pauseTimersOnQuit = true` preserves the remaining time.

**HUD**

10. All nine anchors place the widget correctly at GUI scales 1–4 and at 1280×720 and
    2560×1440.
11. Scale 0.5 and 2.0 keep the widget pinned to its anchor.
12. The toggle keybind, the manager button, and the config field all toggle visibility, and
    the state persists across a restart.
13. F1 hides the widget; the vanilla HUD is otherwise unaffected.
14. Drag-positioning stores an anchor plus offset that survives a resolution change.

**Robustness**

15. Deleting `config/checkbox/` and starting the game recreates defaults with no error.
16. A truncated list file logs a warning, starts empty, and does not overwrite the file until
    the player edits the list.
17. An entry referencing an unknown item id renders greyed out and is preserved on save.
18. With 200 entries and 20 visible, no measurable frame-time regression (< 0.2 ms/frame HUD
    cost).
19. Joining a vanilla server produces no server-side error and no kick; the mod sends no
    non-vanilla packet.
