package com.example.item;

import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;

import com.example.ExampleMod;

/**
 * Block registration. MC 26.1 pattern (verified against fabric-docs reference mod).
 * Registers a matching {@link BlockItem} so the block is obtainable.
 */
public final class ModBlocks {
	public static final Block EXAMPLE_BLOCK = register(
			"example_block",
			Block::new,
			BlockBehaviour.Properties.of().sound(SoundType.STONE).strength(1.5f),
			true
	);

	private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory,
			BlockBehaviour.Properties props, boolean withItem) {
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, ExampleMod.id(name));
		Block block = factory.apply(props.setId(blockKey));

		if (withItem) {
			ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, ExampleMod.id(name));
			BlockItem item = new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix());
			Registry.register(BuiltInRegistries.ITEM, itemKey, item);
		}
		return Registry.register(BuiltInRegistries.BLOCK, blockKey, block);
	}

	public static void initialize() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.BUILDING_BLOCKS)
				.register(entries -> entries.accept(EXAMPLE_BLOCK.asItem()));
	}

	private ModBlocks() {
	}
}
