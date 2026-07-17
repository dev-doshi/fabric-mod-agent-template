package com.example.sim;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/**
 * Orchestrates a swarm of {@link SimPlayer}s under a per-tick {@link Behavior}.
 *
 * <p><b>Concurrency model — read this.</b> The Minecraft server is single-threaded: it processes one
 * tick at a time. There is no wall-clock simultaneity server-side; "simultaneous" player actions are
 * really interleaved within a single tick. This harness reproduces exactly that — on each
 * {@code END_SERVER_TICK} (which runs ON the server thread) it applies the behavior to every player,
 * so all of that tick's actions land in the same tick, the same way a real server drains many
 * clients' packets per tick. Seeded {@link RandomSource} makes runs deterministic.
 */
public final class Simulation {
	/** Per-tick behavior applied to each simulated player. */
	@FunctionalInterface
	public interface Behavior {
		void act(SimPlayer player, long tick, RandomSource random);
	}

	private final List<SimPlayer> players = new ArrayList<>();
	private final RandomSource random;
	private Behavior behavior = (p, t, r) -> { };
	private volatile boolean active;
	private long tick;

	private Simulation(long seed) {
		this.random = RandomSource.create(seed);
	}

	/**
	 * Spawn {@code count} simulated players around {@code center} (jittered within {@code spread}
	 * blocks on X/Z), joined to {@code level}. Deterministic given {@code seed}.
	 */
	public static Simulation spawn(ServerLevel level, int count, Vec3 center, double spread, long seed) {
		Simulation sim = new Simulation(seed);
		for (int i = 0; i < count; i++) {
			double dx = (sim.random.nextDouble() - 0.5) * 2 * spread;
			double dz = (sim.random.nextDouble() - 0.5) * 2 * spread;
			Vec3 pos = new Vec3(center.x + dx, center.y, center.z + dz);
			sim.players.add(SimPlayer.join(level, "Sim_" + i, pos));
		}
		return sim;
	}

	public List<SimPlayer> players() {
		return players;
	}

	public long tick() {
		return tick;
	}

	/**
	 * Begin pumping the behavior each server tick. Registers an {@code END_SERVER_TICK} handler that
	 * runs on the server thread; it is a no-op once {@link #stop()} is called.
	 */
	public Simulation start(Behavior behavior) {
		this.behavior = behavior;
		this.active = true;
		ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
		return this;
	}

	public void stop() {
		this.active = false;
	}

	/** Remove every simulated player from the server. */
	public void disband() {
		stop();
		for (SimPlayer p : players) {
			p.leave();
		}
	}

	private void onServerTick(MinecraftServer server) {
		if (!active) {
			return;
		}
		tick++;
		for (SimPlayer p : players) {
			behavior.act(p, tick, random);
		}
	}
}
