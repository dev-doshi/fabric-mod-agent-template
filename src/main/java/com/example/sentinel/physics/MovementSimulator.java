package com.example.sentinel.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

import com.example.sentinel.config.SentinelConfig;
import com.example.sentinel.core.MoveContext;

/**
 * Server-side reconstruction of what movement is physically valid this tick.
 *
 * <p>P1 models the horizontal-speed and vertical-ascent envelopes from the player's real attributes
 * and state (movement-speed attribute — which already folds in Speed/Slowness effects — sprint,
 * jump-boost, on-ground transients) plus a lag-compensated tolerance. It intentionally errs generous:
 * the goal is to catch clearly-impossible motion (speed/fly/teleport), never to shave legitimate
 * edge cases. Full Grim-style per-input replication is a later iteration (P2+); the interface here is
 * built to absorb that without changing callers.
 */
public final class MovementSimulator {
	/** Vanilla default player movement-speed attribute; used to scale empirical per-tick caps. */
	private static final double DEFAULT_MOVEMENT_SPEED = 0.10;
	/** Empirical ground walk distance per tick at default speed (blocks). */
	private static final double BASE_WALK_PER_TICK = 0.24;
	private static final double SPRINT_MULTIPLIER = 1.30;
	/** Transient burst allowance (sprint-jump, stepping) when recently grounded. */
	private static final double JUMP_TRANSIENT = 0.22;

	private MovementSimulator() {
	}

	/**
	 * States where the horizontal-speed envelope is unreliable or legitimately huge, so the Speed
	 * check should stand down (return true = exempt). Covers vehicles, elytra, swimming, climbing.
	 */
	public static boolean speedExempt(ServerPlayer p) {
		return p.isPassenger()
				|| p.isFallFlying()
				|| p.isInWater()
				|| p.isInLava()
				|| p.onClimbable()
				|| p.getAbilities().flying
				|| p.hasEffect(MobEffects.LEVITATION);
	}

	/** Maximum legal horizontal blocks/tick for this player right now, lag-compensated. */
	public static double horizontalLimit(ServerPlayer p, MoveContext ctx, SentinelConfig cfg) {
		double scale = p.getAttributeValue(Attributes.MOVEMENT_SPEED) / DEFAULT_MOVEMENT_SPEED;
		if (scale <= 0.0) {
			scale = 1.0;
		}
		double limit = BASE_WALK_PER_TICK * scale;
		if (p.isSprinting()) {
			limit *= SPRINT_MULTIPLIER;
		}
		// Sprint-jump and step-up produce short bursts above the steady-state walk speed.
		limit += JUMP_TRANSIENT;
		// Lag compensation: widen by measured ping, plus a flat float-error tolerance.
		limit += cfg.tolerancePerPingTick * ctx.pingTicks();
		limit += cfg.globalMovementTolerance;
		return limit;
	}

	/**
	 * States where vertical ascent while airborne is legitimate, so the Fly check stands down.
	 */
	public static boolean flyExempt(ServerPlayer p) {
		return p.isPassenger()
				|| p.isFallFlying()
				|| p.isInWater()
				|| p.isInLava()
				|| p.onClimbable()
				|| p.getAbilities().flying
				|| p.hasEffect(MobEffects.LEVITATION)
				|| p.hasEffect(MobEffects.SLOW_FALLING);
	}

	/**
	 * Whether the block column just beneath {@code pos} actually supports standing — used to validate
	 * a claimed onGround flag (NoFall / fly-with-onGround-spoof). Samples a shallow band below the feet
	 * so genuine standing/landing passes but hovering in open air fails. {@code pos} is the CLAIMED
	 * position from the packet (the mixin runs before vanilla applies it, so {@code p.position()} is
	 * still the previous tick).
	 */
	public static boolean hasGroundSupport(ServerPlayer p, Vec3 pos) {
		// Scan from just below the feet down ~0.6 block; any collidable block = support.
		for (double dy = 0.001; dy <= 0.6; dy += 0.2) {
			BlockPos bp = BlockPos.containing(pos.x, pos.y - dy, pos.z);
			var state = p.level().getBlockState(bp);
			if (!state.getCollisionShape(p.level(), bp).isEmpty()) {
				return true;
			}
		}
		return false;
	}
}
