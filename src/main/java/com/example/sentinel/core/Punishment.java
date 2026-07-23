package com.example.sentinel.core;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import com.example.ExampleMod;
import com.example.sentinel.config.SentinelConfig;

/**
 * Runs configured console commands when a player's violation level for a check crosses a threshold.
 * This is what makes the config's punishment ladder real rather than decorative.
 *
 * <p>Defaults ship EMPTY — the shipped posture is setback + silent staff alert, no auto-kick/ban.
 * Operators opt in by adding entries like {@code "kick {player} Cheating (sentinel: {check})"}.
 */
public final class Punishment {
	private Punishment() {
	}

	/**
	 * Execute the punishment commands configured for {@code checkId} at this VL, if any.
	 * Placeholders: {@code {player}}, {@code {check}}, {@code {vl}}.
	 */
	public static void run(ServerPlayer player, String checkId, double vl) {
		SentinelConfig cfg = SentinelConfig.get();
		var ladder = cfg.punishments.get(checkId);
		if (ladder == null || ladder.isEmpty()) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		if (server == null) {
			return;
		}
		for (SentinelConfig.PunishmentRule rule : ladder) {
			if (vl < rule.atVl || rule.command == null || rule.command.isBlank()) {
				continue;
			}
			String cmd = rule.command
					.replace("{player}", player.getGameProfile().name())
					.replace("{check}", checkId)
					.replace("{vl}", String.format("%.1f", vl));
			try {
				CommandSourceStack source = server.createCommandSourceStack();
				server.getCommands().performPrefixedCommand(source, cmd);
				ExampleMod.LOGGER.warn("[sentinel] punishment fired for {} ({} vl {}): {}",
						player.getGameProfile().name(), checkId, String.format("%.1f", vl), cmd);
			} catch (Exception e) {
				ExampleMod.LOGGER.error("[sentinel] punishment command failed: {}", cmd, e);
			}
		}
	}
}
