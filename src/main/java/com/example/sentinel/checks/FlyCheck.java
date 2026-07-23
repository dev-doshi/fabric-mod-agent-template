package com.example.sentinel.checks;

import net.minecraft.server.level.ServerPlayer;

import com.example.sentinel.config.SentinelConfig;
import com.example.sentinel.core.MoveContext;
import com.example.sentinel.core.MovementCheck;
import com.example.sentinel.core.PlayerData;
import com.example.sentinel.core.Violation;
import com.example.sentinel.physics.MovementPredictor;
import com.example.sentinel.physics.MovementSimulator;

/**
 * Flags vertical movement outside what gravity permits.
 *
 * <p>An airborne player's vertical velocity must decay as {@code (vy_prev - gravity) * 0.98}, so
 * hovering or climbing without support is provably illegal — no "hover for N ticks" heuristic
 * needed. Leaving the ground is bounded by the jump impulse, which catches step/jump hacks too.
 */
public final class FlyCheck implements MovementCheck {
	@Override
	public String id() {
		return "fly";
	}

	@Override
	public SentinelConfig.CheckSettings settings(SentinelConfig cfg) {
		return cfg.fly;
	}

	@Override
	public Violation detect(ServerPlayer player, PlayerData data, MoveContext ctx) {
		if (MovementPredictor.exempt(player) || player.hasEffect(net.minecraft.world.effect.MobEffects.SLOW_FALLING)) {
			return Violation.NONE;
		}
		boolean supported = MovementSimulator.hasGroundSupport(player, ctx.to);
		SentinelConfig cfg = SentinelConfig.get();
		// Vertical slack is deliberately much tighter than horizontal: it must stay under gravity
		// (0.08/tick), or hovering would read as legal falling.
		double tolerance = cfg.verticalTolerance + cfg.tolerancePerPingTick * ctx.pingTicks();

		double maxVy = MovementPredictor.maxVerticalVelocity(player, data.prevVerticalVelocity, supported, tolerance);
		if (ctx.dy > maxVy) {
			double overshoot = ctx.dy - maxVy;
			double weight = 1.0 + Math.min(4.0, overshoot * 10.0);
			return Violation.of(id(), weight, String.format(
					"dy=%.3f > max %.3f (prev vy %.3f, %s)",
					ctx.dy, maxVy, data.prevVerticalVelocity, supported ? "grounded" : "airborne"));
		}
		return Violation.NONE;
	}
}
