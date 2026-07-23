package com.example.sentinel.checks;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;

import com.example.sentinel.config.SentinelConfig;
import com.example.sentinel.core.BlockContext;
import com.example.sentinel.core.PlayerData;
import com.example.sentinel.core.Violation;
import com.example.sentinel.core.WorldCheck;

/**
 * Behavioural X-ray heuristic: flags an implausibly high ore-to-total mining ratio.
 *
 * <p><b>This is a heuristic, not proof.</b> X-ray is not detectable post-hoc server-side — the client
 * simply renders differently — so this is an advisory signal for staff review, deliberately low
 * weight and only after a meaningful sample. Actual prevention is chunk-packet obfuscation (P4).
 * Ore-vs-not is decided by vanilla ore tags, so it works for any ore added via those tags.
 */
public final class XrayHeuristicCheck implements WorldCheck {
	@Override
	public String id() {
		return "xray";
	}

	@Override
	public SentinelConfig.CheckSettings settings(SentinelConfig cfg) {
		return cfg.xray;
	}

	private static boolean isOre(BlockContext ctx) {
		return ctx.state.is(BlockTags.COAL_ORES)
				|| ctx.state.is(BlockTags.IRON_ORES)
				|| ctx.state.is(BlockTags.GOLD_ORES)
				|| ctx.state.is(BlockTags.COPPER_ORES)
				|| ctx.state.is(BlockTags.DIAMOND_ORES)
				|| ctx.state.is(BlockTags.EMERALD_ORES)
				|| ctx.state.is(BlockTags.LAPIS_ORES)
				|| ctx.state.is(BlockTags.REDSTONE_ORES);
	}

	@Override
	public Violation detect(ServerPlayer player, PlayerData data, BlockContext ctx) {
		if (ctx.kind != BlockContext.Kind.BREAK_STOP || player.isCreative()) {
			return Violation.NONE;
		}
		double ratio = data.recordMinedAndGetOreRatio(isOre(ctx));
		SentinelConfig cfg = SentinelConfig.get();
		if (data.minedTotal() < cfg.xraySampleSize) {
			return Violation.NONE; // not enough data to say anything
		}
		if (ratio > cfg.maxOreRatio) {
			// Advisory only: report once per sample window, then reset so it doesn't spam.
			data.resetMiningStats();
			return Violation.of(id(), 1.0,
					String.format("ore ratio %.0f%% over %d blocks (review)", ratio * 100, cfg.xraySampleSize));
		}
		if (data.minedTotal() >= cfg.xraySampleSize * 2) {
			data.resetMiningStats(); // roll the window for a clean player
		}
		return Violation.NONE;
	}
}
