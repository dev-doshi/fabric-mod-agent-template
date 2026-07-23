package com.example.sentinel.mixin;

import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;

import com.example.sentinel.core.BlockContext;

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

	@Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
	private void sentinel$onPlayerAction(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
		if (player == null || !player.level().getServer().isSameThread()) {
			return;
		}
		BlockContext.Kind kind = switch (packet.getAction()) {
			case START_DESTROY_BLOCK -> BlockContext.Kind.BREAK_START;
			case STOP_DESTROY_BLOCK -> BlockContext.Kind.BREAK_STOP;
			default -> null;
		};
		if (kind != null && CheckManager.onBlockAction(player, kind, packet.getPos())) {
			ci.cancel();
		}
	}

	@Inject(method = "handleUseItemOn", at = @At("HEAD"), cancellable = true)
	private void sentinel$onUseItemOn(ServerboundUseItemOnPacket packet, CallbackInfo ci) {
		if (player == null || !player.level().getServer().isSameThread()) {
			return;
		}
		if (CheckManager.onBlockAction(player, BlockContext.Kind.PLACE, packet.getHitResult().getBlockPos())) {
			ci.cancel();
		}
	}

	@Inject(method = "handleAttack", at = @At("HEAD"), cancellable = true)
	private void sentinel$onAttack(ServerboundAttackPacket packet, CallbackInfo ci) {
		if (player == null || !player.level().getServer().isSameThread()) {
			return;
		}
		Entity target = player.level().getEntityOrPart(packet.entityId());
		if (target != null && CheckManager.onAttack(player, target)) {
			ci.cancel(); // reject the hit — no damage dealt
		}
	}
}

