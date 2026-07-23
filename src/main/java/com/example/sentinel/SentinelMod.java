package com.example.sentinel;

import net.fabricmc.api.ModInitializer;

import com.example.ExampleMod;
import com.example.sentinel.command.SentinelCommand;
import com.example.sentinel.config.SentinelConfig;
import com.example.sentinel.core.CheckManager;

/**
 * Sentinel anticheat entrypoint. Server-authoritative, EULA-compliant (behavioral only — no memory
 * or host scanning). Loads config, wires the check engine into the packet path, and registers the
 * {@code /sentinel} staff command.
 */
public final class SentinelMod implements ModInitializer {
	@Override
	public void onInitialize() {
		SentinelConfig.load();
		CheckManager.init();
		SentinelCommand.register();
		ExampleMod.LOGGER.info("[sentinel] Anticheat engine online (4 movement + 4 combat checks).");
	}
}
