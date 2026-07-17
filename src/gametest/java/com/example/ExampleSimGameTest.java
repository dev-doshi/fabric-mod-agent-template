package com.example;

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
 * Multiplayer simulation test: a swarm of ~24 simulated players moves, swings, and changes settings
 * every tick, concurrently, on the real server. Exercises the full server-side path (movement
 * validation, interaction events, settings sync) under multiplayer load, headless, exit-code driven.
 *
 * <p>This is the harness proving itself. Point the same {@link Simulation} at your own mod's logic
 * (anticheat, player-list features, settings-driven behavior) to test it under realistic concurrency.
 */
public class ExampleSimGameTest {
	private static final int PLAYERS = 24;

	@GameTest(maxTicks = 200)
	public void swarmMovesInteractsAndChangesSettingsConcurrently(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerLevel overworld = server.overworld();
		Vec3 center = Vec3.atBottomCenterOf(overworld.getRespawnData().pos());

		int baseline = server.getPlayerList().getPlayerCount();

		Simulation sim = Simulation.spawn(overworld, PLAYERS, center, 8.0, 1234L)
				.start((player, tick, random) -> {
					// Movement: a small random-walk step each tick, through the real movement packet.
					Vec3 cur = player.entity().position();
					player.moveTo(cur.add((random.nextDouble() - 0.5) * 0.4, 0, (random.nextDouble() - 0.5) * 0.4), true);

					// Interaction: swing periodically.
					if (tick % 20 == 0) {
						player.swing();
					}

					// Settings: many players change view distance on the same tick (concurrent settings sync).
					if (tick % 15 == 0) {
						int viewDistance = 2 + random.nextInt(10);
						player.changeSettings(new ClientInformation(
								"en_us", viewDistance, ChatVisiblity.FULL, true, 0,
								Player.DEFAULT_MAIN_HAND, false, false, ParticleStatus.ALL));
					}
				});

		// Assert all bots joined right away (join is synchronous).
		helper.assertTrue(server.getPlayerList().getPlayerCount() >= baseline + PLAYERS,
				"expected " + PLAYERS + " simulated players to join");

		// After 150 ticks of concurrent activity, assert the swarm is intact (no desync/crash/drops).
		helper.runAtTickTime(150, () -> {
			helper.assertTrue(server.getPlayerList().getPlayerCount() >= baseline + PLAYERS,
					"simulated players dropped during the run");
			for (var p : sim.players()) {
				helper.assertTrue(p.entity().isAlive(), "simulated player " + p.name() + " died/disconnected");
			}
			sim.disband();
			helper.succeed();
		});
	}
}
