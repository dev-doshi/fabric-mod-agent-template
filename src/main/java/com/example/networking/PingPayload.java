package com.example.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import com.example.ExampleMod;

/**
 * A serverbound custom payload carrying a message string. MC 26.1 networking pattern
 * (verified against fabric-docs reference mod):
 *  - a record implementing {@link CustomPacketPayload}
 *  - a {@link CustomPacketPayload.Type} keyed by an {@link Identifier}
 *  - a {@link StreamCodec} describing the wire format
 */
public record PingPayload(String message) implements CustomPacketPayload {
	public static final Identifier ID = ExampleMod.id("ping");
	public static final Type<PingPayload> TYPE = new Type<>(ID);

	public static final StreamCodec<FriendlyByteBuf, PingPayload> CODEC =
			StreamCodec.composite(ByteBufCodecs.STRING_UTF8, PingPayload::message, PingPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
