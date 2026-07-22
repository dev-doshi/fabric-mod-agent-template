package com.example.sentinel.mixin;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.example.sentinel.core.CheckManager;

/**
 * Feeds movement packets to the Sentinel engine. Injected at the head of {@code handleMovePlayer};
 * the {@code isSameThread} guard ensures we act only on the server-thread execution (the vanilla
 * handler reschedules off the netty thread via {@code ensureRunningOnSameThread}, which would
 * otherwise run this inject twice). If the engine rejects the move (setback), we cancel vanilla
 * handling so the illegal position is never applied.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
	@Shadow
	public ServerPlayer player;

	@Inject(method = "handleMovePlayer", at = @At("HEAD"), cancellable = true)
	private void sentinel$onMove(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
		if (player == null || !player.level().getServer().isSameThread()) {
			return;
		}
		if (!packet.hasPosition()) {
			return; // rotation-only packet; nothing to validate here
		}
		double x = packet.getX(player.getX());
		double y = packet.getY(player.getY());
		double z = packet.getZ(player.getZ());
		if (CheckManager.onMove(player, x, y, z, packet.isOnGround())) {
			ci.cancel();
		}
	}
}
