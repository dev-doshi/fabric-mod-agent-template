package com.example.sentinel.checks;

import net.minecraft.server.level.ServerPlayer;

import com.example.sentinel.config.SentinelConfig;
import com.example.sentinel.core.MoveContext;
import com.example.sentinel.core.MovementCheck;
import com.example.sentinel.core.PlayerData;
import com.example.sentinel.core.Violation;
import com.example.sentinel.physics.MovementSimulator;

/**
 * Flags a client that claims {@code onGround=true} while there is no ground beneath it — the classic
 * NoFall spoof (report grounded to cancel fall damage) and a tell for onGround-spoofing fly.
 */
public final class NoFallCheck implements MovementCheck {
	@Override
	public String id() {
		return "nofall";
	}

	@Override
	public SentinelConfig.CheckSettings settings(SentinelConfig cfg) {
		return cfg.noFall;
	}

	@Override
	public Violation detect(ServerPlayer player, PlayerData data, MoveContext ctx) {
		if (MovementSimulator.flyExempt(player)) {
			return Violation.NONE;
		}
		if (ctx.claimedOnGround && !MovementSimulator.hasGroundSupport(player, ctx.to)) {
			return Violation.of(id(), 2.0,
					String.format("claimed onGround with no support at y=%.2f", ctx.to.y));
		}
		return Violation.NONE;
	}
}
