package com.example;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Tier 1 unit test that DOES need Minecraft's registries (but still not a running game).
 * fabric-loader-junit bootstraps Fabric Loader around JUnit; the two calls in {@link #bootstrap()}
 * initialize vanilla registries so classes like {@link Items} resolve.
 *
 * <p>Verified in .agent-docs/mc-src: SharedConstants.tryDetectVersion() and Bootstrap.bootStrap().
 * Anything touching registries (ItemStack, Items.*, codecs) throws without this.
 */
class BootstrapExampleTest {
	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void vanillaRegistryIsAvailable() {
		Assertions.assertNotNull(Items.DIAMOND, "registries should be bootstrapped");
	}
}
