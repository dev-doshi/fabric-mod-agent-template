package com.example.sentinel.checks;

import net.minecraft.server.level.ServerPlayer;

import com.example.sentinel.config.SentinelConfig;
import com.example.sentinel.core.MoveContext;
import com.example.sentinel.core.MovementCheck;
import com.example.sentinel.core.PlayerData;
import com.example.sentinel.core.Violation;
import com.example.sentinel.physics.MovementSimulator;

/**
 * Flags sustained airborne hovering or ascent with no legal cause (fly/hover). A player with no
 * ground support who is not falling for several consecutive ticks is not obeying gravity.
 *
 * <p>{@code data.airborneTicks} is maintained by {@link com.example.sentinel.core.CheckManager}
 * before checks run.
 */
public final class FlyCheck implements MovementCheck {
	/** Airborne ticks without falling before we consider it flight (absorbs jump apex/landing). */
	private static final int GRACE_TICKS = 3;

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
		if (MovementSimulator.flyExempt(player)) {
			return Violation.NONE;
		}
		boolean airborne = !MovementSimulator.hasGroundSupport(player, ctx.to);
		if (!airborne) {
			return Violation.NONE;
		}
		// Not falling (hovering or rising) while airborne past the grace window => flight.
		boolean notFalling = ctx.dy >= -0.005;
		if (notFalling && data.airborneTicks >= GRACE_TICKS) {
			double weight = 1.0 + Math.min(4.0, Math.max(0.0, ctx.dy) * 12.0 + 1.0);
			return Violation.of(id(), weight,
					String.format("airborne %d ticks, dy=%.3f, no support", data.airborneTicks, ctx.dy));
		}
		return Violation.NONE;
	}
}
