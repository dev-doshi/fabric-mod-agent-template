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

## What's in the box
| Feature | Files |
|---|---|
| Item + Block + creative tab | `src/main/java/com/example/item/` |
| Custom entity + NoopRenderer | `src/main/java/com/example/entity/`, `client/ExampleModClient.java` |
| Custom packet + server receiver | `src/main/java/com/example/networking/` |
| `/example` command | `src/main/java/com/example/command/ModCommands.java` |
| JSON config (unit-tested) | `src/main/java/com/example/config/ExampleConfig.java` |
| Server + client mixins | `src/main/java/com/example/mixin/`, `src/client/.../mixin/` |
| Grounding scripts | `scripts/find-mc.sh`, `find-api.sh`, `registry.sh` |

## Versions (single source of truth: `gradle.properties`)
MC 26.1.2 · Loader 0.19.3 · Loom 1.17.14 · Fabric API 0.154.2+26.1.2 · Java 25 · Gradle 9.5.1.

## License
Code is CC0-1.0 (see `LICENSE`). The vendored `.agent-docs/` corpus keeps its upstream licenses.
