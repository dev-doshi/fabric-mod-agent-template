package com.example.sentinel.checks;

import net.minecraft.server.level.ServerPlayer;

import com.example.sentinel.config.SentinelConfig;
import com.example.sentinel.core.MoveContext;
import com.example.sentinel.core.MovementCheck;
import com.example.sentinel.core.PlayerData;
import com.example.sentinel.core.Violation;

/**
 * Flags clients sending movement packets faster than real time (timer hack / blink burst).
 *
 * <p>{@code data.timerBalance} is credited +1 each server tick (capped) and debited 1 per movement
 * packet by {@link com.example.sentinel.core.CheckManager}. A persistently negative balance means
 * more movement packets than ticks have elapsed — impossible at true 20 TPS. This check reads the
 * balance after the debit and flags once it dips below the tolerance.
 */
public final class TimerCheck implements MovementCheck {
	/** How far negative the balance may dip (packet bursts / jitter) before flagging. */
	private static final double SLACK = 3.0;

	@Override
	public String id() {
		return "timer";
	}

	@Override
	public SentinelConfig.CheckSettings settings(SentinelConfig cfg) {
		return cfg.timer;
	}

	@Override
	public Violation detect(ServerPlayer player, PlayerData data, MoveContext ctx) {
		if (data.timerBalance < -SLACK) {
			double weight = 1.0 + Math.min(4.0, (-data.timerBalance - SLACK) * 0.5);
			return Violation.of(id(), weight,
					String.format("timer balance %.1f (packets outrunning ticks)", data.timerBalance));
		}
		return Violation.NONE;
	}
}
