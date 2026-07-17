package com.example;

import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import com.example.sim.Simulation;

/**
 * GUI-observer test: a REAL headless client sits in a world while a swarm of simulated players moves
 * on the same (integrated) server. Proves a mod's GUI/HUD/rendering can be verified under realistic
 * multiplayer load — one live client plus many server-side bots — and captured as a screenshot.
 *
 * <p>This is the "admin watches many players" scenario, done for real: the observing client is a
 * genuine client; the crowd is genuine server-side players driven through real packet handlers.
 */
@SuppressWarnings("UnstableApiUsage")
public class ExampleSimClientGameTest implements FabricClientGameTest {
	private static final int BOTS = 12;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getClientLevel().waitForChunksRender();

			TestServerContext server = singleplayer.getServer();
			AtomicReference<Simulation> sim = new AtomicReference<>();

			// Spawn the swarm on the server thread, next to the world spawn (in view of the client).
			server.runOnServer(mc -> {
				var level = mc.overworld();
				Vec3 center = Vec3.atBottomCenterOf(level.getRespawnData().pos());
				sim.set(Simulation.spawn(level, BOTS, center, 4.0, 42L)
						.start((player, tick, random) -> {
							Vec3 cur = player.entity().position();
							player.moveTo(cur.add((random.nextDouble() - 0.5) * 0.3, 0, (random.nextDouble() - 0.5) * 0.3), true);
						}));
			});

			// Let the crowd move, then capture the client's view of the multiplayer load.
			context.waitTicks(60);
			context.takeScreenshot("sim-swarm-observed");

			server.runOnServer(mc -> sim.get().disband());
		}
	}
}
