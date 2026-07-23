package com.example.sentinel.checks;

import net.minecraft.server.level.ServerPlayer;

import com.example.sentinel.config.SentinelConfig;
import com.example.sentinel.core.BlockContext;
import com.example.sentinel.core.PlayerData;
import com.example.sentinel.core.Violation;
import com.example.sentinel.core.WorldCheck;

/**
 * Flags destroying more blocks per second than a survival player can (nuker). Complements
 * {@link FastBreakCheck}: that one times a single block, this one bounds the aggregate rate.
 */
public final class NukerCheck implements WorldCheck {
	/** Rolling window in server ticks (1 second). */
	private static final long WINDOW = 20;

	@Override
	public String id() {
		return "nuker";
	}

	@Override
	public SentinelConfig.CheckSettings settings(SentinelConfig cfg) {
		return cfg.nuker;
	}

	@Override
	public Violation detect(ServerPlayer player, PlayerData data, BlockContext ctx) {
		if (ctx.kind != BlockContext.Kind.BREAK_STOP || player.isCreative()) {
			return Violation.NONE;
		}
		int perSecond = data.recordBreakAndGetRate(ctx.tick, WINDOW);
		int max = SentinelConfig.get().maxBlocksPerSecond;
		if (perSecond > max) {
			double weight = 1.0 + Math.min(4.0, (perSecond - max) * 0.5);
			return Violation.of(id(), weight, String.format("%d blocks/s > %d", perSecond, max));
		}
		return Violation.NONE;
	}
}
