package com.example;

import net.minecraft.gametest.framework.GameTestHelper;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import com.example.entity.ExampleEntity;
import com.example.entity.ModEntities;
import com.example.item.ModBlocks;

/**
 * Tier 2 — server game tests. Run headless via `./gradlew runGameTest` (also wired into
 * `./gradlew build` via `check`). Exit code is the pass/fail signal — no log scraping.
 *
 * <p>MC 26.1 pattern (verified against fabric-docs reference mod):
 *  - annotate with Fabric's {@code @GameTest} (net.fabricmc.fabric.api.gametest.v1.GameTest)
 *  - the empty structure is the DEFAULT (the old {@code FabricGameTest.EMPTY_STRUCTURE} is gone)
 *  - each method is public, non-static, void, takes one {@link GameTestHelper}
 *  - registered via the "fabric-gametest" entrypoint in src/gametest/resources/fabric.mod.json
 */
public class ExampleGameTest {
	@GameTest
	public void exampleBlockCanBePlaced(GameTestHelper helper) {
		helper.setBlock(0, 1, 0, ModBlocks.EXAMPLE_BLOCK);
		helper.assertBlockPresent(ModBlocks.EXAMPLE_BLOCK, 0, 1, 0);
		helper.succeed();
	}

	@GameTest
	public void exampleEntitySpawnsAndTicks(GameTestHelper helper) {
		ExampleEntity entity = helper.spawn(ModEntities.EXAMPLE_ENTITY, 1, 1, 1);
		helper.assertEntityPresent(ModEntities.EXAMPLE_ENTITY, 1, 1, 1);
		// Entity increments its tick counter each tick; succeedWhen retries until it advances.
		helper.succeedWhen(() -> helper.assertTrue(entity.ticksLived() >= 5, "entity has not ticked yet"));
	}
}
