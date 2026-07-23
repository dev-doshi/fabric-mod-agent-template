package com.example.sentinel.checks;

import net.minecraft.server.level.ServerPlayer;

import com.example.sentinel.config.SentinelConfig;
import com.example.sentinel.core.BlockContext;
import com.example.sentinel.core.PlayerData;
import com.example.sentinel.core.Violation;
import com.example.sentinel.core.WorldCheck;

/**
 * Flags placing blocks faster than a human can (fast-place / scaffold). Bounds the rate of
 * use-item-on-block packets over a rolling second.
 */
public final class FastPlaceCheck implements WorldCheck {
	private static final long WINDOW = 20;

	@Override
	public String id() {
		return "fastplace";
	}

	@Override
	public SentinelConfig.CheckSettings settings(SentinelConfig cfg) {
		return cfg.fastPlace;
	}

	@Override
	public Violation detect(ServerPlayer player, PlayerData data, BlockContext ctx) {
		if (ctx.kind != BlockContext.Kind.PLACE) {
			return Violation.NONE;
		}
		int perSecond = data.recordPlaceAndGetRate(ctx.tick, WINDOW);
		int max = SentinelConfig.get().maxPlacementsPerSecond;
		if (perSecond > max) {
			double weight = 1.0 + Math.min(4.0, (perSecond - max) * 0.4);
			return Violation.of(id(), weight, String.format("%d placements/s > %d", perSecond, max));
		}
		return Violation.NONE;
	}
}
