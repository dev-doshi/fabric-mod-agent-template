package com.example.command;

import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import com.example.config.ExampleConfig;
import com.example.item.ModItems;

/**
 * Registers {@code /example}. Pattern verified against fabric-docs reference mod
 * (ExampleModCommands + CommandRegistrationCallback).
 *
 * <p>{@code /example greet}   -> echoes the configured greeting.
 * <p>{@code /example give}    -> gives the player one EXAMPLE_ITEM (asserted by a gametest).
 */
public final class ModCommands {
	public static void initialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(Commands.literal("example")
						.then(Commands.literal("greet").executes(ModCommands::greet))
						.then(Commands.literal("give").executes(ModCommands::give))));
	}

	private static int greet(CommandContext<CommandSourceStack> ctx) {
		ExampleConfig cfg = ExampleConfig.get();
		String msg = cfg.greetingEnabled ? cfg.greeting : "(greeting disabled)";
		ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
		return 1;
	}

	private static int give(CommandContext<CommandSourceStack> ctx) {
		var player = ctx.getSource().getPlayer();
		if (player == null) {
			ctx.getSource().sendFailure(Component.literal("/example give requires a player"));
			return 0;
		}
		player.getInventory().add(new ItemStack(ModItems.EXAMPLE_ITEM));
		ctx.getSource().sendSuccess(() -> Component.literal("Gave one example_item"), false);
		return 1;
	}

	private ModCommands() {
	}
}
