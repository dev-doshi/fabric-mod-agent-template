package com.example.sentinel.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * The prediction engine: bounds the set of physically-reachable movements for the next tick, rather
 * than comparing against a flat speed cap.
 *
 * <p>Vanilla's player movement is a recurrence (see {@code LivingEntity.travel} /
 * {@code getFrictionInfluencedSpeed} in the decompiled source):
 *
 * <pre>
 *   blockFriction = onGround ? blockBelow.getFriction() : 1.0      // 0.6 for most blocks
 *   friction      = blockFriction * 0.91
 *   groundAccel   = getSpeed() * (0.216 / blockFriction^3)
 *   v_next        = v_prev * friction + accel_input
 * </pre>
 *
 * <p>So the maximum reachable horizontal speed next tick is {@code v_prev * friction + accel}. This
 * is strictly stronger than a flat cap: it bounds <em>acceleration</em>, not just top speed. From a
 * standstill a player can only reach ~0.1 b/t; the walking steady state that recurrence converges to
 * is {@code 0.1 / (1 - 0.546) ≈ 0.215} — exactly vanilla's observed walk speed, which is a useful
 * self-check that the model is right. A cheat "tuned just under" a naive cap is caught immediately,
 * because getting to that speed at all requires impossible acceleration.
 *
 * <p>Vertically, an airborne player must obey {@code vy_next = (vy_prev - gravity) * 0.98}: upward
 * motion has to decay. Hovering or sustained ascent is therefore provably illegal.
 */
public final class MovementPredictor {
	/** Air drag applied to horizontal velocity every tick. */
	public static final double AIR_DRAG = 0.91;
	/** Vanilla's magic constant in getFrictionInfluencedSpeed. */
	public static final double SPEED_FRICTION_CONST = 0.21600002;
	/** Default block slipperiness. */
	public static final double DEFAULT_BLOCK_FRICTION = 0.6;
	/** Horizontal acceleration available while airborne (vanilla flying speed). */
	public static final double AIR_ACCEL = 0.026;
	/** Vertical drag per tick. */
	public static final double VERTICAL_DRAG = 0.98;
	/** Base jump impulse. */
	public static final double JUMP_POWER = 0.42;

	private MovementPredictor() {
	}

	/** Slipperiness of the block supporting the player (1.0 when airborne — no ground friction). */
	public static double blockFriction(ServerPlayer player, Vec3 pos, boolean onGround) {
		if (!onGround) {
			return 1.0;
		}
		try {
			BlockPos below = BlockPos.containing(pos.x, pos.y - 0.2, pos.z);
			return player.level().getBlockState(below).getBlock().getFriction();
		} catch (Throwable ignored) {
			return DEFAULT_BLOCK_FRICTION;
		}
	}

	/** Per-tick horizontal input acceleration available to this player right now. */
	public static double horizontalAccel(ServerPlayer player, double blockFriction, boolean onGround) {
		if (!onGround) {
			return AIR_ACCEL;
		}
		// getSpeed() already folds in the movement-speed attribute (and thus Speed/Slowness effects)
		// plus the sprint modifier.
		double speed = player.getSpeed();
		if (speed <= 0) {
			speed = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
		}
		double bf = Math.max(0.05, blockFriction);
		return speed * (SPEED_FRICTION_CONST / (bf * bf * bf));
	}

	/**
	 * Maximum horizontal distance the player may cover this tick, given the speed they were moving at
	 * last tick. This is the reachable-set bound, widened by a lag/uncertainty tolerance.
	 */
	public static double maxHorizontalSpeed(ServerPlayer player, double prevSpeed, Vec3 pos,
			boolean onGround, double tolerance) {
		double bf = blockFriction(player, pos, onGround);
		double friction = bf * AIR_DRAG;
		double accel = horizontalAccel(player, bf, onGround);
		return prevSpeed * friction + accel + tolerance;
	}

	/** Steady-state speed the recurrence converges to — used by tests to validate the model. */
	public static double steadyStateSpeed(double speed, double blockFriction) {
		double friction = blockFriction * AIR_DRAG;
		double accel = speed * (SPEED_FRICTION_CONST / (blockFriction * blockFriction * blockFriction));
		return accel / (1.0 - friction);
	}

	public static double gravity(ServerPlayer player) {
		double g = player.getAttributeValue(Attributes.GRAVITY);
		return g > 0 ? g : 0.08;
	}

	/**
	 * Maximum upward velocity legal this tick. On the ground that is a jump impulse; in the air the
	 * previous vertical velocity must decay under gravity — you cannot hover or climb.
	 */
	public static double maxVerticalVelocity(ServerPlayer player, double prevVy, boolean supported, double tolerance) {
		if (supported) {
			double jumpBoost = player.hasEffect(MobEffects.JUMP_BOOST)
					? 0.1 * (player.getEffect(MobEffects.JUMP_BOOST).getAmplifier() + 1.0)
					: 0.0;
			return JUMP_POWER + jumpBoost + tolerance;
		}
		return (prevVy - gravity(player)) * VERTICAL_DRAG + tolerance;
	}

	/**
	 * States where the model does not apply and checks must stand down: vehicles, elytra, fluids,
	 * climbing, creative flight, levitation, and the ticks right after taking a hit (knockback is
	 * server-applied velocity the client legitimately carries).
	 */
	public static boolean exempt(ServerPlayer p) {
		return p.isPassenger()
				|| p.isFallFlying()
				|| p.isInWater()
				|| p.isInLava()
				|| p.onClimbable()
				|| p.getAbilities().flying
				|| p.isSpectator()
				|| p.hasEffect(MobEffects.LEVITATION)
				|| p.hurtTime > 0;
	}
}
