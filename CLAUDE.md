# CLAUDE.md — read this first, every session

This is a **Minecraft Fabric mod** for **MC 26.1.2**, engineered so an AI agent can develop in it
without tripping over the ways LLM training data is wrong about modern Minecraft. Follow the loop
below and you will not hallucinate APIs.

---

## ⚠️ STOP: five facts your training data gets wrong (2026)

Minecraft became **unobfuscated** in 26.1 (March 2026) and switched to **calendar versioning**.
Almost every pre-2026 tutorial, StackOverflow answer, and your own priors are now WRONG. Specifically:

1. **Yarn is dead. There is NO `mappings` line in build.gradle.** Names are Mojang-official.
   - `Creeper` (not `CreeperEntity`), `Minecraft` (not `MinecraftClient`), `Level` (not `World`),
     `Item.Properties` (not `Item.Settings`), `ResourceLocation`… except Fabric kept the alias
     **`Identifier`** (`net.minecraft.resources.Identifier`, built via `Identifier.fromNamespaceAndPath`).
   - Packages are Mojang-style: `net.minecraft.world.level.block`, `net.minecraft.world.item`,
     `net.minecraft.world.entity`, `net.minecraft.core.registries`.
2. **Loom plugin id is `net.fabricmc.fabric-loom`** (no remapping). NOT bare `fabric-loom`
   (that silently means the legacy *remap* plugin now) and NOT `fabric-loom-remap` (≤1.21.11 only).
3. **Use `implementation` / `compileOnly` / `jar`** — NOT `modImplementation` / `modCompileOnly` /
   `remapJar`. Those are gone with obfuscation.
4. **Access wideners are "Class Tweakers"** (`.classtweaker`, `classTweaker v2 official` header),
   still wired via `loom.accessWidenerPath`.
5. **Versions:** Java **25**, Loom **1.17.14**, Gradle **9.5.1**, Loader **0.19.3**,
   Fabric API **0.154.2+26.1.2**. See `gradle.properties` — it is the single source of truth.

If something you write disagrees with the above, you are wrong. Verify with the loop below.

---

## The grounding loop — DO THIS before writing any Minecraft code

The decompiled Minecraft source is vendored locally and is the **ground truth**. Grep it. Never
guess a method name, package, or signature.

```bash
scripts/find-mc.sh Creeper            # grep decompiled MC source for a symbol
scripts/find-mc.sh -f GameTestHelper  # find the file, then read it
scripts/find-api.sh CreativeModeTabEvents   # grep Fabric API source (hooks/events/registries)
scripts/registry.sh block | grep torch      # is `minecraft:*_torch` a real id? list registry ids
```

- `.agent-docs/mc-src/common/…` and `.agent-docs/mc-src/client/…` — **decompiled MC 26.1.2**, ~6900
  `.java` files with real names AND parameter names. This is the best grounding that has ever existed
  for MC modding. When in doubt, **read the actual class**.
- `.agent-docs/fabric-api/…` — all 45 Fabric API modules' source + testmods.
- `.agent-docs/example-mod/…` and `.agent-docs/fabric-docs/reference/latest/…` — **compiling** 26.1.2
  mods. Copy their idioms verbatim; they are correct by construction.
- If `.agent-docs/` is missing, run **`scripts/sync-docs.sh`** (it decompiles MC + clones the docs).
  It is gitignored and reproducible. See `.agent-docs/INDEX.md`.

Concrete example of the loop catching a hallucination (this actually happened building this repo):
extending `Entity` — training data says override `readAdditionalSaveData(CompoundTag)`. Grep said
otherwise: `net.minecraft.world.level.storage.ValueInput`, and `Entity` has **four** abstract methods
(`defineSynchedData`, `hurtServer`, `readAdditionalSaveData`, `addAdditionalSaveData`). See
`src/main/java/com/example/entity/ExampleEntity.java`.

---

## The test loop — prove every change; exit code is truth

Three tiers, fast → slow. No log scraping — Gradle exit code is the pass/fail signal.

```bash
scripts/test.sh unit     # Tier 1: JUnit, seconds.  Logic/config/codecs. No game launch.
scripts/test.sh game     # Tier 2: server @GameTest, ~30s. Blocks/items/entities/commands. Headless.
scripts/test.sh client   # Tier 3: client gametest, ~min. GUI/input/rendering + screenshots.
scripts/test.sh          # Tier 1 + 2 (default fast inner loop)
scripts/test.sh all      # 1 + 2 + 3
```

- **Every new feature gets a test.** A server-side behavior → a `@GameTest` in `src/gametest/`.
  Client/render → a `FabricClientGameTest`. Pure logic → JUnit in `src/test/`.
- `./gradlew build` runs Tier 1 + Tier 2 automatically (gametest is wired into `check`).
- Client gametests are **deterministic**: the game is paused between `waitTick()`s, one server tick
  per client tick. Use `context.getInput()` for synthetic input and `assertScreenshotEquals(...)`
  for visual regression — an agent can verify rendering with no human watching.

### Multiplayer simulation (concurrent players)
For testing under realistic multiplayer load, the harness in `src/gametest/java/com/example/sim/`
spawns many **real, fully-joined server players** (no mocks) and drives them through the actual
serverbound packet handlers:
- `SimPlayer.join(level, name, pos)` — joins a bot via `PlayerList.placeNewPlayer` over a fake
  `SimConnection` (real `ServerGamePacketListenerImpl`; movement/interaction/settings all run for real).
- `SimPlayer` actions: `moveTo`, `swing`, `startBreak`, `changeSettings`, `leave` — each injects a
  real packet, so your mod's server logic (anticheat, events, player-list features) is exercised.
- `Simulation.spawn(level, count, center, spread, seed).start(behavior)` — orchestrates N bots under a
  per-tick behavior, pumped on `END_SERVER_TICK` (the server thread), deterministic via a seeded random.
- **Concurrency model:** the MC server is single-threaded; "simultaneous" = same-tick interleaving,
  which is exactly what a real server does draining many clients' packets per tick. The harness
  reproduces that faithfully. It does NOT simulate separate network stacks (that's external protocol
  bots — out of scope). Scale target: dozens (verified with 24).
- Examples: `ExampleSimGameTest` (server swarm, headless) and `ExampleSimClientGameTest` (one real
  client observes the swarm — GUI/HUD under load, with a screenshot).

### Verifying a mixin actually applied
Mixins fail silently. To confirm one applied: run with `-Dmixin.debug.export=true` and check that
your target class appears under `run/**/.mixin.out/`. Better: assert the mixin's *effect* in a
gametest. Prefer **MixinExtras** annotations (`@WrapOperation`, `@ModifyExpressionValue`, `@Local`)
over `@Redirect`; MixinExtras 0.5.4 is bundled in the loader — just import and use it.

---

## Project map

```
src/main/java/com/example/          COMMON (server+client) code
  sentinel/                         Sentinel anticheat (server-authoritative, EULA-compliant)
    core/ CheckManager, PlayerData, ViolationLevels, MoveContext, MovementCheck
    physics/ MovementSimulator      valid movement envelope (attributes + lag-comp)
    checks/ movement: Speed, Fly, NoFall, Timer
            combat:   Reach, HitThroughWalls, KillAuraAngle, AutoClicker
            world:    FastBreak, Nuker, FastPlace, XrayHeuristic (advisory)
    mixin/ ServerGamePacketListenerImplMixin   feeds handleMovePlayer to the engine
    config/ SentinelConfig (hot-reload)  command/ SentinelCommand  alert/ AlertSink
  ExampleMod.java                   main entrypoint; MOD_ID + id() helper
  config/ExampleConfig.java         gson JSON config — pure logic, Tier-1 tested
  item/ModItems.java, ModBlocks.java  item/block registration + creative tab
  entity/ExampleEntity.java, ModEntities.java  minimal Entity + type registration
  networking/PingPayload.java, ModNetworking.java  custom packet + server receiver
  command/ModCommands.java          /example greet | give   (Brigadier)
  mixin/MinecraftServerMixin.java   server mixin example
src/client/java/com/example/client/
  ExampleModClient.java             client entrypoint; registers NoopRenderer for the entity
  mixin/TitleScreenMixin.java       client mixin example
src/test/java/com/example/          Tier 1 JUnit (ExampleConfigTest, BootstrapExampleTest)
src/gametest/java/com/example/      Tier 2 server + Tier 3 client gametests
  sim/                              multiplayer sim harness (SimConnection/SimPlayer/Simulation)
  ExampleSimGameTest.java           server swarm (24 concurrent bots), headless
  ExampleSimClientGameTest.java     one real client observing the swarm (GUI under load)
src/main/resources/                 fabric.mod.json, example.mixins.json, assets/ (models, lang)
scripts/                            find-mc, find-api, registry, test, sync-docs
.agent-docs/                        vendored grounding corpus (gitignored)
```

Entrypoints live in `src/main/resources/fabric.mod.json` (main/client) and
`src/gametest/resources/fabric.mod.json` (`fabric-gametest`, `fabric-client-gametest`). Registering a
new initializer class means adding it to the relevant entrypoint list.

## Registration idioms (all verified against the reference mod — copy these)

- **Item:** `ResourceKey.create(Registries.ITEM, id)` → `factory.apply(props.setId(key))` →
  `Registry.register(BuiltInRegistries.ITEM, key, item)`. See `item/ModItems.java`.
- **Block:** same shape with `Registries.BLOCK` / `BuiltInRegistries.BLOCK`; register a matching
  `BlockItem` for it to be obtainable. See `item/ModBlocks.java`.
- **Entity type:** `EntityType.Builder.of(Factory::new, MobCategory.X).sized(w,h).build(key)`.
- **Creative tab add:** `CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.X).register(...)`.
- **Networking:** a `record … implements CustomPacketPayload` + `Type` + `StreamCodec`; register on
  both sides via `PayloadTypeRegistry.clientboundPlay()/serverboundPlay()`; receive with
  `ServerPlayNetworking.registerGlobalReceiver`.
- **Command:** `CommandRegistrationCallback.EVENT.register((dispatcher, access, env) -> …)`.
- **Entity renderer:** `EntityRenderers.register(type, Renderer::new)` (fabric-api transitively
  access-widens it; this is the recommended path, not the deprecated `EntityRendererRegistry`).

## Common renames (old name in your training data → 26.1 name)
`MinecraftClient`→`Minecraft` · `World`→`Level` · `Item.Settings`→`Item.Properties` ·
`ItemGroup`/`ItemGroupEvents`→`CreativeModeTab`/`CreativeModeTabEvents` ·
`HudRenderCallback`→`HudElementRegistry` · `CreeperEntity`→`Creeper` · `BlockPos`/`Identifier` unchanged.
When unsure, `scripts/find-mc.sh -f <OldOrNewName>` — the file either exists or it doesn't.

## Gotchas
- Don't copy old `build.gradle` files — the plugin id and `modImplementation` will be wrong.
- Pin `fabric_api_version` with the `+26.1.2` suffix; the maven `<latest>` tag is non-monotonic.
- `org.gradle.configuration-cache=false` must stay (IntelliJ/Loom incompatibility, Loom #1349).
- Decompiler is **Vineflower** (default) or CFR. FernFlower was removed in Loom 1.15 — ignore docs
  that still mention it.
- First `genSources` / `sync-docs.sh` run is slow (decompiles all of MC). Cached afterward.
```
