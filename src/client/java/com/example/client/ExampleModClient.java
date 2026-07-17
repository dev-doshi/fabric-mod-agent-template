package com.example.client;

import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.NoopRenderer;

import net.fabricmc.api.ClientModInitializer;

import com.example.ExampleMod;
import com.example.entity.ModEntities;

/**
 * Client entrypoint. Registered under "client" in fabric.mod.json.
 * Registers a zero-model {@code NoopRenderer} for our entity so it never crashes the client.
 */
public class ExampleModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ExampleMod.LOGGER.info("[{}] Initializing client entrypoint", ExampleMod.MOD_ID);
		EntityRenderers.register(ModEntities.EXAMPLE_ENTITY, NoopRenderer::new);
	}
}
