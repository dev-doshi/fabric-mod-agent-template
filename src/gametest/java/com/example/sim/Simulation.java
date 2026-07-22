package com.example.sim;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import java.util.concurrent.atomic.AtomicInteger;

import com.google.gson.Gson;

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

	// Unique per-instance token so concurrent simulations (e.g. multiple gametests sharing one server)
	// never collide on player name/UUID — a duplicate profile kicks the earlier one.
	private static final AtomicInteger INSTANCE_SEQ = new AtomicInteger();

	private final List<SimPlayer> players = new ArrayList<>();
	private final RandomSource random;
	private Behavior behavior = (p, t, r) -> { };
	private volatile boolean active;
	private long tick;

	// --- Trace recording (opt-in): a deterministic, machine-readable per-tick log. ---
	private boolean recording;
	private final List<Map<String, Object>> frames = new ArrayList<>();
	private Vec3 center = Vec3.ZERO;

	private Simulation(long seed) {
		this.random = RandomSource.create(seed);
	}

	/**
	 * Spawn {@code count} simulated players around {@code center} (jittered within {@code spread}
	 * blocks on X/Z), joined to {@code level}. Deterministic given {@code seed}.
	 */
	public static Simulation spawn(ServerLevel level, int count, Vec3 center, double spread, long seed) {
		Simulation sim = new Simulation(seed);
		sim.center = center;
		String prefix = "Sim" + INSTANCE_SEQ.getAndIncrement() + "_";
		for (int i = 0; i < count; i++) {
			double dx = (sim.random.nextDouble() - 0.5) * 2 * spread;
			double dz = (sim.random.nextDouble() - 0.5) * 2 * spread;
			Vec3 pos = new Vec3(center.x + dx, center.y, center.z + dz);
			sim.players.add(SimPlayer.join(level, prefix + i, pos));
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

	/** Enable per-tick trace capture. Call before {@link #start}. */
	public Simulation record() {
		this.recording = true;
		return this;
	}

	/**
	 * Write the captured trace as JSON for the replay viewer (tools/sim-viewer.html) and for an agent
	 * to grep/assert against. Schema: {@code {meta:{players,ticks,center}, frames:[{t, s:[[x,z,yaw,action,vd],...]}]}}.
	 */
	public void writeTrace(Path path) {
		Map<String, Object> meta = new LinkedHashMap<>();
		List<String> names = new ArrayList<>();
		for (SimPlayer p : players) {
			names.add(p.name());
		}
		meta.put("players", names);
		meta.put("ticks", frames.size());
		meta.put("center", new double[] { center.x, center.z });

		Map<String, Object> trace = new LinkedHashMap<>();
		trace.put("meta", meta);
		trace.put("frames", frames);

		try {
			Files.createDirectories(path.getParent());
			try (Writer w = Files.newBufferedWriter(path)) {
				new Gson().toJson(trace, w);
			}
		} catch (IOException e) {
			throw new RuntimeException("failed to write sim trace to " + path, e);
		}
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
		if (recording) {
			captureFrame();
		}
	}

	private void captureFrame() {
		List<Object> snaps = new ArrayList<>(players.size());
		for (SimPlayer p : players) {
			Vec3 pos = p.entity().position();
			// [x, z, yaw, action, viewDistance] — compact per-player row.
			snaps.add(new Object[] {
					round(pos.x), round(pos.z), Math.round(p.entity().getYRot()),
					p.lastAction(), p.viewDistance()
			});
		}
		Map<String, Object> frame = new LinkedHashMap<>();
		frame.put("t", tick);
		frame.put("s", snaps);
		frames.add(frame);
	}

	private static double round(double v) {
		return Math.round(v * 100.0) / 100.0;
	}
}
