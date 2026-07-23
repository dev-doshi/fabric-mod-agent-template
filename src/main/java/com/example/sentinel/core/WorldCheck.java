package com.example.sentinel.core;

import net.minecraft.server.level.ServerPlayer;

import com.example.sentinel.config.SentinelConfig;

/**
 * A server-side world-interaction check (mining / building). Pure detection, like
 * {@link MovementCheck} and {@link CombatCheck}; {@link CheckManager} owns VL and responses.
 */
public interface WorldCheck {
	String id();

	SentinelConfig.CheckSettings settings(SentinelConfig cfg);

	Violation detect(ServerPlayer player, PlayerData data, BlockContext ctx);
}
