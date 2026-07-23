package com.example;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import com.example.sentinel.core.CheckManager;
import com.example.sim.SimPlayer;
import com.example.sim.Simulation;

/**
 * Proves the P2 combat checks with attacker/victim bots. Attackers spawn facing +Z (their default
 * yaw), so victims are positioned relative to that facing rather than relying on rotation packets
 * (which the server may freeze under "awaiting teleport", exactly like movement). Each cheat flags
 * its check; a legit fight flags nothing.
 */
public class SentinelCombatGameTest {
	private static final int RADIUS = 8;

	private static Vec3 buildPlatform(ServerLevel level, Vec3 approx) {
		int cx = (int) Math.floor(approx.x);
		int cy = (int) Math.floor(approx.y);
		int cz = (int) Math.floor(approx.z);
		for (int dx = -RADIUS; dx <= RADIUS; dx++) {
			for (int dz = -RADIUS; dz <= RADIUS; dz++) {
				level.setBlockAndUpdate(new BlockPos(cx + dx, cy - 1, cz + dz), Blocks.STONE.defaultBlockState());
				level.setBlockAndUpdate(new BlockPos(cx + dx, cy, cz + dz), Blocks.AIR.defaultBlockState());
				level.setBlockAndUpdate(new BlockPos(cx + dx, cy + 1, cz + dz), Blocks.AIR.defaultBlockState());
			}
		}
		return new Vec3(cx + 0.5, cy, cz + 0.5);
	}

	/**
	 * Each test gets its OWN arena, offset along X. Gametests run concurrently against one server, so
	 * tests sharing one patch of world would clobber each other's blocks (an earlier bug: one test's
	 * platform-clearing erased another's wall mid-run).
	 */
	private static Vec3 arena(ServerLevel level, int index) {
		Vec3 base = Vec3.atBottomCenterOf(level.getRespawnData().pos());
		return buildPlatform(level, base.add(index * 40.0, 0, 0));
	}

	private static int flags(SimPlayer p, String check) {
		return CheckManager.dataFor(p.entity()).flagCount(check);
	}

	private static int totalFlags(SimPlayer p) {
		return CheckManager.dataFor(p.entity()).totalFlags();
	}

	// Reach: attackers face the victim (+Z) but from 6 blocks away — beyond legal reach.
	@GameTest(maxTicks = 140)
	public void reachHackIsFlagged(GameTestHelper helper) {
		ServerLevel level = helper.getLevel().getServer().overworld();
		Vec3 center = arena(level, 0);
		SimPlayer victim = SimPlayer.join(level, "ReachVictim", center.add(0, 0, 6));

		Simulation sim = Simulation.spawn(level, 4, center, 0.5, 700L).start((p, t, r) -> {
			if (t % 3 == 0) {
				p.attack(victim.entity()); // slow cadence so the auto-clicker check stays quiet
			}
		});

		helper.runAtTickTime(120, () -> {
			for (SimPlayer p : sim.players()) {
				helper.assertTrue(flags(p, "reach") > 0, p.name() + " reach hack NOT flagged");
			}
			sim.disband();
			victim.leave();
			helper.succeed();
		});
	}

	// KillAura: victim is behind the attackers (−Z) and close, so the attack angle is ~180°.
	@GameTest(maxTicks = 140)
	public void killAuraAngleIsFlagged(GameTestHelper helper) {
		ServerLevel level = helper.getLevel().getServer().overworld();
		Vec3 center = arena(level, 1);
		SimPlayer victim = SimPlayer.join(level, "AuraVictim", center.add(0, 0, -3));

		Simulation sim = Simulation.spawn(level, 4, center, 0.5, 800L).start((p, t, r) -> {
			if (t % 3 == 0) {
				p.attack(victim.entity());
			}
		});

		helper.runAtTickTime(120, () -> {
			for (SimPlayer p : sim.players()) {
				helper.assertTrue(flags(p, "killaura") > 0, p.name() + " killaura NOT flagged");
			}
			sim.disband();
			victim.leave();
			helper.succeed();
		});
	}

	// HitThroughWalls: a stone wall sits between attackers and a close, in-front victim.
	@GameTest(maxTicks = 140)
	public void hitThroughWallsIsFlagged(GameTestHelper helper) {
		ServerLevel level = helper.getLevel().getServer().overworld();
		Vec3 center = arena(level, 2);
		SimPlayer victim = SimPlayer.join(level, "WallVictim", center.add(0, 0, 2.6));
		// Wall of stone between attacker cluster (z≈center) and victim (z+2.6).
		int wx = (int) Math.floor(center.x);
		int wz = (int) Math.floor(center.z + 1.3);
		int wy = (int) Math.floor(center.y);
		for (int dx = -2; dx <= 2; dx++) {
			for (int dy = 0; dy <= 2; dy++) {
				level.setBlockAndUpdate(new BlockPos(wx + dx, wy + dy, wz), Blocks.STONE.defaultBlockState());
			}
		}

		Simulation sim = Simulation.spawn(level, 4, center, 0.4, 900L).start((p, t, r) -> {
			if (t % 3 == 0) {
				p.attack(victim.entity());
			}
		});

		helper.runAtTickTime(120, () -> {
			for (SimPlayer p : sim.players()) {
				helper.assertTrue(flags(p, "hitthroughwalls") > 0, p.name() + " hitthroughwalls NOT flagged");
			}
			sim.disband();
			victim.leave();
			helper.succeed();
		});
	}

	// AutoClicker: attackers face a close victim and attack 5×/tick (100 CPS).
	@GameTest(maxTicks = 140)
	public void autoClickerIsFlagged(GameTestHelper helper) {
		ServerLevel level = helper.getLevel().getServer().overworld();
		Vec3 center = arena(level, 3);
		SimPlayer victim = SimPlayer.join(level, "ClickVictim", center.add(0, 0, 2));

		Simulation sim = Simulation.spawn(level, 4, center, 0.4, 1000L).start((p, t, r) -> {
			for (int i = 0; i < 5; i++) {
				p.attack(victim.entity());
			}
		});

		helper.runAtTickTime(120, () -> {
			for (SimPlayer p : sim.players()) {
				helper.assertTrue(flags(p, "autoclicker") > 0, p.name() + " autoclicker NOT flagged");
			}
			sim.disband();
			victim.leave();
			helper.succeed();
		});
	}

	// Legit fight: face a close victim, attack at a human cadence — nothing should flag.
	@GameTest(maxTicks = 160)
	public void legitCombatProducesNoFalsePositives(GameTestHelper helper) {
		ServerLevel level = helper.getLevel().getServer().overworld();
		Vec3 center = arena(level, 4);
		SimPlayer victim = SimPlayer.join(level, "LegitVictim", center.add(0, 0, 2));

		Simulation sim = Simulation.spawn(level, 4, center, 0.4, 1100L).start((p, t, r) -> {
			if (t % 8 == 0) { // ~2.5 CPS, well within human range
				p.attack(victim.entity());
			}
		});

		helper.runAtTickTime(140, () -> {
			for (SimPlayer p : sim.players()) {
				helper.assertTrue(totalFlags(p) == 0, p.name() + " combat false-positive: " + totalFlags(p));
			}
			sim.disband();
			victim.leave();
			helper.succeed();
		});
	}
}
