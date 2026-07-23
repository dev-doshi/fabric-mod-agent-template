package com.example;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.example.sentinel.physics.MovementPredictor;

/**
 * Validates the prediction model against Minecraft's KNOWN movement behaviour, independently of the
 * anticheat itself. This is what keeps the engine honest: if the recurrence were wrong, the steady
 * states it converges to would not match the walk/sprint speeds the game actually produces.
 */
class MovementPredictorTest {
	private static final double GROUND_FRICTION = 0.6;
	/** Vanilla walking movement-speed attribute. */
	private static final double WALK_SPEED = 0.1;
	/** Walking speed × the 1.3 sprint modifier. */
	private static final double SPRINT_SPEED = 0.13;

	@Test
	void walkingSteadyStateMatchesVanillaObservedSpeed() {
		double v = MovementPredictor.steadyStateSpeed(WALK_SPEED, GROUND_FRICTION);
		// Minecraft players walk at ~0.215 blocks/tick (4.317 blocks/s ÷ 20).
		Assertions.assertEquals(0.215, v, 0.02, "walk steady state should match vanilla, got " + v);
	}

	@Test
	void sprintingSteadyStateMatchesVanillaObservedSpeed() {
		double v = MovementPredictor.steadyStateSpeed(SPRINT_SPEED, GROUND_FRICTION);
		// Sprinting is ~0.28 blocks/tick (5.612 blocks/s ÷ 20).
		Assertions.assertEquals(0.28, v, 0.03, "sprint steady state should match vanilla, got " + v);
	}

	@Test
	void accelerationFromRestIsBounded() {
		// The whole point of prediction: you cannot be at full speed on the first tick.
		double friction = GROUND_FRICTION * MovementPredictor.AIR_DRAG;
		double accel = WALK_SPEED * (MovementPredictor.SPEED_FRICTION_CONST
				/ (GROUND_FRICTION * GROUND_FRICTION * GROUND_FRICTION));
		double firstTick = 0.0 * friction + accel;
		Assertions.assertTrue(firstTick < 0.12, "first tick from rest should be ~0.1, got " + firstTick);

		double steady = MovementPredictor.steadyStateSpeed(WALK_SPEED, GROUND_FRICTION);
		Assertions.assertTrue(firstTick < steady * 0.6,
				"acceleration must be gradual: first tick " + firstTick + " vs steady " + steady);
	}

	@Test
	void aTunedCheatUnderAFlatCapIsStillUnreachable() {
		// 0.5 b/t would pass a naive ~0.54 flat cap, but the recurrence says it is impossible:
		// even from full sprint speed you cannot reach it in one tick.
		double sprintSteady = MovementPredictor.steadyStateSpeed(SPRINT_SPEED, GROUND_FRICTION);
		double friction = GROUND_FRICTION * MovementPredictor.AIR_DRAG;
		double accel = SPRINT_SPEED * (MovementPredictor.SPEED_FRICTION_CONST
				/ (GROUND_FRICTION * GROUND_FRICTION * GROUND_FRICTION));
		double bestNextTick = sprintSteady * friction + accel;
		Assertions.assertTrue(bestNextTick < 0.5,
				"0.5 b/t must be unreachable even from sprint; best next tick = " + bestNextTick);
	}

	@Test
	void verticalToleranceMustStayBelowGravity() {
		// A vertical tolerance at or above gravity would make hovering indistinguishable from falling.
		double defaultVerticalTolerance = new com.example.sentinel.config.SentinelConfig().verticalTolerance;
		Assertions.assertTrue(defaultVerticalTolerance < 0.08,
				"vertical tolerance must stay under gravity, got " + defaultVerticalTolerance);
	}
}
