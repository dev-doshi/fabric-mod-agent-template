package com.example.sentinel.core;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Transaction-based latency measurement.
 *
 * <p>Vanilla's {@code connection.latency()} is a smoothed keepalive average updated about once a
 * second — too coarse and too laggy to size a movement envelope with. Instead we send a
 * {@link ClientboundPingPacket} carrying an id, record the tick, and match the client's pong: the
 * round trip is then measured precisely, in ticks, at whatever cadence we choose. This is the same
 * "transaction" trick prediction-based anticheats use, and it is what lets the envelope be widened
 * exactly as much as a player's real latency requires and no more.
 */
public final class LagCompensation {
	/** How often to issue a transaction ping, in ticks. */
	private static final int PING_INTERVAL_TICKS = 20;

	private record Pending(int id, long sentTick) {
	}

	private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();
	private static final Map<UUID, Double> RTT_TICKS = new ConcurrentHashMap<>();
	private static int nextId = 0xACE0;

	private LagCompensation() {
	}

	/** Measured round-trip in ticks, or {@code null} if we have no sample yet. */
	public static Double rttTicks(ServerPlayer player) {
		return RTT_TICKS.get(player.getUUID());
	}

	/**
	 * Best available latency in ticks: the precise transaction sample when we have one, otherwise
	 * vanilla's keepalive average as a fallback.
	 */
	public static double latencyTicks(ServerPlayer player) {
		Double measured = RTT_TICKS.get(player.getUUID());
		if (measured != null) {
			return measured;
		}
		return player.connection.latency() / 50.0;
	}

	/** Issue transaction pings on a fixed cadence. Called from the server tick. */
	public static void tick(MinecraftServer server, long currentTick) {
		if (currentTick % PING_INTERVAL_TICKS != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID id = player.getUUID();
			if (PENDING.containsKey(id)) {
				continue; // still waiting on the previous transaction
			}
			int pingId = nextId++;
			PENDING.put(id, new Pending(pingId, currentTick));
			try {
				player.connection.send(new ClientboundPingPacket(pingId));
			} catch (Exception ignored) {
				PENDING.remove(id);
			}
		}
	}

	/** Called from the pong mixin: closes the transaction and records the round trip. */
	public static void onPong(ServerPlayer player, int id, long currentTick) {
		UUID uuid = player.getUUID();
		Pending pending = PENDING.get(uuid);
		if (pending == null || pending.id() != id) {
			return;
		}
		PENDING.remove(uuid);
		double rtt = Math.max(0, currentTick - pending.sentTick());
		// Smooth a little so one hiccup doesn't blow the envelope open.
		RTT_TICKS.merge(uuid, rtt, (old, fresh) -> old * 0.75 + fresh * 0.25);
	}

	public static void clear(UUID id) {
		PENDING.remove(id);
		RTT_TICKS.remove(id);
	}
}
