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
}
