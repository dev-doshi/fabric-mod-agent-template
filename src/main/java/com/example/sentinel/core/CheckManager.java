package com.example.sentinel.core;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import com.example.sentinel.alert.AlertSink;
import com.example.sentinel.checks.FlyCheck;
import com.example.sentinel.checks.NoFallCheck;
import com.example.sentinel.checks.SpeedCheck;
import com.example.sentinel.checks.TimerCheck;
import com.example.sentinel.config.SentinelConfig;

/**
 * The engine. Holds per-player {@link PlayerData}, runs every {@link MovementCheck} against each
 * movement packet, owns buffering + violation-level accounting, and fires the setback/alert pipeline.
 *
 * <p>{@link #onMove} runs on the server thread (the mixin guards with {@code isSameThread}), so world
 * reads and setback teleports are safe inline and there are no cross-thread races with the per-tick
 * maintenance registered here.
 */
public final class CheckManager {
	private static final List<MovementCheck> CHECKS = List.of(
			new SpeedCheck(), new FlyCheck(), new NoFallCheck(), new TimerCheck());

	/** Max timer credit banked during idle (1s), so a paused client can't hoard packet budget. */
	private static final double TIMER_CAP = 20.0;

	private static final ConcurrentMap<UUID, PlayerData> DATA = new ConcurrentHashMap<>();

	private CheckManager() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(CheckManager::onServerTick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.player.getUUID();
			DATA.remove(id);
			AlertSink.clear(id);
		});
	}

	public static PlayerData dataFor(ServerPlayer player) {
		return DATA.computeIfAbsent(player.getUUID(), u -> new PlayerData(player.position()));
	}

	/**
	 * Process one movement packet. Returns {@code true} if the move was rejected (a setback fired) —
	 * the mixin then cancels vanilla handling so the illegal position is never applied.
	 */
	public static boolean onMove(ServerPlayer player, double x, double y, double z, boolean claimedOnGround) {
		SentinelConfig cfg = SentinelConfig.get();
		if (!cfg.enabled) {
			return false;
		}

		PlayerData data = dataFor(player);
		Vec3 to = new Vec3(x, y, z);
		MoveContext ctx = new MoveContext(data.lastPos, to, claimedOnGround, player.connection.latency());

		// Maintain airborne counter (for Fly) and debit the timer balance (for Timer).
		boolean supported = com.example.sentinel.physics.MovementSimulator.hasGroundSupport(player, to);
		data.airborneTicks = supported ? 0 : data.airborneTicks + 1;
		data.debitTimer();

		boolean setback = false;
		boolean anyFlag = false;
		for (MovementCheck check : CHECKS) {
			SentinelConfig.CheckSettings s = check.settings(cfg);
			if (!s.enabled) {
				continue;
			}
			Violation v = check.detect(player, data, ctx);
			if (!v.isFlag()) {
				data.clearBuffer(check.id());
				continue;
			}
			anyFlag = true;
			AlertSink.verbose(player, v);
			if (!data.bufferReached(check.id(), s.buffer)) {
				continue;
			}
			data.addVl(check.id(), v.weight());
			data.incrementFlag(check.id());
			double vl = data.vl(check.id());
			AlertSink.alert(player, v, vl);
			if (!cfg.silent && vl >= s.setbackVl) {
				setback = true;
				data.resetVl(check.id());
				data.clearBuffer(check.id());
			}
		}

		if (setback) {
			// Reject this move: rubberband to the last accepted position and cancel vanilla handling.
			Vec3 back = data.lastValidPos;
			player.connection.teleport(back.x, back.y, back.z, player.getYRot(), player.getXRot());
			data.lastPos = back;
			return true;
		}

		data.lastPos = to;
		// Only anchor the setback position to a fully-clean move, so rubberbands go to safe ground.
		if (!anyFlag) {
			data.lastValidPos = to;
		}
		return false;
	}

	private static void onServerTick(net.minecraft.server.MinecraftServer server) {
		SentinelConfig cfg = SentinelConfig.get();
		for (PlayerData data : DATA.values()) {
			data.creditTimer(TIMER_CAP);
			for (MovementCheck check : CHECKS) {
				double perTick = check.settings(cfg).decayPerSecond / 20.0;
				if (perTick > 0) {
					data.decayVl(check.id(), perTick);
				}
			}
		}
	}
}
