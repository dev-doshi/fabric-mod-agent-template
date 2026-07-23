package com.example;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import com.example.sentinel.core.CheckManager;
import com.example.sim.SimPlayer;
import com.example.sim.Simulation;

/**
 * Proves the P3 world checks: instant-mining, nuking, spam-placing and implausible ore ratios all
 * flag, while ordinary paced mining does not. Each test runs in its own arena (gametests share one
 * server and would otherwise clobber each other's blocks).
 */
public class SentinelWorldGameTest {
	private static final int RADIUS = 10;

	private static Vec3 buildArena(ServerLevel level, Vec3 approx) {
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

	private static Vec3 arena(ServerLevel level, int index) {
		Vec3 base = Vec3.atBottomCenterOf(level.getRespawnData().pos());
		return buildArena(level, base.add(index * 40.0, 0, 0));
	}

	/** Fill a field of mineable blocks one layer below the arena floor edge, returning their positions. */
	private static BlockPos[] fillField(ServerLevel level, Vec3 center, net.minecraft.world.level.block.Block block, int count) {
		BlockPos[] out = new BlockPos[count];
		int cx = (int) Math.floor(center.x);
		int cy = (int) Math.floor(center.y);
		int cz = (int) Math.floor(center.z);
		int i = 0;
		for (int dx = -RADIUS + 1; dx <= RADIUS - 1 && i < count; dx++) {
			for (int dz = -RADIUS + 1; dz <= RADIUS - 1 && i < count; dz++) {
				BlockPos p = new BlockPos(cx + dx, cy - 1, cz + dz);
				level.setBlockAndUpdate(p, block.defaultBlockState());
				out[i++] = p;
			}
		}
		return out;
	}

	private static int flags(SimPlayer p, String check) {
		return CheckManager.dataFor(p.entity()).flagCount(check);
	}

	private static int totalFlags(SimPlayer p) {
		return CheckManager.dataFor(p.entity()).totalFlags();
	}

	// Instant-mining stone (which needs ~150 ticks bare-handed) must flag.
	@GameTest(maxTicks = 140)
	public void fastBreakIsFlagged(GameTestHelper helper) {
		ServerLevel level = helper.getLevel().getServer().overworld();
		Vec3 center = arena(level, 20);
		BlockPos[] field = fillField(level, center, Blocks.STONE, 200);

		Simulation sim = Simulation.spawn(level, 3, center, 1.0, 2000L).start((p, t, r) -> {
			int idx = (int) (t % field.length);
			level.setBlockAndUpdate(field[idx], Blocks.STONE.defaultBlockState());
			p.instantBreak(field[idx], Direction.UP);
		});

		helper.runAtTickTime(120, () -> {
			for (SimPlayer p : sim.players()) {
				helper.assertTrue(flags(p, "fastbreak") > 0, p.name() + " fastbreak NOT flagged");
			}
			sim.disband();
			helper.succeed();
		});
	}

	// Destroying far more blocks per second than a survival player can must flag.
	@GameTest(maxTicks = 140)
	public void nukerIsFlagged(GameTestHelper helper) {
		ServerLevel level = helper.getLevel().getServer().overworld();
		Vec3 center = arena(level, 21);
		BlockPos[] field = fillField(level, center, Blocks.STONE, 200);

		Simulation sim = Simulation.spawn(level, 3, center, 1.0, 2100L).start((p, t, r) -> {
			// 20 blocks in a single tick — far past the per-second cap.
			for (int i = 0; i < 20; i++) {
				BlockPos target = field[(int) ((t * 20 + i) % field.length)];
				level.setBlockAndUpdate(target, Blocks.STONE.defaultBlockState());
				p.instantBreak(target, Direction.UP);
			}
		});

		helper.runAtTickTime(120, () -> {
			for (SimPlayer p : sim.players()) {
				helper.assertTrue(flags(p, "nuker") > 0, p.name() + " nuker NOT flagged");
			}
			sim.disband();
			helper.succeed();
		});
	}

	// Spamming placement packets must flag.
	@GameTest(maxTicks = 140)
	public void fastPlaceIsFlagged(GameTestHelper helper) {
		ServerLevel level = helper.getLevel().getServer().overworld();
		Vec3 center = arena(level, 22);
		BlockPos base = BlockPos.containing(center.x, center.y - 1, center.z);

		Simulation sim = Simulation.spawn(level, 3, center, 1.0, 2200L).start((p, t, r) -> {
			for (int i = 0; i < 10; i++) {
				p.placeAt(base, Direction.UP);
			}
		});

		helper.runAtTickTime(120, () -> {
			for (SimPlayer p : sim.players()) {
				helper.assertTrue(flags(p, "fastplace") > 0, p.name() + " fastplace NOT flagged");
			}
			sim.disband();
			helper.succeed();
		});
	}

	// Mining almost nothing but ore must raise the (advisory) X-ray heuristic.
	@GameTest(maxTicks = 160)
	public void xrayRatioIsFlagged(GameTestHelper helper) {
		ServerLevel level = helper.getLevel().getServer().overworld();
		Vec3 center = arena(level, 23);
		BlockPos[] field = fillField(level, center, Blocks.IRON_ORE, 200);

		Simulation sim = Simulation.spawn(level, 3, center, 1.0, 2300L).start((p, t, r) -> {
			for (int i = 0; i < 3; i++) {
				BlockPos target = field[(int) ((t * 3 + i) % field.length)];
				level.setBlockAndUpdate(target, Blocks.IRON_ORE.defaultBlockState());
				p.instantBreak(target, Direction.UP);
			}
		});

		helper.runAtTickTime(140, () -> {
			for (SimPlayer p : sim.players()) {
				helper.assertTrue(flags(p, "xray") > 0, p.name() + " xray heuristic NOT flagged");
			}
			sim.disband();
			helper.succeed();
		});
	}

	// Paced, properly-timed mining must not flag anything.
	@GameTest(maxTicks = 200)
	public void legitMiningProducesNoFalsePositives(GameTestHelper helper) {
		ServerLevel level = helper.getLevel().getServer().overworld();
		Vec3 center = arena(level, 24);
		BlockPos target = BlockPos.containing(center.x + 1, center.y - 1, center.z);
		level.setBlockAndUpdate(target, Blocks.DIRT.defaultBlockState());

		Simulation sim = Simulation.spawn(level, 3, center, 1.0, 2400L).start((p, t, r) -> {
			// Begin mining once, then finish only after a realistic delay for dirt (~50 ticks needed).
			if (t == 5) {
				p.startBreak(target, Direction.UP);
			} else if (t == 140) {
				p.stopBreak(target, Direction.UP);
			}
		});

		helper.runAtTickTime(180, () -> {
			for (SimPlayer p : sim.players()) {
				helper.assertTrue(totalFlags(p) == 0, p.name() + " world false-positive: " + totalFlags(p));
			}
			sim.disband();
			helper.succeed();
		});
	}
}
