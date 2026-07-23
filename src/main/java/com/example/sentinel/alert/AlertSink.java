package com.example.sentinel.alert;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import com.example.ExampleMod;
import com.example.sentinel.core.Violation;

/**
 * Routes anticheat flags to the console and to staff. Staff = players with permission level ≥ 2 who
 * have alerts enabled (default on). Verbose subscribers additionally receive every buffered pre-VL
 * detection for tuning. Alerts are chat-only and permission-gated so normal players never see them.
 */
public final class AlertSink {
	private static final Set<UUID> ALERTS_DISABLED = ConcurrentHashMap.newKeySet();
	private static final Set<UUID> VERBOSE = ConcurrentHashMap.newKeySet();

	private AlertSink() {
	}

	public static boolean toggleAlerts(ServerPlayer staff) {
		UUID id = staff.getUUID();
		if (ALERTS_DISABLED.remove(id)) {
			return true;
		}
		ALERTS_DISABLED.add(id);
		return false;
	}

	public static boolean toggleVerbose(ServerPlayer staff) {
		UUID id = staff.getUUID();
		if (VERBOSE.remove(id)) {
			return false;
		}
		VERBOSE.add(id);
		return true;
	}

	// Throttle: at most one alert per player+check per window, with a suppressed-count summary.
	private static final long THROTTLE_MS = 3000;
	private static final java.util.Map<String, long[]> LAST_ALERT = new ConcurrentHashMap<>();

	/**
	 * A confirmed flag (VL added). Goes to console + all staff with alerts on.
	 *
	 * <p>Throttled per player+check: a sustained cheat can flag many times per second, and spraying
	 * thousands of identical lines at staff makes the feed useless. Suppressed hits are counted and
	 * reported with the next alert that gets through.
	 */
	public static void alert(ServerPlayer player, Violation v, double vl) {
		String key = player.getUUID() + "/" + v.checkId();
		long now = System.currentTimeMillis();
		long[] state = LAST_ALERT.computeIfAbsent(key, k -> new long[] {0L, 0L}); // {lastMs, suppressed}
		long suppressed;
		synchronized (state) {
			if (now - state[0] < THROTTLE_MS) {
				state[1]++;
				return;
			}
			suppressed = state[1];
			state[0] = now;
			state[1] = 0;
		}
		String extra = suppressed > 0 ? String.format(" (+%d more)", suppressed) : "";
		String line = String.format("%s failed %s (vl %.1f) — %s%s",
				player.getGameProfile().name(), v.checkId(), vl, v.detail(), extra);
		ExampleMod.LOGGER.warn("[sentinel] {}", line);
		broadcast(player.level().getServer(), Component.literal("§c[Sentinel] §f" + line), false);
	}

	/** A pre-VL detection (buffering). Only verbose subscribers see it. */
	public static void verbose(ServerPlayer player, Violation v) {
		String line = String.format("%s ~ %s — %s", player.getGameProfile().name(), v.checkId(), v.detail());
		broadcast(player.level().getServer(), Component.literal("§8[Sentinel] " + line), true);
	}

	private static void broadcast(MinecraftServer server, Component message, boolean verboseOnly) {
		if (server == null) {
			return;
		}
		for (ServerPlayer staff : server.getPlayerList().getPlayers()) {
			if (!server.getPlayerList().isOp(staff.nameAndId())) {
				continue;
			}
			UUID id = staff.getUUID();
			if (verboseOnly) {
				if (VERBOSE.contains(id)) {
					staff.sendSystemMessage(message);
				}
			} else if (!ALERTS_DISABLED.contains(id)) {
				staff.sendSystemMessage(message);
			}
		}
	}

	public static void clear(UUID id) {
		ALERTS_DISABLED.remove(id);
		VERBOSE.remove(id);
	}
}
