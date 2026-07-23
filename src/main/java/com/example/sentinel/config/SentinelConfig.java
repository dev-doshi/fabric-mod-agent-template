package com.example.sentinel.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

import com.example.ExampleMod;

/**
 * Sentinel anticheat config — plain data + gson, deliberately unit-testable without launching
 * Minecraft (see SentinelConfigTest, Tier 1). Follows the repo's {@code ExampleConfig} pattern.
 *
 * <p>Every check is individually tunable; {@link #reload()} re-reads from disk with no restart
 * (wired to {@code /sentinel reload}).
 */
public final class SentinelConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static SentinelConfig instance = new SentinelConfig();

	// --- Global ---
	public boolean enabled = true;
	/** Silent = detect + alert but do not setback (tuning mode). */
	public boolean silent = false;
	/** Extra per-flag console/chat spam for tuning (staff opt-in via /sentinel verbose). */
	public boolean verboseDefault = false;
	/** Flat leniency added to every movement envelope (blocks/tick) to absorb float error. */
	public double globalMovementTolerance = 0.08;
	/** Extra envelope slack per 50ms of measured ping (lag compensation). */
	public double tolerancePerPingTick = 0.03;

	// --- Per-check settings. Defaults chosen for a survival server: setback + alert, no auto-ban. ---
	public CheckSettings speed = new CheckSettings(true, 12.0, 4.0, 4);
	public CheckSettings fly = new CheckSettings(true, 10.0, 3.0, 3);
	public CheckSettings noFall = new CheckSettings(true, 8.0, 2.0, 2);
	public CheckSettings timer = new CheckSettings(true, 15.0, 5.0, 6);

	// --- Combat (P2) ---
	public CheckSettings reach = new CheckSettings(true, 6.0, 2.0, 2);
	public CheckSettings killAura = new CheckSettings(true, 6.0, 2.0, 2);
	public CheckSettings hitThroughWalls = new CheckSettings(true, 6.0, 2.0, 2);
	public CheckSettings autoClicker = new CheckSettings(true, 10.0, 3.0, 4);

	/** Max legal attack reach (blocks, eye-to-hitbox). Vanilla survival is 3.0. */
	public double maxReachBlocks = 3.0;
	/** Extra reach slack per 50ms ping (lag compensation). */
	public double reachPerPingTick = 0.02;
	/** Max angle (degrees) between look direction and the target when attacking. */
	public double maxAttackAngleDeg = 75.0;
	/** Clicks-per-second above which the auto-clicker check flags. */
	public double maxCps = 16.0;

	// --- World / mining (P3) ---
	public CheckSettings fastBreak = new CheckSettings(true, 8.0, 2.0, 2);
	public CheckSettings nuker = new CheckSettings(true, 8.0, 2.0, 2);
	public CheckSettings fastPlace = new CheckSettings(true, 8.0, 2.0, 3);
	/** X-ray is a review heuristic, not proof — never set it to auto-punish. */
	public CheckSettings xray = new CheckSettings(true, 999.0, 0.5, 1);

	/** Max blocks destroyed per second before the nuker check flags. */
	public int maxBlocksPerSecond = 8;
	/** Max block placements per second before the fast-place check flags. */
	public int maxPlacementsPerSecond = 12;
	/** Blocks mined before the X-ray ratio is evaluated. */
	public int xraySampleSize = 40;
	/** Ore fraction above which mining looks implausible (advisory). */
	public double maxOreRatio = 0.40;

	/** Tunables shared by every check. */
	public static final class CheckSettings {
		public boolean enabled;
		/** VL at which the setback/action pipeline fires. */
		public double setbackVl;
		/** VL removed per second of clean play. */
		public double decayPerSecond;
		/** Consecutive detections required before any VL is added (absorbs one-off jitter). */
		public int buffer;

		public CheckSettings(boolean enabled, double setbackVl, double decayPerSecond, int buffer) {
			this.enabled = enabled;
			this.setbackVl = setbackVl;
			this.decayPerSecond = decayPerSecond;
			this.buffer = buffer;
		}

		CheckSettings() {
			this(true, 10.0, 3.0, 3);
		}
	}

	public static SentinelConfig get() {
		return instance;
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("sentinel.json");
	}

	/** Load from the Fabric config dir, writing defaults if absent. Called from onInitialize. */
	public static void load() {
		Path path = path();
		try {
			if (Files.exists(path)) {
				try (Reader r = Files.newBufferedReader(path)) {
					instance = fromJson(r);
				}
				ExampleMod.LOGGER.info("[sentinel] Loaded config from {}", path);
			} else {
				instance = new SentinelConfig();
				save();
				ExampleMod.LOGGER.info("[sentinel] Wrote default config to {}", path);
			}
		} catch (IOException e) {
			ExampleMod.LOGGER.error("[sentinel] Config load failed, using defaults", e);
			instance = new SentinelConfig();
		}
	}

	/** Hot-reload from disk (no restart). Returns true on success. */
	public static boolean reload() {
		Path path = path();
		try {
			if (Files.exists(path)) {
				try (Reader r = Files.newBufferedReader(path)) {
					instance = fromJson(r);
				}
				return true;
			}
		} catch (IOException e) {
			ExampleMod.LOGGER.error("[sentinel] Config reload failed", e);
		}
		return false;
	}

	public static void save() throws IOException {
		Path path = path();
		Files.createDirectories(path.getParent());
		try (Writer w = Files.newBufferedWriter(path)) {
			GSON.toJson(instance, w);
		}
	}

	/** Parse from a reader. Null-safe AND malformed-safe: any parse error falls back to defaults. */
	public static SentinelConfig fromJson(Reader reader) {
		try {
			SentinelConfig c = GSON.fromJson(reader, SentinelConfig.class);
			return c != null ? c : new SentinelConfig();
		} catch (com.google.gson.JsonParseException e) {
			return new SentinelConfig();
		}
	}

	public String toJson() {
		return GSON.toJson(this);
	}
}
