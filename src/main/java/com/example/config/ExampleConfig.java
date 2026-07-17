package com.example.config;

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
 * Minimal JSON config. Deliberately plain data + static helpers so the parse/serialize
 * logic is unit-testable WITHOUT launching Minecraft (see ExampleConfigTest, Tier 1).
 *
 * <p>gson ships with Minecraft ({@code com.google.gson}) — no extra dependency needed.
 */
public final class ExampleConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static ExampleConfig instance = new ExampleConfig();

	// --- Fields are the config schema. Defaults live here. ---
	public boolean greetingEnabled = true;
	public String greeting = "Hello from the example mod!";
	public int luckyNumber = 7;

	public static ExampleConfig get() {
		return instance;
	}

	/** Load from the standard Fabric config dir, writing defaults if absent. Called from onInitialize. */
	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve(ExampleMod.MOD_ID + ".json");
		try {
			if (Files.exists(path)) {
				try (Reader r = Files.newBufferedReader(path)) {
					instance = fromJson(r);
				}
				ExampleMod.LOGGER.info("[{}] Loaded config from {}", ExampleMod.MOD_ID, path);
			} else {
				instance = new ExampleConfig();
				save(path);
				ExampleMod.LOGGER.info("[{}] Wrote default config to {}", ExampleMod.MOD_ID, path);
			}
		} catch (IOException e) {
			ExampleMod.LOGGER.error("[{}] Config load failed, using defaults", ExampleMod.MOD_ID, e);
			instance = new ExampleConfig();
		}
	}

	public static void save(Path path) throws IOException {
		Files.createDirectories(path.getParent());
		try (Writer w = Files.newBufferedWriter(path)) {
			GSON.toJson(instance, w);
		}
	}

	// --- Pure, side-effect-free helpers: the unit-test surface. ---

	/** Parse config from any reader. Null/missing object -> defaults. */
	public static ExampleConfig fromJson(Reader reader) {
		ExampleConfig cfg = GSON.fromJson(reader, ExampleConfig.class);
		return cfg == null ? new ExampleConfig() : cfg.sanitized();
	}

	public String toJson() {
		return GSON.toJson(this);
	}

	/** Clamp/repair invalid values so a hand-edited config can't crash the game. */
	public ExampleConfig sanitized() {
		if (greeting == null || greeting.isBlank()) {
			greeting = "Hello from the example mod!";
		}
		// luckyNumber is constrained to 1..100.
		luckyNumber = Math.max(1, Math.min(100, luckyNumber));
		return this;
	}
}
