package com.example.sentinel.core;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.world.phys.Vec3;

/**
 * Per-player rolling anticheat state: the last known-valid position (for setback), per-check
 * violation levels with buffering, and the timer-balance accumulator.
 *
 * <p>Accessed from the network thread (per packet) and the server thread (per-tick decay); movement
 * fields are only touched on the packet path, VL map is synchronized to be safe.
 */
public final class PlayerData {
	/** Last position the engine accepted as legal — where a setback rubberbands the player to. */
	public Vec3 lastValidPos;
	/** Position from the previous processed move packet. */
	public Vec3 lastPos;

	/** Consecutive ticks the player has been airborne without a legal cause (fly/nofall signal). */
	public int airborneTicks;
	/** Server tick of the last processed move packet. */
	public int lastMoveTick = -1;

	/**
	 * Timer balance: credited each server tick, debited each move packet. Persistently negative =>
	 * the client is sending movement faster than real time (timer/blink). Starts at 0.
	 */
	public double timerBalance;

	private final Map<String, Double> vl = new HashMap<>();
	private final Map<String, Integer> bufferCount = new HashMap<>();
	// Cumulative confirmed flags per check — never reset by setback, so tests/staff have a stable signal.
	private final Map<String, Integer> flags = new HashMap<>();

	// Attack "moments" (fractional server ticks) over the last ~second, for CPS / click-entropy.
	private final java.util.ArrayDeque<Double> attackMoments = new java.util.ArrayDeque<>();
	private int intraTickAttackSeq;
	private long lastAttackTickSeen = -1;

	public PlayerData(Vec3 spawn) {
		this.lastValidPos = spawn;
		this.lastPos = spawn;
	}

	/** Debit one packet's worth of timer budget (called per movement packet). */
	public synchronized void debitTimer() {
		timerBalance -= 1.0;
	}

	/** Credit one tick's worth of timer budget, capped (called per server tick). */
	public synchronized void creditTimer(double cap) {
		timerBalance = Math.min(cap, timerBalance + 1.0);
	}

	public synchronized double vl(String checkId) {
		return vl.getOrDefault(checkId, 0.0);
	}

	public synchronized void addVl(String checkId, double amount) {
		vl.merge(checkId, amount, Double::sum);
	}

	public synchronized void decayVl(String checkId, double amount) {
		double next = Math.max(0.0, vl.getOrDefault(checkId, 0.0) - amount);
		if (next == 0.0) {
			vl.remove(checkId);
		} else {
			vl.put(checkId, next);
		}
	}

	public synchronized void resetVl(String checkId) {
		vl.remove(checkId);
	}

	/**
	 * Increment the consecutive-detection buffer for a check and report whether it has reached the
	 * required threshold. A clean pass should call {@link #clearBuffer(String)}.
	 */
	public synchronized boolean bufferReached(String checkId, int required) {
		int n = bufferCount.merge(checkId, 1, Integer::sum);
		return n >= Math.max(1, required);
	}

	public synchronized void clearBuffer(String checkId) {
		bufferCount.remove(checkId);
	}

	public synchronized void incrementFlag(String checkId) {
		flags.merge(checkId, 1, Integer::sum);
	}

	public synchronized int flagCount(String checkId) {
		return flags.getOrDefault(checkId, 0);
	}

	public synchronized int totalFlags() {
		return flags.values().stream().mapToInt(Integer::intValue).sum();
	}

	/**
	 * Record an attack at {@code currentTick} and return the current CPS (attacks in the last 20
	 * ticks = 1 second). Multiple attacks in one tick are ordered by an intra-tick sequence so their
	 * moments are distinct.
	 */
	public synchronized double recordAttackAndGetCps(long currentTick) {
		if (currentTick != lastAttackTickSeen) {
			intraTickAttackSeq = 0;
			lastAttackTickSeen = currentTick;
		}
		double moment = currentTick + Math.min(0.99, intraTickAttackSeq++ / 100.0);
		attackMoments.addLast(moment);
		double cutoff = moment - 20.0;
		while (!attackMoments.isEmpty() && attackMoments.peekFirst() < cutoff) {
			attackMoments.removeFirst();
		}
		return attackMoments.size();
	}

	/**
	 * Coefficient of variation of the inter-attack intervals in the window (0 = perfectly regular,
	 * like a macro; higher = more human jitter). Returns a large value when too few samples.
	 */
	public synchronized double attackIntervalRegularity() {
		if (attackMoments.size() < 4) {
			return Double.MAX_VALUE;
		}
		Double[] m = attackMoments.toArray(new Double[0]);
		double sum = 0;
		int n = m.length - 1;
		double[] gaps = new double[n];
		for (int i = 0; i < n; i++) {
			gaps[i] = m[i + 1] - m[i];
			sum += gaps[i];
		}
		double mean = sum / n;
		if (mean <= 0) {
			return 0.0;
		}
		double var = 0;
		for (double g : gaps) {
			var += (g - mean) * (g - mean);
		}
		return Math.sqrt(var / n) / mean; // coefficient of variation
	}
}
