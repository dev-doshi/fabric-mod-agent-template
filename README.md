# example-mod — AI-agent-optimized Fabric mod dev environment

A Minecraft **Fabric** mod for **MC 26.1.2**, built as a seamless workspace for AI coding agents:
a vertical-slice starter (block, item, entity, command, networking, config, mixin — each with a
passing test), a full three-tier automated test harness, and a vendored offline docs corpus so an
agent can grep ground truth instead of hallucinating APIs.

> **New here (human or AI)? Read [CLAUDE.md](CLAUDE.md).** It explains the one thing that breaks
> most tutorials: Minecraft is **unobfuscated** since 26.1, so Yarn/mappings are gone and names are
> Mojang-official.

## Requirements
- **JDK 25** (Microsoft OpenJDK or Temurin). Check: `java -version`.
- Nothing else — the Gradle wrapper (9.5.1) and Loom fetch the rest.

## Quick start
```bash
./gradlew build            # compile + Tier 1 (unit) + Tier 2 (server gametest). Produces build/libs/*.jar
./gradlew runClient        # launch the dev client
./gradlew runServer        # launch a dev server
scripts/sync-docs.sh       # build the offline grounding corpus under .agent-docs/ (first run: minutes)
```

## Testing (exit code is the signal — no log scraping)
```bash
scripts/test.sh unit       # JUnit, seconds        (src/test/)
scripts/test.sh game       # server @GameTest, ~30s (src/gametest/, headless)
scripts/test.sh client     # client gametest, ~min  (headless render + screenshots)
scripts/test.sh            # unit + server (fast inner loop)
scripts/test.sh all        # everything
```
CI runs all three tiers — see [.github/workflows/build.yml](.github/workflows/build.yml).

## Sentinel anticheat (P0–P3: movement + combat + world/mining)
A dynamic, highly-configurable, EULA-compliant anticheat built into the mod and tested by the sim
harness spawning bots that *cheat on purpose*. See `src/main/java/com/example/sentinel/`.

- **Server-authoritative prediction**, not client trust: a mixin feeds `handleMovePlayer` to a check
  engine that compares each move to a lag-compensated, attribute-derived valid envelope.
- **Movement checks (P1):** Speed, Fly, NoFall, Timer. Default response is **setback + silent staff
  alert** (no auto-ban) — near-zero false-positive risk.
- **Combat checks (P2):** Reach, HitThroughWalls (line-of-sight — vanilla does *not* check this),
  KillAura (attack-angle — vanilla does not check facing), AutoClicker (CPS + click-interval
  regularity). Default response is **cancel the hit + alert**. Violation levels with decay + buffering.
- **World checks (P3):** FastBreak (times each break against vanilla's own `getDestroyProgress`
  oracle), Nuker (blocks/second cap), FastPlace (placements/second cap), and an **advisory** X-ray
  ore-ratio heuristic. X-ray is *not* provable server-side — that check never auto-punishes; it flags
  for staff review only. Real prevention is chunk obfuscation (P4, not yet built).
- **Configurable + hot-reload:** per-check `enabled`/`setbackVl`/`decay`/`buffer` in `sentinel.json`;
  `/sentinel reload|alerts|verbose|vl <player>`.
- **EULA-compliant:** behavioral only — no memory/host scanning; client attestation (deferred P5) is
  documented as friction, never proof. See the plan for the honest verdict on client checksums.
- **Proved by tests:** `SentinelMovementGameTest` spawns cheating bots (each check flags) and legit
  bots (zero false positives), all headless + CI-gated; plus Tier-1 JUnit on VL/config logic.

## Multiplayer simulation (concurrent players)
The harness can spawn **many real, fully-joined server players** and drive them through the actual
serverbound packet handlers — so your mod's server logic runs under realistic multiplayer load, not
against mocks. See `src/gametest/java/com/example/sim/`.

```java
Simulation sim = Simulation.spawn(level, 24, center, /*spread*/ 8.0, /*seed*/ 1234L)
    .start((player, tick, random) -> {
        player.moveTo(player.entity().position().add(rndStep(random)), true); // real movement packet
        if (tick % 20 == 0) player.swing();                                   // real interaction
        if (tick % 15 == 0) player.changeSettings(newClientInfo(random));      // real settings sync
    });
```

- **Realistic:** bots join via `PlayerList.placeNewPlayer` over a fake connection; movement,
  interaction, and settings all go through the real `ServerGamePacketListenerImpl`.
- **Deterministic:** seeded; actions land per-tick on the server thread.
- **Concurrency model:** the MC server is single-threaded, so "simultaneous" means same-tick
  interleaving — exactly how a real server drains many clients per tick. Verified with 24 bots.
- Two worked tests: `ExampleSimGameTest` (headless server swarm) and `ExampleSimClientGameTest`
  (one real client observes the swarm — GUI/HUD under load, captured as a screenshot).
- Not covered: separate per-client network stacks (that's external protocol bots — out of scope).

## What's in the box
| Feature | Files |
|---|---|
| Item + Block + creative tab | `src/main/java/com/example/item/` |
| Custom entity + NoopRenderer | `src/main/java/com/example/entity/`, `client/ExampleModClient.java` |
| Custom packet + server receiver | `src/main/java/com/example/networking/` |
| `/example` command | `src/main/java/com/example/command/ModCommands.java` |
| JSON config (unit-tested) | `src/main/java/com/example/config/ExampleConfig.java` |
| Server + client mixins | `src/main/java/com/example/mixin/`, `src/client/.../mixin/` |
| Multiplayer simulation harness | `src/gametest/java/com/example/sim/` |
| Grounding scripts | `scripts/find-mc.sh`, `find-api.sh`, `registry.sh` |

## Versions (single source of truth: `gradle.properties`)
MC 26.1.2 · Loader 0.19.3 · Loom 1.17.14 · Fabric API 0.154.2+26.1.2 · Java 25 · Gradle 9.5.1.

## Licensing & legal
- **This repo's code, config, and assets** are **CC0-1.0** (public domain dedication — see `LICENSE`).
  Use it for anything, no attribution required. The starter `icon.png` and Gradle wrapper originate
  from the CC0 `fabric-example-mod` and Apache-2.0 Gradle respectively.
- **The `.agent-docs/` corpus is NOT distributed with this repo.** It is `.gitignore`d and built
  locally on each machine by `scripts/sync-docs.sh`. This is deliberate and important:
  - **Decompiled Minecraft source is never committed or published.** Minecraft is proprietary;
    redistributing its (decompiled) code would violate the [Minecraft EULA](https://aka.ms/MinecraftEULA).
    `sync-docs.sh` decompiles a copy locally, for your own reference only — the same thing your IDE
    does. It stays on your disk.
  - Vendored docs each retain their upstream licenses (Fabric docs are CC BY-NC-SA 4.0, Fabric API is
    Apache-2.0, etc.). They are cloned locally, not re-hosted here.
- Minecraft and Fabric are trademarks of their respective owners; this project is unaffiliated.
