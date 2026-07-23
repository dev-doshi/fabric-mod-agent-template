package com.example.sentinel.core;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import com.example.sentinel.config.SentinelConfig;

/**
 * A server-side combat check, run when a player sends an attack packet against {@code target}.
 * Returns a {@link Violation} (or {@link Violation#NONE}) — like {@link MovementCheck}, checks are
 * pure detection; {@link CheckManager} owns buffering, VL, alerts, and cancelling the hit.
 */
public interface CombatCheck {
	String id();

	SentinelConfig.CheckSettings settings(SentinelConfig cfg);

	Violation detect(ServerPlayer attacker, Entity target, PlayerData data, long currentTick);
}
