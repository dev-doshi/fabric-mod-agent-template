package com.example;

import java.io.StringReader;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.example.config.ExampleConfig;

/**
 * Tier 1 unit test — pure logic, no Minecraft launch. Runs in seconds via `./gradlew test`.
 * This is the fastest layer of the agent feedback loop: put testable logic in plain classes
 * like {@link ExampleConfig} so it can be verified without the game.
 */
class ExampleConfigTest {
	@Test
	void defaultsAreValid() {
		ExampleConfig cfg = new ExampleConfig().sanitized();
		Assertions.assertTrue(cfg.greetingEnabled);
		Assertions.assertFalse(cfg.greeting.isBlank());
		Assertions.assertEquals(7, cfg.luckyNumber);
	}

	@Test
	void roundTripsThroughJson() {
		ExampleConfig cfg = new ExampleConfig();
		cfg.greeting = "hi";
		cfg.luckyNumber = 42;
		cfg.greetingEnabled = false;

		ExampleConfig back = ExampleConfig.fromJson(new StringReader(cfg.toJson()));
		Assertions.assertEquals("hi", back.greeting);
		Assertions.assertEquals(42, back.luckyNumber);
		Assertions.assertFalse(back.greetingEnabled);
	}

	@Test
	void sanitizeClampsAndRepairs() {
		ExampleConfig cfg = ExampleConfig.fromJson(new StringReader("{\"luckyNumber\": 9999, \"greeting\": \"  \"}"));
		Assertions.assertEquals(100, cfg.luckyNumber, "luckyNumber should clamp to <= 100");
		Assertions.assertFalse(cfg.greeting.isBlank(), "blank greeting should be repaired to default");
	}

	@Test
	void emptyJsonYieldsDefaults() {
		ExampleConfig cfg = ExampleConfig.fromJson(new StringReader("{}"));
		Assertions.assertEquals(7, cfg.luckyNumber);
	}
}
