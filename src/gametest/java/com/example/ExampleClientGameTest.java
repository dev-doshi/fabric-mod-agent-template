package com.example;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * Tier 3 — headless client game test. Run via `./gradlew runClientGameTest` (locally headed;
 * CI uses `runProductionClientGameTest` + xvfb). Drives a REAL client deterministically:
 * the game stays paused between wait calls, exactly one server tick per client tick.
 *
 * <p>This single primitive is what lets an AI agent verify client/render/GUI code with NO human
 * clicking around the game. Add {@code context.assertScreenshotEquals(...)} for visual regression.
 *
 * <p>Verified against fabric-docs reference mod (ExampleModClientGameTest). Marked unstable API.
 */
@SuppressWarnings("UnstableApiUsage")
public class ExampleClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getClientLevel().waitForChunksRender();
			context.takeScreenshot("example-singleplayer-loaded");
		}
	}
}
