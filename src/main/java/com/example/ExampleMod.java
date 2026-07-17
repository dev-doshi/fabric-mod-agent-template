package com.example;

import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.command.ModCommands;
import com.example.config.ExampleConfig;
import com.example.entity.ModEntities;
import com.example.item.ModBlocks;
import com.example.item.ModItems;
import com.example.networking.ModNetworking;

/**
 * Common (server + client) entrypoint. Registered under "main" in fabric.mod.json.
 *
 * <p>NOTE (MC 26.1+): Minecraft is UNOBFUSCATED. Names here are Mojang-official
 * ({@code Identifier}, {@code Registry}, {@code BuiltInRegistries}). There is no Yarn / no mappings.
 * Ground every API against .agent-docs/mc-src (see CLAUDE.md).
 */
public class ExampleMod implements ModInitializer {
	public static final String MOD_ID = "example";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("[{}] Initializing common entrypoint", MOD_ID);

		ExampleConfig.load();
		ModItems.initialize();
		ModBlocks.initialize();
		ModEntities.initialize();
		ModNetworking.initialize();
		ModCommands.initialize();

		LOGGER.info("[{}] Common init complete", MOD_ID);
	}

	/** Build a namespaced {@link Identifier} under this mod's id. */
	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
