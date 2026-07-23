package com.example.sentinel.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import com.example.sentinel.alert.AlertSink;
import com.example.sentinel.config.SentinelConfig;
import com.example.sentinel.core.CheckManager;

/**
 * {@code /sentinel} — staff control surface (gamemaster level). Subcommands:
 * {@code reload} (hot-reload config), {@code alerts} (toggle own alert feed),
 * {@code verbose} (toggle pre-VL detection feed), {@code vl <player>} (inspect a player's VLs).
 */
public final class SentinelCommand {
	private SentinelCommand() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, access, env) -> {
			LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("sentinel")
					.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));

			root.then(Commands.literal("reload").executes(ctx -> {
				boolean ok = SentinelConfig.reload();
				ctx.getSource().sendSuccess(() -> Component.literal(
						ok ? "§a[Sentinel] Config reloaded." : "§e[Sentinel] No config file; keeping current."), true);
				return 1;
			}));

			root.then(Commands.literal("alerts").executes(ctx -> {
				ServerPlayer p = ctx.getSource().getPlayer();
				if (p == null) {
					ctx.getSource().sendFailure(Component.literal("Players only."));
					return 0;
				}
				boolean on = AlertSink.toggleAlerts(p);
				ctx.getSource().sendSuccess(() -> Component.literal("§b[Sentinel] Alerts " + (on ? "ON" : "OFF")), false);
				return 1;
			}));

			root.then(Commands.literal("verbose").executes(ctx -> {
				ServerPlayer p = ctx.getSource().getPlayer();
				if (p == null) {
					ctx.getSource().sendFailure(Component.literal("Players only."));
					return 0;
				}
				boolean on = AlertSink.toggleVerbose(p);
				ctx.getSource().sendSuccess(() -> Component.literal("§b[Sentinel] Verbose " + (on ? "ON" : "OFF")), false);
				return 1;
			}));

			root.then(Commands.literal("vl").then(Commands.argument("player", StringArgumentType.word()).executes(ctx -> {
				String name = StringArgumentType.getString(ctx, "player");
				ServerPlayer target = ctx.getSource().getServer().getPlayerList().getPlayerByName(name);
				if (target == null) {
					ctx.getSource().sendFailure(Component.literal("Unknown player: " + name));
					return 0;
				}
				var data = CheckManager.dataFor(target);
				ctx.getSource().sendSuccess(() -> Component.literal(String.format(
						"§7[Sentinel] %s VL — speed %.1f fly %.1f nofall %.1f timer %.1f | reach %.1f killaura %.1f walls %.1f autoclick %.1f",
						name, data.vl("speed"), data.vl("fly"), data.vl("nofall"), data.vl("timer"),
						data.vl("reach"), data.vl("killaura"), data.vl("hitthroughwalls"), data.vl("autoclicker"))), false);
				return 1;
			})));

			dispatcher.register(root);
		});
	}
}
