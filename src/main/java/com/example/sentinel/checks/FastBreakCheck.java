package com.example.sentinel.checks;

import net.minecraft.server.level.ServerPlayer;

import com.example.sentinel.config.SentinelConfig;
import com.example.sentinel.core.BlockContext;
import com.example.sentinel.core.PlayerData;
import com.example.sentinel.core.Violation;
import com.example.sentinel.core.WorldCheck;

/**
 * Flags blocks destroyed faster than the player's tool/effects physically allow.
 *
 * <p>The oracle is vanilla's own {@code BlockState.getDestroyProgress(player, level, pos)} — the
 * fraction of a block mined per tick. Its reciprocal is the minimum legal break time; a client that
 * reports STOP_DESTROY sooner than that is instant-mining (fast-break / nuker).
 */
public final class FastBreakCheck implements WorldCheck {
	/** Allow this fraction of slack for lag/rounding before flagging. */
	private static final double TOLERANCE = 0.6;

	@Override
	public String id() {
		return "fastbreak";
	}

	@Override
	public SentinelConfig.CheckSettings settings(SentinelConfig cfg) {
		return cfg.fastBreak;
	}

	@Override
	public Violation detect(ServerPlayer player, PlayerData data, BlockContext ctx) {
		if (ctx.kind != BlockContext.Kind.BREAK_STOP) {
			return Violation.NONE;
		}
		if (player.isCreative() || data.breakStartTick < 0 || data.breakStartPos == null
				|| !data.breakStartPos.equals(ctx.pos)) {
			return Violation.NONE; // no matching start (or creative insta-break) — nothing to time
		}
		float progressPerTick = ctx.state.getDestroyProgress(player, player.level(), ctx.pos);
		if (progressPerTick <= 0.0f || progressPerTick >= 1.0f) {
			return Violation.NONE; // unbreakable, or legitimately instant
		}
		double requiredTicks = 1.0 / progressPerTick;
		long elapsed = ctx.tick - data.breakStartTick;
		if (elapsed < requiredTicks * TOLERANCE) {
			double weight = 1.0 + Math.min(4.0, (requiredTicks - elapsed) * 0.3);
			return Violation.of(id(), weight, String.format(
					"broke %s in %d ticks, needs ~%.1f", ctx.state.getBlock().getName().getString(), elapsed, requiredTicks));
		}
		return Violation.NONE;
	}
}
