package com.example.sentinel.checks;

import net.minecraft.server.level.ServerPlayer;

import com.example.sentinel.config.SentinelConfig;
import com.example.sentinel.core.MoveContext;
import com.example.sentinel.core.MovementCheck;
import com.example.sentinel.core.PlayerData;
import com.example.sentinel.core.Violation;
import com.example.sentinel.physics.MovementPredictor;

/**
 * Flags horizontal movement outside the physically-reachable set for this tick.
 *
 * <p>Uses the {@link MovementPredictor} recurrence ({@code v_next ≤ v_prev * friction + accel})
 * rather than a flat speed cap, so it bounds acceleration as well as top speed. That is what catches
 * a cheat tuned to sit just under a naive limit: reaching that speed at all would require
 * acceleration the game cannot produce.
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
		if (MovementPredictor.exempt(player)) {
			return Violation.NONE;
		}
		SentinelConfig cfg = SentinelConfig.get();
		double tolerance = cfg.globalMovementTolerance + cfg.tolerancePerPingTick * ctx.pingTicks();
		boolean onGround = ctx.claimedOnGround || data.airborneTicks == 0;
		double limit = MovementPredictor.maxHorizontalSpeed(
				player, data.prevHorizontalSpeed, ctx.to, onGround, tolerance);

		if (ctx.horizontal > limit) {
			double overshoot = ctx.horizontal - limit;
			double weight = 1.0 + Math.min(4.0, overshoot * 8.0);
			return Violation.of(id(), weight, String.format(
					"h=%.3f > reachable %.3f (prev %.3f, +%.3f)",
					ctx.horizontal, limit, data.prevHorizontalSpeed, overshoot));
		}
		return Violation.NONE;
	}
}
