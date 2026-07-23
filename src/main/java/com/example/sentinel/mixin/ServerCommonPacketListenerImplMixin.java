package com.example.sentinel.mixin;

import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.example.sentinel.core.CheckManager;
import com.example.sentinel.core.LagCompensation;

/**
 * Closes Sentinel's latency transactions: when the client pongs one of our pings, record the precise
 * round-trip so the movement envelope can be widened by real measured lag rather than a guess.
 */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerCommonPacketListenerImplMixin {
	@Inject(method = "handlePong", at = @At("HEAD"))
	private void sentinel$onPong(ServerboundPongPacket packet, CallbackInfo ci) {
		if (!((Object) this instanceof ServerGamePacketListenerImpl gameListener)) {
			return; // only play-phase connections have a player
		}
		ServerPlayer player = gameListener.player;
		if (player == null) {
			return;
		}
		LagCompensation.onPong(player, packet.getId(), CheckManager.currentTick());
	}
}
