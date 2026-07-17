package com.example.networking;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import com.example.ExampleMod;

/**
 * Registers the {@link PingPayload} type and a server-side receiver.
 * Pattern verified against fabric-docs reference mod (ExampleModNetworkingBasic).
 */
public final class ModNetworking {
	public static void initialize() {
		// Payload types must be registered on BOTH sides (here, common init) before use.
		PayloadTypeRegistry.clientboundPlay().register(PingPayload.TYPE, PingPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(PingPayload.TYPE, PingPayload.CODEC);

		// Server receives a Ping from a client and logs it. The handler runs on the
		// server thread via the networking API's context.
		ServerPlayNetworking.registerGlobalReceiver(PingPayload.TYPE, (payload, context) -> {
			ExampleMod.LOGGER.info("[{}] Ping from {}: {}",
					ExampleMod.MOD_ID, context.player().getName().getString(), payload.message());
		});
	}

	private ModNetworking() {
	}
}
