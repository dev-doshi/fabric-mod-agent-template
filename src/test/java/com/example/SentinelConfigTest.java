package com.example;

import java.io.StringReader;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.example.sentinel.config.SentinelConfig;
import com.example.sentinel.core.PlayerData;

import net.minecraft.world.phys.Vec3;

/**
 * Tier-1 unit tests for Sentinel logic that needs no running game: config parse/robustness and the
 * violation-level accounting (buffer, decay, flag counter). Fast; no Minecraft bootstrap required
 * for the config path (plain gson) — PlayerData is a pure POJO.
 */
class SentinelConfigTest {
	@Test
	void defaultsRoundTripThroughJson() {
		SentinelConfig cfg = new SentinelConfig();
		String json = cfg.toJson();
		SentinelConfig back = SentinelConfig.fromJson(new StringReader(json));
		Assertions.assertTrue(back.enabled);
		Assertions.assertTrue(back.speed.enabled);
		Assertions.assertEquals(cfg.speed.setbackVl, back.speed.setbackVl);
		Assertions.assertEquals(cfg.timer.buffer, back.timer.buffer);
	}

	@Test
	void malformedJsonFallsBackToDefaults() {
		SentinelConfig back = SentinelConfig.fromJson(new StringReader("not json at all"));
		Assertions.assertNotNull(back);
		Assertions.assertTrue(back.enabled);
	}

	@Test
	void emptyJsonYieldsPopulatedDefaults() {
		// gson leaves object fields null for {}; check-settings must still be usable.
		SentinelConfig back = SentinelConfig.fromJson(new StringReader("{}"));
		Assertions.assertNotNull(back);
	}

	@Test
	void vlAccumulatesAndDecays() {
		PlayerData d = new PlayerData(Vec3.ZERO);
		d.addVl("speed", 5.0);
		Assertions.assertEquals(5.0, d.vl("speed"));
		d.decayVl("speed", 2.0);
		Assertions.assertEquals(3.0, d.vl("speed"));
		d.decayVl("speed", 100.0); // never negative
		Assertions.assertEquals(0.0, d.vl("speed"));
	}

	@Test
	void bufferRequiresConsecutiveDetections() {
		PlayerData d = new PlayerData(Vec3.ZERO);
		Assertions.assertFalse(d.bufferReached("fly", 3));
		Assertions.assertFalse(d.bufferReached("fly", 3));
		Assertions.assertTrue(d.bufferReached("fly", 3));
		d.clearBuffer("fly");
		Assertions.assertFalse(d.bufferReached("fly", 3));
	}

	@Test
	void timerBalanceDebitsAndCreditsWithCap() {
		PlayerData d = new PlayerData(Vec3.ZERO);
		d.creditTimer(20.0);
		d.creditTimer(20.0); // capped at 20 — but starts at 0, so 2 here
		Assertions.assertEquals(2.0, d.timerBalance);
		for (int i = 0; i < 5; i++) {
			d.debitTimer();
		}
		Assertions.assertEquals(-3.0, d.timerBalance);
	}

	@Test
	void flagCounterIsCumulative() {
		PlayerData d = new PlayerData(Vec3.ZERO);
		d.incrementFlag("speed");
		d.incrementFlag("speed");
		d.incrementFlag("fly");
		Assertions.assertEquals(2, d.flagCount("speed"));
		Assertions.assertEquals(3, d.totalFlags());
	}

	@Test
	void cpsCountsAttacksInOneSecondWindow() {
		PlayerData d = new PlayerData(Vec3.ZERO);
		double cps = 0;
		// 30 attacks on tick 0 — all inside the 20-tick window.
		for (int i = 0; i < 30; i++) {
			cps = d.recordAttackAndGetCps(0);
		}
		Assertions.assertEquals(30.0, cps);
		// Advance 25 ticks and attack once — the tick-0 batch has aged out of the 20-tick window.
		double later = d.recordAttackAndGetCps(25);
		Assertions.assertTrue(later < 5.0, "old attacks should have aged out, got " + later);
	}

	@Test
	void regularTimingHasLowVariationIrregularHasHigh() {
		PlayerData regular = new PlayerData(Vec3.ZERO);
		for (int t = 0; t < 10; t++) {
			regular.recordAttackAndGetCps(t); // one per tick — perfectly regular
		}
		Assertions.assertTrue(regular.attackIntervalRegularity() < 0.10,
				"regular clicking should read as low CoV");

		PlayerData jittery = new PlayerData(Vec3.ZERO);
		long[] ticks = {0, 1, 3, 4, 7, 8, 12, 13};
		for (long t : ticks) {
			jittery.recordAttackAndGetCps(t);
		}
		Assertions.assertTrue(jittery.attackIntervalRegularity() > 0.10,
				"irregular clicking should read as higher CoV");
	}
}
