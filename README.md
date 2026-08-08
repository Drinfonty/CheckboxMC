# Checkbox

Checkbox is a client-side Minecraft mod that adds a todo list with automatic progress
tracking, drawn as a customisable HUD overlay. Entries can be plain text ("build a house"),
an auto-tracked counter ("collect 8 oak logs", "kill 10 zombies"), or a countdown timer.
Because it is entirely client-side, it works on vanilla servers.

It is built as a multi-platform project with a shared `:common` codebase supporting both
**Fabric** and **NeoForge**.

---

## Status

Project scaffolding only. The build, module layout and mod metadata are in place and both
jars build and load; the features below are specified but not yet implemented.

For feature specifications and design details, see:
*   [docs/SPEC.md](docs/SPEC.md): Normative behaviour, data formats, defaults, acceptance criteria.
*   [docs/DESIGN.md](docs/DESIGN.md): Architecture, tracking design, and the reasoning behind it.

## Planned Features
- **Three entry types**: manual text entries, auto-tracked counters for items collected and
  mobs killed, and countdown timers.
- **Client-side tracking**: item progress from an inventory census, kills attributed from
  `ClientboundDamageEventPacket`, so no server-side component is needed.
- **Customisable HUD**: nine anchors plus pixel offsets, adjustable scale, drag-to-place
  editor, and visibility toggled by keybind or button.
- **Per-world lists**: entries are stored per save and per server, with an optional global list.

---

## Branch & Minecraft Version Mapping

The project maintains different branches to target different major Minecraft and loader
versions:

| Branch Name | Built Against | Supported Minecraft | Mod Version | NeoForge | Java |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **`main`** | **`26.2`** | `26.2` | `0.1.0` | `26.2.0.45-beta` | 25 |

"Supported Minecraft" is the range declared in `minecraft_dependency`, and is what
`modrinth_game_versions` publishes. Only the "Built Against" version is compiled and
launched during verification.

Legacy branches (`legacy-26.1`, `legacy-1.21`) are planned but not created yet. Two things
will **not** port cleanly and must be re-derived per branch: the HUD API (26.x replaced
`GuiGraphics` with `GuiGraphicsExtractor` and moved HUD drawing to `Hud.extractRenderState`),
and Fabric mappings (`legacy-1.21` needs `fabric-loom-remap` and publishes `remapJar`'s
`intermediary` output, which changes how a jar must be verified).

### Fabric mapping namespaces

| Branch Name | Loom | Loom Plugin | Fabric Production Namespace | Verify Fabric With |
| :--- | :--- | :--- | :--- | :--- |
| **`main`** | 1.17 | `fabric-loom` | Mojang (`official`) — no remap step | `:fabric:runClient -PtestJar` |

Fabric does not publish intermediary mappings for Minecraft 26.x, so on `main` the `jar`
output *is* the publishable artifact and the dev client runs in the same namespace as
production. NeoForge runs Mojang mappings on every branch, so its jar is never remapped.

---

## Building the Mod

- **Build Fabric Mod:**
  ```bash
  ./gradlew :fabric:build
  ```
- **Build NeoForge Mod:**
  ```bash
  ./gradlew :neoforge:build
  ```
- **Build All Subprojects:**
  ```bash
  ./gradlew build
  ```

Output binaries are placed under `${project.rootDir}/release/`:
- Fabric: `release/checkbox-<version>-<mc-suffix>-fabric.jar`
- NeoForge: `release/checkbox-<version>-<mc-suffix>-neoforge.jar`

The Fabric build also deploys its jar to `~/.minecraft/mods`, replacing any previous
`checkbox-*.jar` there.

---

## Running and Testing

### 1. Standard Development Run

Loads Minecraft from your workspace compilation output (unpackaged classpath), with
`-Dcheckbox.debug=true` set for verbose tracker diagnostics:

- **Fabric Dev Client:**
  ```bash
  ./gradlew :fabric:runClient
  ```
- **NeoForge Dev Client:**
  ```bash
  ./gradlew :neoforge:runClient
  ```

### 2. Pre-Publish Verification

**Always run these before publishing.** A dev client is not enough to tell you a jar works.

- **Fabric — packaged jar:**
  ```bash
  ./gradlew :fabric:runClient -PtestJar
  ```
- **NeoForge — packaged jar:**
  ```bash
  ./gradlew :neoforge:runClient -PtestJar
  ```

`-PtestJar` also drops the debug system property, so the run is a faithful rehearsal of what
a player sees.

#### Why `-PtestJar` is sufficient on this branch

Fabric publishes no intermediary mappings for Minecraft 26.x, so this branch uses the
`fabric-loom` plugin, there is no `remapJar` step, and the `jar` output is the publishable
artifact. The dev client runs in the **same** Mojang namespace as production, so a mixin that
resolves here resolves for a user. This is **not** portable to a 1.21 branch, where the
published jar is remapped to `intermediary` and a production jar loaded into a dev client
cannot match its own mixin targets — Mixin logs `@Mixin target ... was not found`, skips
every mixin, and the client still reaches the main menu looking healthy.

#### NeoForge `-PtestJar`

NeoForge runs Mojang mappings in both dev and production, so `-PtestJar` is faithful — but it
must actually load the jar. Declaring source sets in `neoForge.mods` hands them to FML's
in-dev folder locator, which **wins over** a jar in `mods/`. Check the log says:

```
 - checkbox (jar(mods/checkbox-<version>-<mc>-neoforge.jar))
```

and *not* `composite(folder(...build/classes/java/main), ...)`. The build arranges the former
by omitting the `mods` block under `-PtestJar` and staging the jar as the only mod in an
isolated `neoforge/run-testjar/`.

---

## Publishing

Modrinth publishing via the Minotaur plugin is wired up but **inactive**: no Modrinth project
exists yet, so `modrinth_project_id` in `gradle.properties` is empty and the `modrinth` task
is not configured. To enable it, create the project, set the id, and put your token in
`local.properties` (git-ignored) as `modrinth.token=...` or in the `MODRINTH_TOKEN`
environment variable. Rehearse a publish without uploading:

```bash
./gradlew modrinth -PmodrinthDebug
```

---

## Development Workflow & Cross-Branch Porting

1. **Implement on `main` first**, and test there.
2. **Commit and push** to `main`.
3. **Port to legacy branches by cherry-picking**:
   ```bash
   git checkout legacy-1.21
   git cherry-pick <commit-hash-from-main>
   ```
4. **Resolve conflicts.** Build configuration is **not** uniformly portable between branches.
   Anything touching mappings, mixins, HUD APIs, or which task produces the published jar has
   to be re-derived per branch rather than cherry-picked blindly.
5. **Verify locally before pushing**:
   ```bash
   ./gradlew clean build
   ./gradlew :fabric:runClient -PtestJar
   ./gradlew :neoforge:runClient -PtestJar
   ```
6. **Push** once verification succeeds.
