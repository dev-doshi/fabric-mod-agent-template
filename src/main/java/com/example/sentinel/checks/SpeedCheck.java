package com.example.sentinel.checks;

import net.minecraft.server.level.ServerPlayer;

import com.example.sentinel.config.SentinelConfig;
import com.example.sentinel.core.MoveContext;
import com.example.sentinel.core.MovementCheck;
import com.example.sentinel.core.PlayerData;
import com.example.sentinel.core.Violation;
import com.example.sentinel.physics.MovementSimulator;

/**
 * Flags horizontal movement faster than the player's lag-compensated, attribute-derived speed
 * envelope allows (speedhack, teleport, bhop beyond physics).
 */
public final class SpeedCheck implements MovementCheck {
	@Override
	public String id() {
		return "speed";
	}

	@Override
	public SentinelConfig.CheckSettings settings(SentinelConfig cfg) {
		return cfg.speed;
	}

	@Override
	public Violation detect(ServerPlayer player, PlayerData data, MoveContext ctx) {
		if (MovementSimulator.speedExempt(player)) {
			return Violation.NONE;
		}
		double limit = MovementSimulator.horizontalLimit(player, ctx, SentinelConfig.get());
		if (ctx.horizontal > limit) {
			double overshoot = ctx.horizontal - limit;
			double weight = 1.0 + Math.min(4.0, overshoot * 8.0);
			return Violation.of(id(), weight,
					String.format("hspeed %.3f > %.3f (+%.3f)", ctx.horizontal, limit, overshoot));
		}
		return Violation.NONE;
	}
}
