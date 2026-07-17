package com.example.item;

import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;

import com.example.ExampleMod;

/**
 * Item registration. MC 26.1 pattern (verified against fabric-docs reference mod):
 *  - key via {@link ResourceKey#create} with {@link Registries#ITEM}
 *  - id set on Properties via {@code setId(key)} BEFORE constructing the Item
 *  - register into {@link BuiltInRegistries#ITEM}
 */
public final class ModItems {
	public static final Item EXAMPLE_ITEM =
			register("example_item", Item::new, new Item.Properties());

	public static <T extends Item> T register(String name, Function<Item.Properties, T> factory, Item.Properties props) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(ExampleMod.MOD_ID, name));
		T item = factory.apply(props.setId(key));
		Registry.register(BuiltInRegistries.ITEM, key, item);
		return item;
	}

	public static void initialize() {
		// Add our item to a vanilla creative tab.
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS)
				.register(entries -> entries.accept(EXAMPLE_ITEM));
	}

	private ModItems() {
	}
}
