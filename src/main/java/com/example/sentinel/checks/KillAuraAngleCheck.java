package com.example.sentinel.checks;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import com.example.sentinel.config.SentinelConfig;
import com.example.sentinel.core.CombatCheck;
import com.example.sentinel.core.PlayerData;
import com.example.sentinel.core.Violation;

/**
 * Flags attacks where the attacker is not facing the target — the angle between the look direction
 * and the direction to the target exceeds the configured maximum. A legitimate player must look at
 * what they hit; kill-aura attacks entities behind or beside the player. Vanilla does not check this.
 */
public final class KillAuraAngleCheck implements CombatCheck {
	@Override
	public String id() {
		return "killaura";
	}

	@Override
	public SentinelConfig.CheckSettings settings(SentinelConfig cfg) {
		return cfg.killAura;
	}

	@Override
	public Violation detect(ServerPlayer attacker, Entity target, PlayerData data, long currentTick) {
		Vec3 eye = attacker.getEyePosition();
		Vec3 toTarget = target.getBoundingBox().getCenter().subtract(eye);
		if (toTarget.lengthSqr() < 1.0e-6) {
			return Violation.NONE;
		}
		Vec3 look = attacker.getLookAngle().normalize();
		double cos = look.dot(toTarget.normalize());
		double angleDeg = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, cos))));
		double max = SentinelConfig.get().maxAttackAngleDeg;
		if (angleDeg > max) {
			double weight = 1.0 + Math.min(4.0, (angleDeg - max) / 25.0);
			return Violation.of(id(), weight, String.format("attack angle %.0f° > %.0f°", angleDeg, max));
		}
		return Violation.NONE;
	}
}
