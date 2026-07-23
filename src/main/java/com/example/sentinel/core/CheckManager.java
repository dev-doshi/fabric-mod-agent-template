package com.example.sentinel.core;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.world.entity.Entity;

import com.example.sentinel.alert.AlertSink;
import com.example.sentinel.checks.AutoClickerCheck;
import com.example.sentinel.checks.FastBreakCheck;
import com.example.sentinel.checks.FastPlaceCheck;
import com.example.sentinel.checks.FlyCheck;
import com.example.sentinel.checks.HitThroughWallsCheck;
import com.example.sentinel.checks.KillAuraAngleCheck;
import com.example.sentinel.checks.NoFallCheck;
import com.example.sentinel.checks.NukerCheck;
import com.example.sentinel.checks.ReachCombatCheck;
import com.example.sentinel.checks.SpeedCheck;
import com.example.sentinel.checks.TimerCheck;
import com.example.sentinel.checks.XrayHeuristicCheck;
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

	private static final List<CombatCheck> COMBAT_CHECKS = List.of(
			new ReachCombatCheck(), new HitThroughWallsCheck(), new KillAuraAngleCheck(), new AutoClickerCheck());

	private static final List<WorldCheck> WORLD_CHECKS = List.of(
			new FastBreakCheck(), new NukerCheck(), new FastPlaceCheck(), new XrayHeuristicCheck());

	/** Max timer credit banked during idle (1s), so a paused client can't hoard packet budget. */
	private static final double TIMER_CAP = 20.0;

	private static final ConcurrentMap<UUID, PlayerData> DATA = new ConcurrentHashMap<>();

	/** Server tick counter, advanced each END_SERVER_TICK; used for CPS/time-window checks. */
	private static volatile long currentTick;

	private CheckManager() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(CheckManager::onServerTick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.player.getUUID();
			DATA.remove(id);
			AlertSink.clear(id);
			LagCompensation.clear(id);
		});
	}

	/**
	 * Whether this player is exempt from all checks (config allow-list, or ops when opsBypass is on).
	 * Off by default for ops — administrators are not automatically above the anticheat.
	 */
	public static boolean isBypassed(ServerPlayer player) {
		SentinelConfig cfg = SentinelConfig.get();
		String name = player.getGameProfile().name();
		if (cfg.bypassPlayers != null && cfg.bypassPlayers.contains(name)) {
			return true;
		}
		if (cfg.opsBypass) {
			var server = player.level().getServer();
			return server != null && server.getPlayerList().isOp(player.nameAndId());
		}
		return false;
	}

	public static long currentTick() {
		return currentTick;
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
		if (!cfg.enabled || isBypassed(player)) {
			return false;
		}

		PlayerData data = dataFor(player);
		Vec3 to = new Vec3(x, y, z);
		MoveContext ctx = new MoveContext(data.lastPos, to, claimedOnGround,
				(int) Math.round(LagCompensation.latencyTicks(player) * 50.0));

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
			Punishment.run(player, check.id(), vl);
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
			// Velocity is zeroed by the rubberband — the player is authoritatively back at rest.
			data.prevHorizontalSpeed = 0.0;
			data.prevVerticalVelocity = 0.0;
			return true;
		}

		// Carry this tick's observed velocity forward as the predictor's state for the next tick.
		data.prevHorizontalSpeed = ctx.horizontal;
		data.prevVerticalVelocity = ctx.dy;
		data.lastPos = to;
		// Only anchor the setback position to a fully-clean move, so rubberbands go to safe ground.
		if (!anyFlag) {
			data.lastValidPos = to;
		}
		return false;
	}

	/**
	 * Process one attack packet. Returns {@code true} if the hit should be cancelled (a combat check
	 * flagged past threshold) — the mixin then cancels vanilla handling so no damage is dealt.
	 */
	public static boolean onAttack(ServerPlayer player, Entity target) {
		SentinelConfig cfg = SentinelConfig.get();
		if (!cfg.enabled || target == null || target == player || isBypassed(player)) {
			return false;
		}
		PlayerData data = dataFor(player);
		boolean cancel = false;
		for (CombatCheck check : COMBAT_CHECKS) {
			SentinelConfig.CheckSettings s = check.settings(cfg);
			if (!s.enabled) {
				continue;
			}
			Violation v = check.detect(player, target, data, currentTick);
			if (!v.isFlag()) {
				data.clearBuffer(check.id());
				continue;
			}
			AlertSink.verbose(player, v);
			if (!data.bufferReached(check.id(), s.buffer)) {
				continue;
			}
			data.addVl(check.id(), v.weight());
			data.incrementFlag(check.id());
			double vl = data.vl(check.id());
			AlertSink.alert(player, v, vl);
			Punishment.run(player, check.id(), vl);
			if (!cfg.silent && vl >= s.setbackVl) {
				cancel = true;
				data.resetVl(check.id());
				data.clearBuffer(check.id());
			}
		}
		return cancel;
	}

	/**
	 * Process a world interaction (block break start/stop, or a placement attempt). Returns
	 * {@code true} if it should be cancelled (a world check flagged past threshold).
	 */
	public static boolean onBlockAction(ServerPlayer player, BlockContext.Kind kind, net.minecraft.core.BlockPos pos) {
		SentinelConfig cfg = SentinelConfig.get();
		if (!cfg.enabled || isBypassed(player)) {
			return false;
		}
		PlayerData data = dataFor(player);

		if (kind == BlockContext.Kind.BREAK_START) {
			// Remember when/where mining began so FastBreak can time it; no checks fire on start.
			data.breakStartTick = currentTick;
			data.breakStartPos = pos.immutable();
			return false;
		}

		BlockContext ctx = new BlockContext(kind, pos, player.level().getBlockState(pos), currentTick);
		boolean cancel = false;
		for (WorldCheck check : WORLD_CHECKS) {
			SentinelConfig.CheckSettings s = check.settings(cfg);
			if (!s.enabled) {
				continue;
			}
			Violation v = check.detect(player, data, ctx);
			if (!v.isFlag()) {
				data.clearBuffer(check.id());
				continue;
			}
			AlertSink.verbose(player, v);
			if (!data.bufferReached(check.id(), s.buffer)) {
				continue;
			}
			data.addVl(check.id(), v.weight());
			data.incrementFlag(check.id());
			double vl = data.vl(check.id());
			AlertSink.alert(player, v, vl);
			Punishment.run(player, check.id(), vl);
			if (!cfg.silent && vl >= s.setbackVl) {
				cancel = true;
				data.resetVl(check.id());
				data.clearBuffer(check.id());
			}
		}
		if (kind == BlockContext.Kind.BREAK_STOP) {
			data.breakStartTick = -1;
			data.breakStartPos = null;
		}
		return cancel;
	}

	private static void onServerTick(net.minecraft.server.MinecraftServer server) {
		currentTick++;
		LagCompensation.tick(server, currentTick);
		SentinelConfig cfg = SentinelConfig.get();
		for (PlayerData data : DATA.values()) {
			data.creditTimer(TIMER_CAP);
			for (MovementCheck check : CHECKS) {
				double perTick = check.settings(cfg).decayPerSecond / 20.0;
				if (perTick > 0) {
					data.decayVl(check.id(), perTick);
				}
			}
			for (CombatCheck check : COMBAT_CHECKS) {
				double perTick = check.settings(cfg).decayPerSecond / 20.0;
				if (perTick > 0) {
					data.decayVl(check.id(), perTick);
				}
			}
			for (WorldCheck check : WORLD_CHECKS) {
				double perTick = check.settings(cfg).decayPerSecond / 20.0;
				if (perTick > 0) {
					data.decayVl(check.id(), perTick);
				}
			}
		}
	}
}
