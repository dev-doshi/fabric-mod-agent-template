package com.example.sentinel.core;

import net.minecraft.server.level.ServerPlayer;

import com.example.sentinel.config.SentinelConfig;

/**
 * A server-side movement check. Given the player, their rolling {@link PlayerData}, and the current
 * {@link MoveContext}, return a {@link Violation} (or {@link Violation#NONE} if the movement is
 * within the physically-valid, lag-compensated envelope).
 *
 * <p>Checks are pure detection: they never mutate world state or punish. {@link CheckManager} owns
 * buffering, VL accounting, and the setback/alert pipeline.
 */
public interface MovementCheck {
	String id();

	SentinelConfig.CheckSettings settings(SentinelConfig cfg);

	Violation detect(ServerPlayer player, PlayerData data, MoveContext ctx);
}
