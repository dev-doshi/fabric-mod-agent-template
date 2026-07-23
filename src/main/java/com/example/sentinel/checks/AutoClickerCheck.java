package com.example.sentinel.checks;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import com.example.sentinel.config.SentinelConfig;
import com.example.sentinel.core.CombatCheck;
import com.example.sentinel.core.PlayerData;
import com.example.sentinel.core.Violation;

/**
 * Flags inhuman attack rates (auto-clicker / attack-aura). Records each attack and flags when the
 * clicks-per-second exceeds the configured max. When CPS is high, a very regular click interval
 * (low coefficient of variation — a macro's signature) raises the weight, since humans jitter.
 */
public final class AutoClickerCheck implements CombatCheck {
	/** CoV below this at high CPS looks macro-generated (humans rarely go this regular). */
	private static final double MACRO_REGULARITY = 0.10;

	@Override
	public String id() {
		return "autoclicker";
	}

	@Override
	public SentinelConfig.CheckSettings settings(SentinelConfig cfg) {
		return cfg.autoClicker;
	}

	@Override
	public Violation detect(ServerPlayer attacker, Entity target, PlayerData data, long currentTick) {
		double cps = data.recordAttackAndGetCps(currentTick);
		double maxCps = SentinelConfig.get().maxCps;
		if (cps > maxCps) {
			double weight = 1.0 + Math.min(4.0, (cps - maxCps) * 0.3);
			double regularity = data.attackIntervalRegularity();
			String tag = "";
			if (regularity < MACRO_REGULARITY) {
				weight += 1.0; // near-perfectly regular timing on top of high CPS
				tag = String.format(", CoV %.3f", regularity);
			}
			return Violation.of(id(), weight, String.format("%.0f CPS > %.0f%s", cps, maxCps, tag));
		}
		return Violation.NONE;
	}
}
