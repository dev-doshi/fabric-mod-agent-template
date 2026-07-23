package com.example.sentinel.checks;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import com.example.sentinel.config.SentinelConfig;
import com.example.sentinel.core.CombatCheck;
import com.example.sentinel.core.PlayerData;
import com.example.sentinel.core.Violation;

/**
 * Flags attacks where a solid block occludes the line of sight from the attacker's eye to the target
 * (hit-through-walls). Vanilla does not check occlusion on attacks, so this is genuine added coverage.
 *
 * <p>Implemented by sampling block collision along the eye→target segment via {@code getBlockState}
 * (the same reliable query the movement ground-check uses) rather than {@code Level.clip}, which was
 * observed to miss at far-out coordinates.
 */
public final class HitThroughWallsCheck implements CombatCheck {
	private static final double STEP = 0.25;

	@Override
	public String id() {
		return "hitthroughwalls";
	}

	@Override
	public SentinelConfig.CheckSettings settings(SentinelConfig cfg) {
		return cfg.hitThroughWalls;
	}

	@Override
	public Violation detect(ServerPlayer attacker, Entity target, PlayerData data, long currentTick) {
		Vec3 eye = attacker.getEyePosition();
		Vec3 tgt = target.getBoundingBox().getCenter();
		Vec3 delta = tgt.subtract(eye);
		double dist = delta.length();
		if (dist < 1.0e-4) {
			return Violation.NONE;
		}
		int steps = (int) Math.ceil(dist / STEP);
		// Sample strictly between the endpoints so the attacker's / target's own occupied blocks
		// don't count as occlusion.
		for (int i = 1; i < steps; i++) {
			Vec3 p = eye.add(delta.scale((double) i / steps));
			BlockPos bp = BlockPos.containing(p);
			if (!attacker.level().getBlockState(bp).getCollisionShape(attacker.level(), bp).isEmpty()) {
				return Violation.of(id(), 2.0, "solid block on line of sight to target");
			}
		}
		return Violation.NONE;
	}
}
