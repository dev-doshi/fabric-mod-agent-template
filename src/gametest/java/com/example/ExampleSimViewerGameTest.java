package com.example;

import java.nio.file.Path;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import com.example.sim.Simulation;

/**
 * Produces the demo trace for the replay viewer (tools/sim-viewer.html). Runs a scripted, visually
 * distinct scenario — orbiters, patrollers, and random-walkers, all swinging and changing settings —
 * records every tick, and writes {@code sim-trace.json} (in the gameTest run dir).
 *
 * <p>The trace doubles as a machine-readable oracle: an agent can assert on positions/actions/settings
 * per tick without any rendering. Deterministic (fixed seed + scripted paths).
 */
public class ExampleSimViewerGameTest {
	private static final int PLAYERS = 18;
	private static final int RECORD_TICKS = 180;

	@GameTest(maxTicks = 230)
	public void recordDemoTrace(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerLevel overworld = server.overworld();
		Vec3 center = Vec3.atBottomCenterOf(overworld.getRespawnData().pos());
		double y = center.y;

		Simulation sim = Simulation.spawn(overworld, PLAYERS, center, 6.0, 20260717L)
				.record()
				.start((player, tick, random) -> {
					int i = index(player.name());
					int role = i % 3;
					double px;
					double pz;
					float yaw;

					if (role == 0) {
						// Orbiters: circle around center.
						double radius = 6 + (i / 3) * 1.5;
						double ang = tick * 0.06 + i;
						px = center.x + Math.cos(ang) * radius;
						pz = center.z + Math.sin(ang) * radius;
						yaw = (float) (Math.toDegrees(ang) + 90);
					} else if (role == 1) {
						// Patrollers: sweep back and forth along X, fixed lane on Z.
						double lane = center.z - 6 + (i / 3) * 2.0;
						px = center.x + Math.sin(tick * 0.08 + i) * 8.0;
						pz = lane;
						yaw = Math.cos(tick * 0.08 + i) >= 0 ? 90 : -90;
					} else {
						// Random-walkers.
						Vec3 cur = player.entity().position();
						px = cur.x + (random.nextDouble() - 0.5) * 0.6;
						pz = cur.z + (random.nextDouble() - 0.5) * 0.6;
						yaw = random.nextInt(360) - 180;
					}

					player.moveTo(new Vec3(px, y, pz), yaw, 0f, true);

					if (tick % 20 == (i % 20)) {
						player.swing();
					}
					if (tick % 15 == 0) {
						int viewDistance = 2 + ((i + (int) (tick / 15)) % 12);
						player.changeSettings(new ClientInformation(
								"en_us", viewDistance, ChatVisiblity.FULL, true, 0,
								Player.DEFAULT_MAIN_HAND, false, false, ParticleStatus.ALL));
					}
				});

		helper.runAtTickTime(RECORD_TICKS + 5, () -> {
			for (var p : sim.players()) {
				helper.assertTrue(p.entity().isAlive(), "sim player " + p.name() + " died");
			}
			sim.stop();
			Path out = Path.of("sim-trace.json").toAbsolutePath();
			sim.writeTrace(out);
			ExampleMod.LOGGER.info("[sim-viewer] wrote trace: {}", out);
			sim.disband();
			helper.succeed();
		});
	}

	private static int index(String name) {
		int u = name.lastIndexOf('_');
		return u < 0 ? 0 : Integer.parseInt(name.substring(u + 1));
	}
}
