# AGENTS.md

This repo's agent instructions live in **[CLAUDE.md](CLAUDE.md)** — read it first, every session,
regardless of which agent/tool you are.

TL;DR of what will trip you up if you skip it:
- Minecraft 26.1.2 is **unobfuscated**. Yarn is dead. **No `mappings` line.** Names are
  Mojang-official (`Level`, `Minecraft`, `Item.Properties`; `Identifier` kept as an alias).
- Build with `implementation`/`jar` and plugin id `net.fabricmc.fabric-loom` — never
  `modImplementation`/`remapJar` or bare `fabric-loom`.
- **Ground before you code:** `scripts/find-mc.sh <Symbol>` greps decompiled MC source
  (`.agent-docs/mc-src`). Never guess an API.
- **Prove every change:** `scripts/test.sh [unit|game|client]`. Exit code is truth.

Full details, project map, registration idioms, renames table, and gotchas: **[CLAUDE.md](CLAUDE.md)**.
