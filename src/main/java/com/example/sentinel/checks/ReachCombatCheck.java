package com.example.sentinel.checks;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import com.example.sentinel.config.SentinelConfig;
import com.example.sentinel.core.CombatCheck;
import com.example.sentinel.core.PlayerData;
import com.example.sentinel.core.Violation;

/**
 * Flags attacks landed from farther than the lag-compensated maximum reach (eye position to the
 * nearest point of the target's hitbox). Runs before vanilla's own 3.0 range gate, so a reach cheat
 * is recorded (VL + alert) rather than silently no-op'd.
 */
public final class ReachCombatCheck implements CombatCheck {
	@Override
	public String id() {
		return "reach";
	}

	@Override
	public SentinelConfig.CheckSettings settings(SentinelConfig cfg) {
		return cfg.reach;
	}

	@Override
	public Violation detect(ServerPlayer attacker, Entity target, PlayerData data, long currentTick) {
		SentinelConfig cfg = SentinelConfig.get();
		Vec3 eye = attacker.getEyePosition();
		double distSqr = target.getBoundingBox().distanceToSqr(eye);
		double dist = Math.sqrt(distSqr);
		double limit = cfg.maxReachBlocks + cfg.reachPerPingTick * (attacker.connection.latency() / 50.0);
		if (dist > limit) {
			double weight = 1.0 + Math.min(4.0, (dist - limit) * 3.0);
			return Violation.of(id(), weight, String.format("reach %.2f > %.2f", dist, limit));
		}
		return Violation.NONE;
	}
}
