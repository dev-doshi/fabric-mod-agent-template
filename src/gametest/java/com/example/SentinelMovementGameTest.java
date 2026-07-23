package com.example;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import com.example.sentinel.core.CheckManager;
import com.example.sim.SimPlayer;
import com.example.sim.Simulation;

/**
 * Proves the Sentinel anticheat with the multiplayer sim harness: bots that cheat (inject illegal
 * movement packets) must flag; bots that play legitimately must NOT. This is the closed loop the
 * plan describes — every check ships with a sim that demonstrates both detection and no false
 * positive, headless and CI-gated.
 */
public class SentinelMovementGameTest {
	private static final int PLATFORM_RADIUS = 8;

	// A flat stone platform with a 2-high air gap, so ground-support checks (fly/nofall) are
	// deterministic and not at the mercy of whatever terrain is at world spawn.
	private static Vec3 buildPlatform(ServerLevel level, Vec3 approxCenter) {
		int cx = (int) Math.floor(approxCenter.x);
		int cy = (int) Math.floor(approxCenter.y);
		int cz = (int) Math.floor(approxCenter.z);
		for (int dx = -PLATFORM_RADIUS; dx <= PLATFORM_RADIUS; dx++) {
			for (int dz = -PLATFORM_RADIUS; dz <= PLATFORM_RADIUS; dz++) {
				level.setBlockAndUpdate(new BlockPos(cx + dx, cy - 1, cz + dz), Blocks.STONE.defaultBlockState());
				level.setBlockAndUpdate(new BlockPos(cx + dx, cy, cz + dz), Blocks.AIR.defaultBlockState());
				level.setBlockAndUpdate(new BlockPos(cx + dx, cy + 1, cz + dz), Blocks.AIR.defaultBlockState());
			}
		}
		return new Vec3(cx + 0.5, cy, cz + 0.5);
	}

	private static Vec3 clampToPlatform(Vec3 center, double x, double z, double radius) {
		double cxMin = center.x - radius;
		double cxMax = center.x + radius;
		double czMin = center.z - radius;
		double czMax = center.z + radius;
		return new Vec3(Math.max(cxMin, Math.min(cxMax, x)), center.y, Math.max(czMin, Math.min(czMax, z)));
	}

	private static int totalFlags(SimPlayer p) {
		return CheckManager.dataFor(p.entity()).totalFlags();
	}

	private static int flags(SimPlayer p, String check) {
		return CheckManager.dataFor(p.entity()).flagCount(check);
	}

	private static Vec3 spawnCenter(ServerLevel level) {
		return Vec3.atBottomCenterOf(level.getRespawnData().pos());
	}

	/**
	 * Each test gets its OWN arena, offset along X. Gametests run concurrently against one server, so
	 * tests sharing one patch of world would clobber each other's blocks. Indices here are kept clear
	 * of the combat suite's arenas.
	 */
	private static Vec3 arena(ServerLevel level, int index) {
		return buildPlatform(level, spawnCenter(level).add(index * 40.0, 0, 0));
	}

	// --- The false-positive guard: legitimate play must never flag. ---
	@GameTest(maxTicks = 140)
	public void legitMovementProducesNoFalsePositives(GameTestHelper helper) {
		ServerLevel level = helper.getLevel().getServer().overworld();
		Vec3 center = arena(level, 10);

		Simulation sim = Simulation.spawn(level, 8, center, 2.0, 100L).start((p, t, r) -> {
			Vec3 cur = p.entity().position();
			// Small, grounded, bounded random-walk steps — ordinary survival movement.
			Vec3 next = clampToPlatform(center, cur.x + (r.nextDouble() - 0.5) * 0.2, cur.z + (r.nextDouble() - 0.5) * 0.2, 4.0);
			p.moveTo(next, true);
		});

		helper.runAtTickTime(120, () -> {
			for (SimPlayer p : sim.players()) {
				helper.assertTrue(totalFlags(p) == 0, p.name() + " false-positive: " + totalFlags(p) + " flags");
			}
			sim.disband();
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 140)
	public void speedHackIsFlagged(GameTestHelper helper) {
		ServerLevel level = helper.getLevel().getServer().overworld();
		Vec3 center = arena(level, 11);

		Simulation sim = Simulation.spawn(level, 6, center, 2.0, 200L).start((p, t, r) ->
				// ~1.5 blocks/tick horizontal — far beyond any legal walk/sprint speed.
				p.cheatMoveBy(1.5, 0, 0, true));

		helper.runAtTickTime(120, () -> {
			for (SimPlayer p : sim.players()) {
				helper.assertTrue(flags(p, "speed") > 0, p.name() + " speed hack NOT flagged");
			}
			sim.disband();
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 160)
	public void flyHackIsFlagged(GameTestHelper helper) {
		ServerLevel level = helper.getLevel().getServer().overworld();
		Vec3 center = arena(level, 12);

		Simulation sim = Simulation.spawn(level, 6, center, 2.0, 300L).start((p, t, r) ->
				// Rising through the air while claiming to be airborne, never falling — flight.
				p.cheatMoveBy(0, 0.4, 0, false));

		helper.runAtTickTime(140, () -> {
			for (SimPlayer p : sim.players()) {
				helper.assertTrue(flags(p, "fly") > 0, p.name() + " fly hack NOT flagged");
			}
			sim.disband();
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 140)
	public void noFallSpoofIsFlagged(GameTestHelper helper) {
		ServerLevel level = helper.getLevel().getServer().overworld();
		Vec3 center = arena(level, 13);
		Vec3 high = new Vec3(center.x, center.y + 12, center.z);

		Simulation sim = Simulation.spawn(level, 6, high, 2.0, 400L).start((p, t, r) ->
				// Descending through open air while claiming onGround=true — the NoFall spoof.
				p.cheatMoveBy(0, -0.08, 0, true));

		helper.runAtTickTime(120, () -> {
			for (SimPlayer p : sim.players()) {
				helper.assertTrue(flags(p, "nofall") > 0, p.name() + " nofall spoof NOT flagged");
			}
			sim.disband();
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 140)
	public void timerHackIsFlagged(GameTestHelper helper) {
		ServerLevel level = helper.getLevel().getServer().overworld();
		Vec3 center = arena(level, 14);

		Simulation sim = Simulation.spawn(level, 6, center, 2.0, 500L).start((p, t, r) -> {
			// Five movement packets in a single tick — the client is outrunning real time.
			Vec3 cur = p.entity().position();
			for (int i = 0; i < 5; i++) {
				p.moveTo(new Vec3(cur.x + i * 0.01, center.y, cur.z), true);
			}
		});

		helper.runAtTickTime(120, () -> {
			for (SimPlayer p : sim.players()) {
				helper.assertTrue(flags(p, "timer") > 0, p.name() + " timer hack NOT flagged");
			}
			sim.disband();
			helper.succeed();
		});
	}
}
